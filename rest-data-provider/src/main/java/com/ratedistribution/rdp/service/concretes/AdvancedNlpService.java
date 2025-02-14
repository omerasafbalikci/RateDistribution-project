package com.ratedistribution.rdp.service.concretes;

import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.PropertiesUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class AdvancedNlpService {

    private final StanfordCoreNLP pipeline;

    public AdvancedNlpService() {
        // Basit pipeline config
        Properties props = PropertiesUtils.asProperties(
                "annotators", "tokenize,ssplit,pos,lemma,parse,sentiment",
                "enforceRequirements", "true"
        );
        this.pipeline = new StanfordCoreNLP(props);
    }

    /**
     * Tek bir başlık veya metin için sentiment skorunu döndürür.
     * Stanford: 0=very negative,1=negative,2=neutral,3=positive,4=very positive
     */
    public double analyzeSentiment(String text) {
        if (text == null || text.isEmpty()) return 2.0; // nötr

        CoreDocument doc = new CoreDocument(text);
        pipeline.annotate(doc);

        double total = 0;
        int count = 0;
        for (CoreSentence sentence : doc.sentences()) {
            String sentiment = sentence.sentiment();
            double score = mapSentimentToScore(sentiment);
            total += score;
            count++;
        }
        return (count == 0) ? 2.0 : (total / count);
    }

    private double mapSentimentToScore(String label) {
        switch (label.toLowerCase()) {
            case "very negative": return 0;
            case "negative":      return 1;
            case "neutral":       return 2;
            case "positive":      return 3;
            case "very positive": return 4;
            default: return 2;
        }
    }

    /**
     * Kural tabanlı anlam analizi.
     * Örnek: "FED" + "cut" => FED_RATE_CUT
     *        "FED" + "hike" => FED_RATE_HIKE
     */
    public NewsTrigger analyzeMeaning(String text) {
        if (text == null || text.isEmpty()) return NewsTrigger.NONE;
        String lower = text.toLowerCase();

        // FED faiz indirimi: "cut", "rate cut", "lower rates", "reduces rates"
        if (lower.contains("fed") && (lower.contains("cut") || lower.contains("rate cut")
                || lower.contains("lower rates") || lower.contains("reduces rates"))) {
            return NewsTrigger.FED_RATE_CUT;
        }

        // FED faiz artırımı: "hike", "raise rates", "increase rates"
        if (lower.contains("fed") && (lower.contains("hike") || lower.contains("rate hike")
                || lower.contains("raise rates") || lower.contains("increase rates"))) {
            return NewsTrigger.FED_RATE_HIKE;
        }

        // Enflasyon artışı: "inflation" + "increase", "rising", "surge", "spike"
        if (lower.contains("inflation") && (lower.contains("increase") || lower.contains("rising")
                || lower.contains("surge") || lower.contains("spike"))) {
            return NewsTrigger.INFLATION_RISE;
        }

        // Gümrük vergileri (tariffs): "tariff" + "impose", "increase", "new tariffs", "raised"
        if (lower.contains("tariff") && (lower.contains("impose") || lower.contains("increase")
                || lower.contains("new tariffs") || lower.contains("raised"))) {
            return NewsTrigger.NEW_TARIFFS;
        }

        // Kripto para haberleri: "crypto", "bitcoin", "ethereum", "token launch", "blockchain"
        if (lower.contains("crypto") || lower.contains("bitcoin") || lower.contains("ethereum")
                || lower.contains("token launch") || lower.contains("blockchain")) {
            return NewsTrigger.CRYPTO_NEWS;
        }

        return NewsTrigger.NONE;
    }
    /**
     * Bir grup başlığı sentiment ve meaning bazında analiz eder,
     * ortalama sentiment ve baskın tetikleyiciyi döndürür.
     */
    public NlpAnalysisResult analyzeNewsBatch(List<String> headlines) {
        if (headlines == null || headlines.isEmpty()) {
            return new NlpAnalysisResult(2.0, NewsTrigger.NONE);
        }

        double sum = 0.0;
        int count = 0;
        Map<NewsTrigger, Integer> triggerCountMap = new HashMap<>();

        for (String hl : headlines) {
            double s = analyzeSentiment(hl);
            sum += s;
            count++;

            NewsTrigger t = analyzeMeaning(hl);
            triggerCountMap.put(t, triggerCountMap.getOrDefault(t, 0) + 1);
        }

        double avgSent = sum / count;

        // En çok tekrar eden tetikleyiciyi bul
        NewsTrigger dominantTrigger = triggerCountMap.entrySet().stream()
                .filter(entry -> entry.getKey() != NewsTrigger.NONE)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(NewsTrigger.NONE);

        return new NlpAnalysisResult(avgSent, dominantTrigger);
    }

    public enum NewsTrigger {
        NONE,
        FED_RATE_CUT,
        FED_RATE_HIKE,
        INFLATION_RISE,
        NEW_TARIFFS,
        CRYPTO_NEWS
    }

    public static class NlpAnalysisResult {
        private final double sentimentScore;
        private final NewsTrigger trigger;

        public NlpAnalysisResult(double sentimentScore, NewsTrigger trigger) {
            this.sentimentScore = sentimentScore;
            this.trigger = trigger;
        }

        public double getSentimentScore() {
            return sentimentScore;
        }

        public NewsTrigger getTrigger() {
            return trigger;
        }
    }
}
