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
import java.util.Map;

@Service
@Slf4j
public class GeneratorService {
    
    private final OllamaConfig ollamaConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public GeneratorService(OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Generate answer using Ollama LLM with given context
     */
    public String generateAnswer(String query, String context) {
        try {
            String url = ollamaConfig.getBaseUrl() + "/api/generate";
            
            String prompt = buildPrompt(query, context);
            
            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaConfig.getGenerationModel());
            body.put("prompt", prompt);
            body.put("stream", false);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            String response = restTemplate.postForObject(url, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            String answer = jsonNode.get("response").asText();
            log.info("Generated answer for query");
            return answer;
            
        } catch (Exception e) {
            log.error("Error generating answer", e);
            return "Error generating answer";
        }
    }
    
    private String buildPrompt(String query, String context) {
        return String.format("""
                Answer the following query based on the provided context.
                
                Query: %s
                
                Context:
                %s
                
                Answer:""", query, context);
    }
}
