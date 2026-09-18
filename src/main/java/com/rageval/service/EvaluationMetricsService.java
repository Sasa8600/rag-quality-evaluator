package com.rageval.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
public class EvaluationMetricsService {
    
    private final EmbeddingService embeddingService;
    
    public EvaluationMetricsService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }
    
    /**
     * Compute Precision@K
     * How many of top-K retrieved docs are relevant?
     */
    public BigDecimal computePrecisionAtK(List<Long> retrievedChunkIds, 
                                          List<Long> relevantChunkIds, 
                                          int k) {
        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int topK = Math.min(k, retrievedChunkIds.size());
        List<Long> topKRetrieved = retrievedChunkIds.subList(0, topK);
        
        long relevantCount = topKRetrieved.stream()
                .filter(relevantChunkIds::contains)
                .count();
        
        double precision = (double) relevantCount / topK;
        return BigDecimal.valueOf(precision).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Compute Recall
     * Did we find all (or most) relevant docs?
     */
    public BigDecimal computeRecall(List<Long> retrievedChunkIds, 
                                    List<Long> relevantChunkIds) {
        if (relevantChunkIds == null || relevantChunkIds.isEmpty()) {
            return BigDecimal.ONE;
        }
        
        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        long relevantFound = retrievedChunkIds.stream()
                .filter(relevantChunkIds::contains)
                .count();
        
        double recall = (double) relevantFound / relevantChunkIds.size();
        return BigDecimal.valueOf(recall).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Hit Rate — the simplest possible retrieval sanity check: did we get AT LEAST ONE
     * relevant chunk in the top-K, yes or no? Unlike Recall (what fraction of all relevant
     * chunks did we find) this doesn't care how many, only whether retrieval missed
     * completely. Useful as a quick, easy-to-explain floor metric: if Hit Rate is low,
     * retrieval is fundamentally broken for that query, no need to even look at the
     * finer-grained metrics yet.
     */
    public BigDecimal computeHitRate(List<Long> retrievedChunkIds, List<Long> relevantChunkIds) {
        if (relevantChunkIds == null || relevantChunkIds.isEmpty()) {
            return BigDecimal.ONE;
        }
        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        boolean hit = retrievedChunkIds.stream().anyMatch(relevantChunkIds::contains);
        return hit ? BigDecimal.ONE : BigDecimal.ZERO;
    }
    
    /**
     * Compute MRR (Mean Reciprocal Rank)
     * Rank position of first relevant doc
     */
    public BigDecimal computeMRR(List<Long> retrievedChunkIds, 
                                 List<Long> relevantChunkIds) {
        if (retrievedChunkIds == null || relevantChunkIds == null) {
            return BigDecimal.ZERO;
        }
        
        for (int i = 0; i < retrievedChunkIds.size(); i++) {
            if (relevantChunkIds.contains(retrievedChunkIds.get(i))) {
                double mrr = 1.0 / (i + 1);
                return BigDecimal.valueOf(mrr).setScale(4, RoundingMode.HALF_UP);
            }
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Compute NDCG (Normalized Discounted Cumulative Gain)
     * Quality of ranking based on relevance positions
     */
    public BigDecimal computeNDCG(List<Long> retrievedChunkIds, 
                                  List<Long> relevantChunkIds, 
                                  int k) {
        if (retrievedChunkIds == null || retrievedChunkIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // DCG: sum of relevance / log(position+1)
        double dcg = 0.0;
        int topK = Math.min(k, retrievedChunkIds.size());
        
        for (int i = 0; i < topK; i++) {
            if (relevantChunkIds.contains(retrievedChunkIds.get(i))) {
                dcg += 1.0 / Math.log(i + 2);  // log base 2
            }
        }
        
        // IDCG: perfect ranking
        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, relevantChunkIds.size()); i++) {
            idcg += 1.0 / Math.log(i + 2);
        }
        
        double ndcg = idcg > 0 ? dcg / idcg : 0.0;
        return BigDecimal.valueOf(ndcg).setScale(4, RoundingMode.HALF_UP);
    }
    
    /**
     * Compute Answer Relevance
     * Is generated answer similar to query?
     */
    public BigDecimal computeAnswerRelevance(String query, String generatedAnswer) {
        try {
            var queryEmbedding = embeddingService.generateEmbedding(query);
            var answerEmbedding = embeddingService.generateEmbedding(generatedAnswer);
            
            if (queryEmbedding == null || answerEmbedding == null) {
                return BigDecimal.ZERO;
            }
            
            double similarity = cosineSimilarity(queryEmbedding, answerEmbedding);
            return BigDecimal.valueOf(similarity).setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Error computing answer relevance", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Compute Faithfulness
     * Simple check: answer facts should come from context
     * Using embedding similarity to check if answer is grounded
     */
    public BigDecimal computeFaithfulness(String answer, String context) {
        try {
            var answerEmbedding = embeddingService.generateEmbedding(answer);
            var contextEmbedding = embeddingService.generateEmbedding(context);
            
            if (answerEmbedding == null || contextEmbedding == null) {
                return BigDecimal.ZERO;
            }
            
            double similarity = cosineSimilarity(answerEmbedding, contextEmbedding);
            return BigDecimal.valueOf(similarity).setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Error computing faithfulness", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Compute Context Precision
     * Are retrieved documents relevant to query?
     */
    public BigDecimal computeContextPrecision(String query, String context) {
        try {
            var queryEmbedding = embeddingService.generateEmbedding(query);
            var contextEmbedding = embeddingService.generateEmbedding(context);
            
            if (queryEmbedding == null || contextEmbedding == null) {
                return BigDecimal.ZERO;
            }
            
            double similarity = cosineSimilarity(queryEmbedding, contextEmbedding);
            return BigDecimal.valueOf(similarity).setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Error computing context precision", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Compute Context Recall
     * Same as Recall metric (did we get all relevant docs)
     */
    public BigDecimal computeContextRecall(List<Long> retrievedChunkIds, 
                                          List<Long> relevantChunkIds) {
        return computeRecall(retrievedChunkIds, relevantChunkIds);
    }
    
    /**
     * Compute Overall RAG Score
     * Weighted average of all metrics
     */
    public BigDecimal computeRAGScore(BigDecimal precision,
                                     BigDecimal recall,
                                     BigDecimal answerRelevance,
                                     BigDecimal faithfulness,
                                     BigDecimal contextPrecision) {
        try {
            // precision and recall depend on a test query having ground-truth relevant chunk
            // IDs, which is optional. When either is missing (null), its weight is redistributed
            // proportionally across the metrics that are always computable (answerRelevance,
            // faithfulness, contextPrecision) instead of silently treating the missing metric
            // as 0 — that would drag every ground-truth-free query's RAG score down toward zero
            // regardless of actual answer quality, which is misleading, not a real score.
            double wPrecision = precision != null ? 0.30 : 0.0;
            double wRecall = recall != null ? 0.20 : 0.0;
            double wAnswerRelevance = 0.20;
            double wFaithfulness = 0.15;
            double wContextPrecision = 0.15;

            double missingWeight = (precision == null ? 0.30 : 0.0) + (recall == null ? 0.20 : 0.0);
            if (missingWeight > 0) {
                double alwaysOnTotal = wAnswerRelevance + wFaithfulness + wContextPrecision;
                double scale = (alwaysOnTotal + missingWeight) / alwaysOnTotal;
                wAnswerRelevance *= scale;
                wFaithfulness *= scale;
                wContextPrecision *= scale;
            }

            BigDecimal score = BigDecimal.ZERO;
            if (precision != null) {
                score = score.add(precision.multiply(BigDecimal.valueOf(wPrecision)));
            }
            if (recall != null) {
                score = score.add(recall.multiply(BigDecimal.valueOf(wRecall)));
            }
            score = score.add(answerRelevance.multiply(BigDecimal.valueOf(wAnswerRelevance)))
                    .add(faithfulness.multiply(BigDecimal.valueOf(wFaithfulness)))
                    .add(contextPrecision.multiply(BigDecimal.valueOf(wContextPrecision)));

            return score.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Error computing RAG score", e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Cosine Similarity between two vectors
     */
    private double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.isEmpty() || vec2.isEmpty()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        int len = Math.min(vec1.size(), vec2.size());
        
        for (int i = 0; i < len; i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
