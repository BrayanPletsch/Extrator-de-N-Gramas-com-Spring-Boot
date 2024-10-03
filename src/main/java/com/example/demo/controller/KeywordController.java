package com.example.demo.controller;

import com.example.demo.service.BiGramExtractor;
import com.example.demo.service.TriGramExtractor;
import com.example.demo.service.UniGramExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class KeywordController {

    private final UniGramExtractor uniGramExtractor;
    private final BiGramExtractor biGramExtractor;
    private final TriGramExtractor triGramExtractor;

    @GetMapping("/unigrams")
    public List<Map.Entry<String, Integer>> getUnigrams(@RequestParam String url) {
        return uniGramExtractor.extractNGramsFromMultiplePages(url);
    }

    @GetMapping("/bigrams")
    public List<Map.Entry<String, Integer>> getBigrams(@RequestParam String url) {
        return biGramExtractor.extractTopBiGramsFromSite(url);
    }

    @GetMapping("/trigrams")
    public List<Map.Entry<String, Integer>> getTrigrams(@RequestParam String url) {
        return triGramExtractor.extractTopTriGramsFromSite(url)
                .stream()
                .limit(TriGramExtractor.FINAL_TOP_LIMIT)
                .toList();
    }
}

