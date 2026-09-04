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
  relevantDocIds: string;
  createdAt: string;
}

export interface EvaluationResult {
  id: number;
  testQuery: TestQuery;
  generatedAnswer: string;
  retrievedChunkIds: string;
  precisionAtK: number;
  recall: number;
  mrr: number;
  ndcg: number;
  answerRelevance: number;
  faithfulness: number;
  contextPrecision: number;
  contextRecall: number;
  ragScore: number;
  createdAt: string;
}

export interface EvaluationRun {
  id: number;
  runName: string;
  totalQueries: number;
  avgRagScore: number;
  avgPrecision: number;
  avgRecall: number;
  avgAnswerRelevance: number;
  avgFaithfulness: number;
  createdAt: string;
}

export interface QueryResponse {
  query: string;
  answer: string;
  retrievedChunks: number;
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
