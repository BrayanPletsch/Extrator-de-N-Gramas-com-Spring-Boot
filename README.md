# Extrator de N-Gramas com Spring Boot

Este projeto é uma aplicação web desenvolvida em **Java** e **Spring Boot** que extrai **unigramas**, **bigramas** e **trigramas** de páginas web. Utiliza **Maven** para gerenciamento de dependências e a biblioteca **Jsoup** para fazer o scraping e processar o conteúdo HTML. O projeto demonstra o uso de controladores e serviços Spring Boot, tarefas multi-threaded com Executors, e processamento de N-gramas para tarefas de processamento de linguagem natural (PLN).

## Principais Funcionalidades

- **Extração de N-Gramas**: Extrai unigramas, bigramas e trigramas de uma URL fornecida e retorna os mais frequentes.
- **Scraping Multi-threaded**: Utiliza `ExecutorService` para realizar scraping e extração de texto de várias páginas web de forma concorrente.
- **Processamento de Texto e PLN**: Processa e normaliza o texto, removendo stopwords comuns e tratando casos especiais como CamelCase e pontuações.
- **Parâmetros Customizáveis**: Limite de palavras-chave, stopwords e profundidade máxima de scraping configuráveis.

## Tecnologias Utilizadas

- **Java**: Linguagem principal.
- **Spring Boot**: Framework para construção da aplicação, gerenciamento de dependências e criação de endpoints REST.
- **Maven**: Para gerenciamento de dependências e build do projeto.
- **Jsoup**: Para parsing de HTML e scraping de páginas web.
- **ExecutorService**: Para gerenciamento de tarefas multi-threaded.

## Estrutura do Projeto

O projeto segue uma arquitetura **Spring Boot MVC** típica com Controllers e Services, respeitando o princípio de separação de responsabilidades:

### 1. `KeywordController`

Este controlador expõe três endpoints REST para interação com a aplicação:

- `GET /unigrams?url={url}`: Extrai os principais unigramas (palavras únicas) da URL fornecida.
  ```java
  @GetMapping("/unigrams")
    public List<Map.Entry<String, Integer>> getUnigrams(@RequestParam String url) {
        return uniGramExtractor.extractNGramsFromMultiplePages(url);
    }
  ```
- `GET /bigrams?url={url}`: Extrai os principais bigramas (combinações de duas palavras) da URL fornecida.
  ```java
  @GetMapping("/bigrams")
    public List<Map.Entry<String, Integer>> getBigrams(@RequestParam String url) {
        return biGramExtractor.extractTopBiGramsFromSite(url);
    }
  ```
- `GET /trigrams?url={url}`: Extrai os principais trigramas (combinações de três palavras) da URL fornecida.
  ```java
  @GetMapping("/trigrams")
    public List<Map.Entry<String, Integer>> getTrigrams(@RequestParam String url) {
        return triGramExtractor.extractTopTriGramsFromSite(url)
                .stream()
                .limit(TriGramExtractor.FINAL_TOP_LIMIT)
                .toList();
    }
  ```

### 2. Serviços

A lógica de negócio para extração de N-gramas está encapsulada em três classes de serviço:

- **`UniGramExtractor`**: Extrai unigramas da(s) URL(s) fornecida(s). Esse serviço lida com scraping concorrente, processa o texto e aplica filtros para remover stopwords e termos inválidos.
  ```java
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
  ```
  
- **`BiGramExtractor`**: Semelhante ao `UniGramExtractor`, mas adaptado para extrair combinações de duas palavras (bigramas). Inclui normalização de texto, remoção de stopwords e ordenação dos bigramas mais frequentes.
  ```java
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
  ```
  
- **`TriGramExtractor`**: Extrai combinações de três palavras (trigramas) da URL fornecida. Esse serviço também suporta o crawling concorrente de páginas, garantindo que apenas trigramas válidos e significativos sejam retornados.
  ```java
  public List<Map.Entry<String, Integer>> extractTopTriGramsFromSite(String url) {
        Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
        Map<String, Integer> combinedFrequencyMap = Collections.synchronizedMap(new HashMap<>());

        try {
            Queue<String> urlQueue = new LinkedList<>();
            urlQueue.add(url);

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

                        List<String> trigrams = extractNGrams(normalizedText, 3, originalWordMapping);

                        for (String trigram : trigrams) {
                            if (isValidNGram(trigram)) {
                                combinedFrequencyMap.merge(trigram, 1, Integer::sum);
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
                    .limit(FINAL_TOP_LIMIT) // Retorna o top 20 final
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return List.of(Map.entry("Erro ao processar as URLs.", 0));
        }
    }
  ```

### 3. Processo de Extração de N-Gramas

Cada serviço segue um fluxo semelhante:

1. **Parsing de HTML**: Usando `Jsoup`, a aplicação busca o conteúdo HTML da página e extrai o texto visível.
2. **Normalização de Texto**: O texto extraído é limpo, removendo pontuações, separando palavras CamelCase e convertendo todos os caracteres para minúsculas.
3. **Filtro de Stopwords**: Palavras comuns (ex.: "e", "o", "de") são filtradas utilizando listas pré-definidas de stopwords.
4. **Geração de N-Gramas**: Dependendo do serviço, unigramas, bigramas ou trigramas são gerados a partir do texto limpo.
5. **Cálculo de Frequência**: A frequência de cada N-grama é calculada e ordenada para retornar os principais resultados.

## Como Executar

1. Clone o repositório:
   ```bash
   git clone https://github.com/seuusuario/ngram-extractor.git
   cd ngram-extractor
   ```

2. Construa o projeto com Maven:
   ```bash
   mvn clean install
   ```

3. Execute a aplicação:
   ```bash
   mvn spring-boot:run
   ```

4. Acesse a API através dos seguintes endpoints:
   - `/unigrams?url={url}`
   - `/bigrams?url={url}`
   - `/trigrams?url={url}`

## Exemplo de Uso

Para extrair os principais unigramas de `https://example.com`, você pode enviar uma requisição GET:

```
GET http://localhost:8080/unigrams?url=https://example.com
```

A resposta será uma lista em JSON com os principais unigramas e suas frequências, por exemplo:

```json
[
  {"word": "exemplo", "frequency": 15},
  {"word": "site", "frequency": 10},
  ...
]
```

## Melhorias Futuras

- **Suporte a Mais Idiomas**: Adicionar suporte para mais idiomas, expandindo a lista de stopwords.
- **Melhoria na Normalização de Texto**: Refinar a normalização de texto para tratar melhor casos como abreviações ou caracteres especiais.
- **Cache**: Implementar cache para URLs já processadas, evitando requisições duplicadas.

## Contato

Sinta-se à vontade para entrar em contato pelo [LinkedIn](https://www.linkedin.com/in/brayan-pletsch/), pelo meu site [brayan.blog](https://www.brayan.blog/) ou pelo email brayan.pletsch@gmail.com se tiver alguma dúvida sobre o projeto ou quiser discutir possíveis oportunidades.

---
