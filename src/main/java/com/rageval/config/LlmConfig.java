package com.rageval.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Selects between local Ollama (default — no API keys, nothing leaves your machine)
 * and cloud providers (Groq for generation, Gemini for embeddings) for environments
 * where Ollama can't run (e.g. a free-tier host with too little RAM for a 7B model).
 */
@Component
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmConfig {

    /** "ollama" (default) or "cloud" */
    private String provider = "ollama";

    @NestedConfigurationProperty
    private Groq groq = new Groq();

    @NestedConfigurationProperty
    private Gemini gemini = new Gemini();

    public boolean isCloud() {
        return "cloud".equalsIgnoreCase(provider);
    }

    @Getter
    @Setter
    public static class Groq {
        private String apiKey;
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String model = "openai/gpt-oss-20b";

        // Optional, for approximate cost tracking. Left null/0 by default rather than
        // hardcoding a price: Groq's per-model pricing varies enormously (roughly
        // $0.05-$3.00 per million tokens depending on the model, per Groq's own pricing
        // page) and changes over time, so a hardcoded default here would likely be wrong
        // for whichever model is actually configured. Set these two env vars yourself
        // from Groq's current pricing page for the model above, or leave unset — cost is
        // then simply not computed (token counts are still tracked either way).
        private java.math.BigDecimal pricePerMillionInputTokens;
        private java.math.BigDecimal pricePerMillionOutputTokens;
    }

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String model = "gemini-embedding-001";
        // Must match the pgvector column dimension (embeddings vector(768) in schema.sql) —
        // Gemini defaults to 3072 dims, so this is requested explicitly to stay compatible.
        private int outputDimensionality = 768;
    }
}
