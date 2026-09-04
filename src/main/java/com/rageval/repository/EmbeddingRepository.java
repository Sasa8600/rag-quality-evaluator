package com.rageval.repository;

import com.rageval.model.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {
    
    @Query(value = "SELECT e.* FROM embeddings e " +
           "ORDER BY (1 - (e.embedding <=> CAST(:queryEmbedding AS vector))) ASC " +
           "LIMIT :topK", 
           nativeQuery = true)
    List<Embedding> findMostSimilar(@Param("queryEmbedding") String queryEmbedding, @Param("topK") int topK);

    Optional<Embedding> findByDocumentChunkId(Long chunkId);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO embeddings (chunk_id, embedding, created_at) VALUES (:chunkId, CAST(:embedding AS vector), CURRENT_TIMESTAMP)",
            nativeQuery = true)
    void saveEmbeddingWithVector(@Param("chunkId") Long chunkId, @Param("embedding") String embedding);
}
