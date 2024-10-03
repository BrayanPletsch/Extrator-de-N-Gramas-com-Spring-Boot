package com.example.demo.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
public class UniGramExtractor {

    private static final Set<String> STOPWORDS = new LinkedHashSet<>(List.of(
            "a", "o", "e", "de", "em", "para", "com", "do", "da", "das", "dos", "no", "na", "se", "ao", "aos",
            "à", "às", "pelo", "pela", "pelos", "pelas", "um", "uma", "uns", "umas", "que", "os", "as", "nos",
            "esta", "está", "mais", "como", "ou", "sobre", "eles", "ela", "ele", "todas", "todos", "sua",
            "seu", "suas", "seus", "por", "é", "ser", "estamos", "nós", "foi", "são", "the", "to", "and", "também",
            "isso", "até", "pode", "podem", "isso", "essa", "esse", "essas", "esses"
    ));

    public static final int KEYWORDS_LIMIT = 20;
    public static final int MAX_PAGES = 5;
    public static final int MAX_LINKS_PER_PAGE = 5;

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public List<Map.Entry<String, Integer>> extractNGramsFromMultiplePages(String url) {
        try {
            Set<String> visitedUrls = new LinkedHashSet<>();
            Map<String, Integer> frequencyMap = new ConcurrentHashMap<>();

            Queue<Callable<Void>> tasks = new LinkedList<>();
            tasks.add(() -> {
                extractFromUrl(url, frequencyMap, visitedUrls, 0);
                return null;
            });

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }

            return frequencyMap.entrySet().stream()
                    .filter(entry -> entry.getKey().length() > 2)
                    .filter(entry -> !STOPWORDS.contains(entry.getKey().toLowerCase()))
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(KEYWORDS_LIMIT)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return List.of(Map.entry("Erro ao processar as URLs.", 0));
        }
    }

    private void extractFromUrl(String url, Map<String, Integer> frequencyMap, Set<String> visitedUrls, int depth) {
        if (depth >= MAX_PAGES || visitedUrls.contains(url)) return;

        try {
            visitedUrls.add(url);
            Connection connection = Jsoup.connect(url).timeout(5000);
            Document document = connection.get();
            String text = document.text();

            text = text.replaceAll("[^\\p{IsAlphabetic}\\p{N}\\s]", " ").replaceAll("\\s+", " ");
            text = splitCamelCaseWords(text);
            String[] words = text.split("\\s+");

            for (String word : words) {
                String wordLowerCase = word.toLowerCase();
                if (!isStopword(wordLowerCase) && word.length() > 2) {
                    frequencyMap.put(word, frequencyMap.getOrDefault(word, 0) + 1);
                }
            }

            Elements links = document.select("a[href]");
            int linksProcessed = 0;
            for (Element link : links) {
                String linkUrl = link.absUrl("href");
                if (isSameDomain(url, linkUrl) && !visitedUrls.contains(linkUrl)) {
                    linksProcessed++;
                    if (linksProcessed >= MAX_LINKS_PER_PAGE) break;
                    executor.submit(() -> extractFromUrl(linkUrl, frequencyMap, visitedUrls, depth + 1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isSameDomain(String originalUrl, String linkUrl) {
        try {
            return Jsoup.connect(linkUrl).get().location().contains(Jsoup.connect(originalUrl).get().location());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStopword(String word) {
        return STOPWORDS.contains(word);
    }

    private String splitCamelCaseWords(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}