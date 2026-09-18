-- Phase 7: Hallucination Rate (real, claim-level), Latency, and Cost tracking.
-- ddl-auto is set to validate, so this must be applied manually against an existing DB:
--   psql -U dev_user -d rag_evaluator -f migrations/004_phase7_hallucination_latency_cost.sql

ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS hallucination_rate NUMERIC(3,2);
ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS retrieval_latency_ms INTEGER;
ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS generation_latency_ms INTEGER;
ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER;
ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS completion_tokens INTEGER;
ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(10,6);

ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS avg_hallucination_rate NUMERIC(3,2);
ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS avg_retrieval_latency_ms INTEGER;
ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS avg_generation_latency_ms INTEGER;
ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS total_cost_usd NUMERIC(10,6);
