-- Phase 7: Hit Rate metric.
-- ddl-auto is set to validate, so this must be applied manually against an existing DB:
--   psql -U dev_user -d rag_evaluator -f migrations/003_phase7_hit_rate.sql

ALTER TABLE evaluation_results ADD COLUMN IF NOT EXISTS hit_rate NUMERIC(3,2);
ALTER TABLE evaluation_runs ADD COLUMN IF NOT EXISTS avg_hit_rate NUMERIC(3,2);
