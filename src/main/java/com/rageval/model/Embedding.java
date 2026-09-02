package com.rageval.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "embeddings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Embedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "chunk_id", nullable = false)
    private DocumentChunk documentChunk;
    
    @Column(columnDefinition = "vector(768)")
    private String embedding;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
