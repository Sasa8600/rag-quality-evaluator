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
    
    // BUG FIX (was ordering ASC on similarity = returned the LEAST similar chunks).
    // pgvector's <=> is cosine DISTANCE (0 = identical), so ordering ASC on the
    // raw distance correctly returns nearest neighbours first.
    @Query(value = "SELECT e.* FROM embeddings e " +
           "ORDER BY e.embedding <=> CAST(:queryEmbedding AS vector) ASC " +
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
