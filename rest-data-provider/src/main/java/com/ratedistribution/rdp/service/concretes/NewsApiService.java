package com.ratedistribution.rdp.service.concretes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.rdp.config.SimulatorProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsApiService {

    // Kendi API key'iniz:
    private final SimulatorProperties simulatorProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * NewsAPI üzerinden belirli bir arama query'si ile haber başlıklarını çeker
     */
    public List<String> fetchNewsHeadlines(String query) {
        List<String> headlines = new ArrayList<>();
        try {
            String newsApiKey = this.simulatorProperties.getNewsApiKey();
            String url = "https://newsapi.org/v2/everything?q=" + query
                    + "&language=en&apiKey=" + newsApiKey
                    + "&sortBy=publishedAt&pageSize=10";

            String jsonResponse = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            NewsApiResponse response = mapper.readValue(jsonResponse, NewsApiResponse.class);

            if (response != null && response.getArticles() != null) {
                for (Article a : response.getArticles()) {
                    headlines.add(a.getTitle());
                }
            }
        } catch (Exception e) {
        }
        return headlines;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NewsApiResponse {
        private String status;
        private int totalResults;
        private List<Article> articles;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Article {
        private String title;
        private String description;
        private String url;
    }
}
