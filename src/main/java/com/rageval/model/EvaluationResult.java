package com.rageval.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "test_query_id", nullable = false)
    private TestQuery testQuery;
    
    @Column(columnDefinition = "TEXT")
    private String generatedAnswer;
    
    @Column(columnDefinition = "TEXT")
    private String retrievedChunkIds;
    
    // Metrics (0-1 range)
    @Column(name = "precision_at_k", precision = 3, scale = 2)
    private BigDecimal precisionAtK;
    
    @Column(precision = 3, scale = 2)
    private BigDecimal recall;

    @Column(name = "hit_rate", precision = 3, scale = 2)
    private BigDecimal hitRate;

    @Column(name = "hallucination_rate", precision = 3, scale = 2)
    private BigDecimal hallucinationRate;

    @Column(name = "retrieval_latency_ms")
    private Integer retrievalLatencyMs;

    @Column(name = "generation_latency_ms")
    private Integer generationLatencyMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    // Null when the model's per-token price isn't configured (see LlmConfig.Groq) or when
    // running locally via Ollama, where the marginal cost really is zero — not "unknown".
    @Column(name = "estimated_cost_usd", precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd;
    
    @Column(name = "mrr", precision = 5, scale = 4)
    private BigDecimal mrr;
    
    @Column(precision = 5, scale = 4)
    private BigDecimal ndcg;
    
    @Column(name = "answer_relevance", precision = 3, scale = 2)
    private BigDecimal answerRelevance;
    
    @Column(precision = 3, scale = 2)
    private BigDecimal faithfulness;
    
    @Column(name = "context_precision", precision = 3, scale = 2)
    private BigDecimal contextPrecision;
    
    @Column(name = "context_recall", precision = 3, scale = 2)
    private BigDecimal contextRecall;
    
    @Column(name = "rag_score", precision = 3, scale = 2)
    private BigDecimal ragScore;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
