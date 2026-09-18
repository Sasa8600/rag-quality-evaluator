-- Phase 1: Core schema for RAG
-- Run this in PostgreSQL after creating database

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Documents table
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Document chunks (smaller segments)
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Embeddings (vectors)
CREATE TABLE IF NOT EXISTS embeddings (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT REFERENCES document_chunks(id) ON DELETE CASCADE,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Query results (for evaluation)
CREATE TABLE IF NOT EXISTS query_results (
    id BIGSERIAL PRIMARY KEY,
    query_text TEXT NOT NULL,
    answer_text TEXT,
    retrieved_chunk_ids TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_doc_chunks_doc_id ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_chunk_id ON embeddings(chunk_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_vector ON embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);


-- Phase 2: Evaluation Schema
CREATE TABLE IF NOT EXISTS test_queries (
    id BIGSERIAL PRIMARY KEY,
    query_text TEXT NOT NULL,
    expected_answer TEXT,
    relevant_doc_ids TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evaluation_results (
    id BIGSERIAL PRIMARY KEY,
    test_query_id BIGINT REFERENCES test_queries(id) ON DELETE CASCADE,
    generated_answer TEXT,
    retrieved_chunk_ids TEXT,
    precision_at_k NUMERIC(3,2),
    recall NUMERIC(3,2),
    hit_rate NUMERIC(3,2),
    hallucination_rate NUMERIC(3,2),
    retrieval_latency_ms INTEGER,
    generation_latency_ms INTEGER,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    estimated_cost_usd NUMERIC(10,6),
    mrr NUMERIC(5,4),
    ndcg NUMERIC(5,4),
    answer_relevance NUMERIC(3,2),
    faithfulness NUMERIC(3,2),
    context_precision NUMERIC(3,2),
    context_recall NUMERIC(3,2),
    rag_score NUMERIC(3,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evaluation_runs (
    id BIGSERIAL PRIMARY KEY,
    run_name VARCHAR(255) NOT NULL,
    total_queries INTEGER NOT NULL,
    avg_rag_score NUMERIC(3,2),
    avg_precision NUMERIC(3,2),
    avg_recall NUMERIC(3,2),
    avg_hit_rate NUMERIC(3,2),
    avg_hallucination_rate NUMERIC(3,2),
    avg_retrieval_latency_ms INTEGER,
    avg_generation_latency_ms INTEGER,
    total_cost_usd NUMERIC(10,6),
    avg_answer_relevance NUMERIC(3,2),
    avg_faithfulness NUMERIC(3,2),
    top_k INTEGER,
    retrieval_mode VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_eval_results_query_id ON evaluation_results(test_query_id);
CREATE INDEX IF NOT EXISTS idx_test_queries_created ON test_queries(created_at);
