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
    
    @Column(name = "avg_answer_relevance", precision = 3, scale = 2)
    private BigDecimal avgAnswerRelevance;
    
    @Column(name = "avg_faithfulness", precision = 3, scale = 2)
    private BigDecimal avgFaithfulness;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
