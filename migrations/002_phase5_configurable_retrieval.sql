-- Phase 5: configurable retrieval (top-K, hybrid/keyword search) + run comparison.
-- ddl-auto is "validate", so Hibernate will NOT create these columns for you —
-- run this once against your existing rag_evaluator database before restarting
-- the backend:
--   psql -U dev_user -d rag_evaluator -f migrations/002_phase5_configurable_retrieval.sql

ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS top_k INTEGER;
ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS retrieval_mode VARCHAR(20);

-- Speeds up the new keyword/hybrid search (Postgres full-text search on chunk_text).
CREATE INDEX IF NOT EXISTS idx_doc_chunks_fulltext
    ON document_chunks USING GIN (to_tsvector('english', chunk_text));
