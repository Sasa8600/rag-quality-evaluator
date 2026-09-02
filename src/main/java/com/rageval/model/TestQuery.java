package com.rageval.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "test_queries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String queryText;
    
    @Column(columnDefinition = "TEXT")
    private String expectedAnswer;
    
    @Column(columnDefinition = "TEXT")
    private String relevantDocIds;  // Comma-separated IDs or JSON
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
