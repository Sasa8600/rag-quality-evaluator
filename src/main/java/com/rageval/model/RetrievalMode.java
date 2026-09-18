package com.rageval.model;

public enum RetrievalMode {
    VECTOR,   // pure semantic similarity (pgvector cosine distance)
    KEYWORD,  // pure Postgres full-text search (to_tsvector / ts_rank)
    HYBRID    // Reciprocal Rank Fusion of VECTOR + KEYWORD result lists
}
