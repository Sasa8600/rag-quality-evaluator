package com.rageval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rageval.config.LlmConfig;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmbeddingService {

    private final OllamaConfig ollamaConfig;
    private final LlmConfig llmConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingService(OllamaConfig ollamaConfig, LlmConfig llmConfig) {
        this.ollamaConfig = ollamaConfig;
        this.llmConfig = llmConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate an embedding for the given text — via local Ollama, or via the
     * Gemini embeddings API when llm.provider=cloud (see LlmConfig).
     */
    public List<Double> generateEmbedding(String text) {
        return llmConfig.isCloud() ? generateEmbeddingCloud(text) : generateEmbeddingOllama(text);
    }

    private List<Double> generateEmbeddingOllama(String text) {
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

            log.error("Failed to extract embedding from Ollama response");
            return null;

        } catch (Exception e) {
            log.error("Error generating Ollama embedding for text: {}", text, e);
            return null;
        }
    }

    private List<Double> generateEmbeddingCloud(String text) {
        try {
            LlmConfig.Gemini gemini = llmConfig.getGemini();
            if (gemini.getApiKey() == null || gemini.getApiKey().isBlank()) {
                log.error("llm.provider=cloud but llm.gemini.api-key is not set");
                return null;
            }

            String url = gemini.getBaseUrl() + "/models/" + gemini.getModel() + ":embedContent";

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(Map.of("text", text)));

            Map<String, Object> body = new HashMap<>();
            body.put("content", content);
            body.put("taskType", "RETRIEVAL_DOCUMENT");
            body.put("outputDimensionality", gemini.getOutputDimensionality());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", gemini.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(url, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);

            JsonNode valuesNode = jsonNode.path("embedding").path("values");
            if (valuesNode.isArray()) {
                return objectMapper.convertValue(valuesNode, List.class);
            }

            log.error("Failed to extract embedding from Gemini response: {}", response);
            return null;

        } catch (Exception e) {
            log.error("Error generating Gemini embedding for text: {}", text, e);
            return null;
        }
    }

    /**
     * Convert embedding to PostgreSQL vector format
     */
    public String embeddingToVectorString(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return "[]";
        }
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}
