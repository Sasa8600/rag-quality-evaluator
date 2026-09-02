package com.rageval.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ollama")
@Getter
public class OllamaConfig {
    private String baseUrl;
    private String embeddingModel;
    private String generationModel;
    private int requestTimeout;
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    public void setGenerationModel(String generationModel) {
        this.generationModel = generationModel;
    }
    
    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
