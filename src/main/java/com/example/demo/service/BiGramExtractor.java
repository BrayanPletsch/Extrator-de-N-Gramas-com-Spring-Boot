package com.example.demo.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BiGramExtractor {

    private static final String[] STOPWORDS = {
            "de", "do", "da", "dos", "das", "em", "para", "com", "sem", "por", "sobre", "entre", "ao", "à", "as", "os",
            "o", "a", "e", "que", "no", "nos", "nós", "na", "um", "uma", "uns", "umas", "é", "ser", "foi", "como", "também", "não"
    };

    private static final Set<String> STOPWORDS_SET = new HashSet<>(Arrays.asList(STOPWORDS));
    public static final int KEYWORDS_LIMIT = 20;
    public static final int MAX_PAGES = 5;
    public static final int MAX_LINKS_PER_PAGE = 5;
    public static final int TIMEOUT = 5000;

    private static final Pattern INCORRECT_WORD_JOIN = Pattern.compile("([a-zA-Z])([A-Z])");

    public List<Map.Entry<String, Integer>> extractTopBiGramsFromSite(String url) {
        Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
        Map<String, Integer> combinedFrequencyMap = Collections.synchronizedMap(new HashMap<>());

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Queue<String> urlQueue = new LinkedList<>();
        urlQueue.add(url);

        try {
            while (!urlQueue.isEmpty() && visitedUrls.size() < MAX_PAGES) {
                String currentUrl = urlQueue.poll();
                if (visitedUrls.contains(currentUrl)) continue;

                executorService.submit(() -> {
                    try {
                        visitedUrls.add(currentUrl);
                        Document document = Jsoup.connect(currentUrl).timeout(TIMEOUT).get();
                        String text = document.text();

                        Map<String, String> originalWordMapping = new HashMap<>();
                        String normalizedText = normalizeAndCleanText(text, originalWordMapping);

                        List<String> bigrams = extractNGrams(normalizedText, 2, originalWordMapping);

                        for (String bigram : bigrams) {
                            if (isValidNGram(bigram)) {
                                combinedFrequencyMap.merge(bigram, 1, Integer::sum);
                            }
                        }

                        if (visitedUrls.size() < MAX_PAGES) {
                            Elements links = document.select("a[href]");
                            int linksProcessed = 0;
                            for (Element link : links) {
                                String linkUrl = link.absUrl("href");
                                if (isSameDomain(currentUrl, linkUrl) && !visitedUrls.contains(linkUrl)) {
                                    urlQueue.add(linkUrl);
                                    linksProcessed++;
                                    if (linksProcessed >= MAX_LINKS_PER_PAGE) break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.MINUTES);

            return combinedFrequencyMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(KEYWORDS_LIMIT)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return List.of(Map.entry("Erro ao processar as URLs.", 0));
        }
    }

    private boolean isSameDomain(String originalUrl, String linkUrl) {
        try {
            return Jsoup.connect(linkUrl).get().location().contains(Jsoup.connect(originalUrl).get().location());
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeAndCleanText(String text, Map<String, String> originalWordMapping) {
        text = text.replaceAll("[^\\p{L}\\s]", " ");
        text = INCORRECT_WORD_JOIN.matcher(text).replaceAll("$1 $2");

        String[] words = text.split("\\s+");
        for (String word : words) {
            String normalizedWord = Normalizer.normalize(word.toLowerCase(), Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "");
            originalWordMapping.put(normalizedWord, word);
        }

        return text.replaceAll("\\s+", " ");
    }

    private List<String> extractNGrams(String text, int n, Map<String, String> originalWordMapping) {
        String[] words = text.split("\\s+");
        List<String> nGrams = new ArrayList<>();

        for (int i = 0; i <= words.length - n; i++) {
            String[] nGramWords = Arrays.copyOfRange(words, i, i + n);

            if (!isStopwordNGram(nGramWords) && isValidNGram(nGramWords)) {
                String originalNGram = Arrays.stream(nGramWords)
                        .map(word -> originalWordMapping.getOrDefault(word, word))
                        .collect(Collectors.joining(" "));
                nGrams.add(originalNGram);
            }
        }

        return nGrams;
    }

    private boolean isStopwordNGram(String[] nGramWords) {
        return Arrays.stream(nGramWords).allMatch(STOPWORDS_SET::contains);
    }

    private boolean isValidNGram(String[] nGramWords) {
        return Arrays.stream(nGramWords).noneMatch(word -> STOPWORDS_SET.contains(word) || word.length() < 3);
    }

    private boolean isValidNGram(String bigram) {
        return !bigram.trim().isEmpty() && !bigram.matches(".*\\d.*") && !bigram.matches(".*\\s\\s+.*");
    }
}
