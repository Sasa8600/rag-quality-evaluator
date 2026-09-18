package com.rageval.repository;

import com.rageval.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentId(Long documentId);

    // Postgres full-text keyword search (complements pgvector semantic search).
    // Falls back gracefully to an empty list if nothing matches the tsquery.
    @Query(value = "SELECT dc.* FROM document_chunks dc " +
           "WHERE to_tsvector('english', dc.chunk_text) @@ plainto_tsquery('english', :query) " +
           "ORDER BY ts_rank(to_tsvector('english', dc.chunk_text), plainto_tsquery('english', :query)) DESC " +
           "LIMIT :topN",
           nativeQuery = true)
    List<DocumentChunk> findByKeywordSearch(@Param("query") String query, @Param("topN") int topN);
}
