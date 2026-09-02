package com.rageval.repository;

import com.rageval.model.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {
    
    @Query(value = "SELECT e.*, " +
           "(1 - (e.embedding <=> CAST(:queryEmbedding AS vector))) as similarity " +
           "FROM embeddings e " +
           "ORDER BY similarity DESC " +
           "LIMIT :topK", 
           nativeQuery = true)
    List<Embedding> findMostSimilar(String queryEmbedding, int topK);
}
