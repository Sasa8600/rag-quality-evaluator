package com.rageval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rageval.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmbeddingService {
    
    private final OllamaConfig ollamaConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public EmbeddingService(OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Generate embedding for given text using Ollama
     */
    public List<Double> generateEmbedding(String text) {
        try {
            String url = ollamaConfig.getBaseUrl() + "/api/embeddings";
            
            Map<String, String> body = new HashMap<>();
            body.put("model", ollamaConfig.getEmbeddingModel());
            body.put("prompt", text);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            
            String response = restTemplate.postForObject(url, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            JsonNode embeddingNode = jsonNode.get("embedding");
            if (embeddingNode != null && embeddingNode.isArray()) {
                return objectMapper.convertValue(embeddingNode, List.class);
            }
            
            log.error("Failed to extract embedding from response");
            return null;
            
        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", text, e);
            return null;
        }
    }
    
    /**
     * Convert embedding to PostgreSQL vector format
     */
    public String embeddingToVectorString(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
