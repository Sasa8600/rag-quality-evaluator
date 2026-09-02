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
            BigDecimal score = precision.multiply(BigDecimal.valueOf(0.3))
                    .add(recall.multiply(BigDecimal.valueOf(0.2)))
                    .add(answerRelevance.multiply(BigDecimal.valueOf(0.2)))
                    .add(faithfulness.multiply(BigDecimal.valueOf(0.15)))
                    .add(contextPrecision.multiply(BigDecimal.valueOf(0.15)));
            
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
