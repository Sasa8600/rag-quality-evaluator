package com.rageval.dto;

/**
 * Body for creating a custom test query. relevantDocIds is optional: leave it null/blank
 * when you don't know the exact chunk IDs a query should retrieve — Precision@K, Recall,
 * MRR, NDCG and Context Recall need that ground truth and are reported as unavailable
 * (not a misleading 0.00) when it's absent, while Answer Relevance, Faithfulness and
 * Context Precision don't need it and are always computed.
 */
public record TestQueryRequest(String queryText, String expectedAnswer, String relevantDocIds) {
}
