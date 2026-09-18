package com.rageval.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String runName;
    
    @Column(nullable = false)
    private Integer totalQueries;
    
    @Column(name = "avg_rag_score", precision = 3, scale = 2)
    private BigDecimal avgRagScore;
    
    @Column(name = "avg_precision", precision = 3, scale = 2)
    private BigDecimal avgPrecision;
    
    @Column(name = "avg_recall", precision = 3, scale = 2)
    private BigDecimal avgRecall;

    @Column(name = "avg_hit_rate", precision = 3, scale = 2)
    private BigDecimal avgHitRate;

    @Column(name = "avg_hallucination_rate", precision = 3, scale = 2)
    private BigDecimal avgHallucinationRate;

    @Column(name = "avg_retrieval_latency_ms")
    private Integer avgRetrievalLatencyMs;

    @Column(name = "avg_generation_latency_ms")
    private Integer avgGenerationLatencyMs;

    @Column(name = "total_cost_usd", precision = 10, scale = 6)
    private BigDecimal totalCostUsd;
    
    @Column(name = "avg_answer_relevance", precision = 3, scale = 2)
    private BigDecimal avgAnswerRelevance;
    
    @Column(name = "avg_faithfulness", precision = 3, scale = 2)
    private BigDecimal avgFaithfulness;

    @Column(name = "top_k")
    private Integer topK;

    @Enumerated(EnumType.STRING)
    @Column(name = "retrieval_mode", length = 20)
    private RetrievalMode retrievalMode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
