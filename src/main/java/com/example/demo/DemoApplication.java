package com.example.demo;

import com.example.demo.service.UniGramExtractor;
import com.example.demo.service.BiGramExtractor;
import com.example.demo.service.TriGramExtractor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	CommandLineRunner run(UniGramExtractor uniGramExtractor, BiGramExtractor biGramExtractor, TriGramExtractor triGramExtractor) {
		return args -> {
			String url = "https://www.bbc.com/portuguese/articles/c5y3xy47jxzo";

			System.out.println("Uni-gramas (Top 20):");
			List<Map.Entry<String, Integer>> uniGrams = uniGramExtractor.extractNGramsFromMultiplePages(url);
			uniGrams.forEach(entry -> System.out.println(entry.getKey() + " - " + entry.getValue() + " vezes"));

			System.out.println("\n");

			System.out.println("Bi-gramas (Top 20):");
			List<Map.Entry<String, Integer>> biGrams = biGramExtractor.extractTopBiGramsFromSite(url);
			biGrams.forEach(entry -> System.out.println(entry.getKey() + " - " + entry.getValue() + " vezes"));

			System.out.println("\n");

			System.out.println("Tri-gramas (Top 20):");
			List<Map.Entry<String, Integer>> triGrams = triGramExtractor.extractTopTriGramsFromSite(url);
			triGrams.forEach(entry -> System.out.println(entry.getKey() + " - " + entry.getValue() + " vezes"));
		};
	}
}
