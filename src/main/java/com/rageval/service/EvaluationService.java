package com.rageval.service;

import com.rageval.model.*;
import com.rageval.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EvaluationService {
    
    private final EvaluationMetricsService metricsService;
    private final RetrieverService retrieverService;
    private final GeneratorService generatorService;
    private final TestQueryRepository testQueryRepository;
    private final EvaluationResultRepository resultRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    
    public EvaluationService(EvaluationMetricsService metricsService,
                            RetrieverService retrieverService,
                            GeneratorService generatorService,
                            TestQueryRepository testQueryRepository,
                            EvaluationResultRepository resultRepository,
                            EvaluationRunRepository evaluationRunRepository) {
        this.metricsService = metricsService;
        this.retrieverService = retrieverService;
        this.generatorService = generatorService;
        this.testQueryRepository = testQueryRepository;
        this.resultRepository = resultRepository;
        this.evaluationRunRepository = evaluationRunRepository;
    }
    
    @Transactional
    public EvaluationResult evaluateSingleQuery(TestQuery testQuery) {
        try {
            log.info("Evaluating query: {}", testQuery.getQueryText());
            
            // Step 1: Retrieve relevant chunks
            List<DocumentChunk> retrievedChunks = retrieverService.retrieveRelevantChunks(
                    testQuery.getQueryText(), 3);
            
            List<Long> retrievedChunkIds = retrievedChunks.stream()
                    .map(DocumentChunk::getId)
                    .collect(Collectors.toList());
            
            // Step 2: Generate answer
            StringBuilder contextBuilder = new StringBuilder();
            for (DocumentChunk chunk : retrievedChunks) {
                contextBuilder.append(chunk.getChunkText()).append("\n\n");
            }
            
            String generatedAnswer = generatorService.generateAnswer(
                    testQuery.getQueryText(), 
                    contextBuilder.toString()
            );
            
            // Step 3: Parse ground truth relevant docs
            List<Long> relevantChunkIds = parseRelevantDocIds(testQuery.getRelevantDocIds());
            
            // Step 4: Compute all metrics
            BigDecimal precisionAtK = metricsService.computePrecisionAtK(
                    retrievedChunkIds, relevantChunkIds, 3);
            
            BigDecimal recall = metricsService.computeRecall(
                    retrievedChunkIds, relevantChunkIds);
            
            BigDecimal mrr = metricsService.computeMRR(
                    retrievedChunkIds, relevantChunkIds);
            
            BigDecimal ndcg = metricsService.computeNDCG(
                    retrievedChunkIds, relevantChunkIds, 3);
            
            BigDecimal answerRelevance = metricsService.computeAnswerRelevance(
                    testQuery.getQueryText(), generatedAnswer);
            
            BigDecimal faithfulness = metricsService.computeFaithfulness(
                    generatedAnswer, contextBuilder.toString());
            
            BigDecimal contextPrecision = metricsService.computeContextPrecision(
                    testQuery.getQueryText(), contextBuilder.toString());
            
            BigDecimal contextRecall = metricsService.computeContextRecall(
                    retrievedChunkIds, relevantChunkIds);
            
            // Step 5: Compute overall RAG score
            BigDecimal ragScore = metricsService.computeRAGScore(
                    precisionAtK, recall, answerRelevance, faithfulness, contextPrecision);
            
            // Step 6: Store results
            EvaluationResult result = EvaluationResult.builder()
                    .testQuery(testQuery)
                    .generatedAnswer(generatedAnswer)
                    .retrievedChunkIds(retrievedChunkIds.toString())
                    .precisionAtK(precisionAtK)
                    .recall(recall)
                    .mrr(mrr)
                    .ndcg(ndcg)
                    .answerRelevance(answerRelevance)
                    .faithfulness(faithfulness)
                    .contextPrecision(contextPrecision)
                    .contextRecall(contextRecall)
                    .ragScore(ragScore)
                    .build();
            
            result = resultRepository.save(result);
            
            log.info("Query evaluated with RAG score: {}", ragScore);
            return result;
            
        } catch (Exception e) {
            log.error("Error evaluating query", e);
            throw new RuntimeException("Failed to evaluate query", e);
        }
    }
    
    @Transactional
    public EvaluationRun runBatchEvaluation(String runName) {
        try {
            log.info("Starting batch evaluation: {}", runName);
            
            // Get all test queries
            List<TestQuery> testQueries = testQueryRepository.findAll();
            
            if (testQueries.isEmpty()) {
                throw new RuntimeException("No test queries found. Please seed test data first.");
            }
            
            // Evaluate each query
            List<EvaluationResult> results = testQueries.stream()
                    .map(this::evaluateSingleQuery)
                    .collect(Collectors.toList());
            
            // Compute averages
            BigDecimal avgRagScore = resultRepository.findAverageRagScore();
            BigDecimal avgPrecision = resultRepository.findAveragePrecision();
            BigDecimal avgRecall = resultRepository.findAverageRecall();
            
            // Create evaluation run
            EvaluationRun run = EvaluationRun.builder()
                    .runName(runName)
                    .totalQueries(testQueries.size())
                    .avgRagScore(avgRagScore != null ? avgRagScore : BigDecimal.ZERO)
                    .avgPrecision(avgPrecision != null ? avgPrecision : BigDecimal.ZERO)
                    .avgRecall(avgRecall != null ? avgRecall : BigDecimal.ZERO)
                    .build();
            
            run = evaluationRunRepository.save(run);
            
            log.info("Batch evaluation complete. Avg RAG Score: {}", avgRagScore);
            return run;
            
        } catch (Exception e) {
            log.error("Error running batch evaluation", e);
            throw new RuntimeException("Failed to run batch evaluation", e);
        }
    }
    
    private List<Long> parseRelevantDocIds(String docIdsString) {
        if (docIdsString == null || docIdsString.isEmpty()) {
            return List.of();
        }
        
        return Arrays.stream(docIdsString.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
    
    public List<EvaluationResult> getResultsByTestQuery(Long testQueryId) {
        return resultRepository.findByTestQueryId(testQueryId);
    }
}
