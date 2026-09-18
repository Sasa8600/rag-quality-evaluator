export type RetrievalMode = 'VECTOR' | 'KEYWORD' | 'HYBRID';

export interface Document {
  id: number;
  name: string;
  content: string;
  source: string;
  createdAt: string;
  updatedAt: string;
}

export interface TestQuery {
  id: number;
  queryText: string;
  expectedAnswer: string;
  relevantDocIds: string | null;
  createdAt: string;
}

export interface EvaluationResult {
  id: number;
  testQuery: TestQuery;
  generatedAnswer: string;
  retrievedChunkIds: string;
  // These five depend on the test query having ground-truth relevantDocIds — a custom
  // query created without them (or a query with blank relevantDocIds) reports these as
  // null ("not available"), not 0.00. A real 0.00 means retrieval was actually checked
  // against ground truth and found nothing relevant; null means there was no ground
  // truth to check against in the first place — the two are not the same thing.
  precisionAtK: number | null;
  recall: number | null;
  hitRate: number | null;
  mrr: number | null;
  ndcg: number | null;
  contextRecall: number | null;
  answerRelevance: number;
  faithfulness: number;
  // Real, claim-level check (independent LLM call), not derived from faithfulness.
  hallucinationRate: number | null;
  contextPrecision: number;
  ragScore: number;
  retrievalLatencyMs: number | null;
  generationLatencyMs: number | null;
  promptTokens: number | null;
  completionTokens: number | null;
  // 0 for local Ollama (genuinely free); null only in cloud mode when pricing isn't configured.
  estimatedCostUsd: number | null;
  createdAt: string;
}

export interface EvaluationRun {
  id: number;
  runName: string;
  totalQueries: number;
  avgRagScore: number;
  // null when every query in the run had no ground truth to average over (see
  // EvaluationResult) rather than a misleading 0.00 average.
  avgPrecision: number | null;
  avgRecall: number | null;
  avgHitRate: number | null;
  avgAnswerRelevance: number;
  avgFaithfulness: number;
  avgHallucinationRate: number | null;
  avgRetrievalLatencyMs: number | null;
  avgGenerationLatencyMs: number | null;
  totalCostUsd: number | null;
  topK: number | null;
  retrievalMode: RetrievalMode | null;
  createdAt: string;
}

export interface QueryResponse {
  query: string;
  answer: string;
  retrievedChunks: number;
  topK?: number;
  retrievalMode?: RetrievalMode;
  status: string;
  message?: string;
}

export interface EvaluationProgress {
  type: string;
  runName: string;
  current: number;
  total: number;
  status: 'started' | 'in_progress' | 'complete' | 'error';
  lastQuery?: string;
  lastRagScore?: number;
}
