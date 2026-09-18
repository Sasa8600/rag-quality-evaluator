package com.rageval.service;

import com.rageval.config.EvaluationProgressHandler;
import com.rageval.config.LlmConfig;
import com.rageval.model.*;
import com.rageval.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EvaluationService {

    private static final int DEFAULT_TOP_K = 3;
    private static final RetrievalMode DEFAULT_MODE = RetrievalMode.VECTOR;

    private final EvaluationMetricsService metricsService;
    private final RetrieverService retrieverService;
    private final GeneratorService generatorService;
    private final TestQueryRepository testQueryRepository;
    private final EvaluationResultRepository resultRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationProgressHandler progressHandler;
    private final LlmConfig llmConfig;

    public EvaluationService(EvaluationMetricsService metricsService,
                            RetrieverService retrieverService,
                            GeneratorService generatorService,
                            TestQueryRepository testQueryRepository,
                            EvaluationResultRepository resultRepository,
                            EvaluationRunRepository evaluationRunRepository,
                            EvaluationProgressHandler progressHandler,
                            LlmConfig llmConfig) {
        this.metricsService = metricsService;
        this.retrieverService = retrieverService;
        this.generatorService = generatorService;
        this.testQueryRepository = testQueryRepository;
        this.resultRepository = resultRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.progressHandler = progressHandler;
        this.llmConfig = llmConfig;
    }

    /**
     * Approximate $ cost of one generation call, from its own token usage. Zero for local
     * Ollama — that really is free, a known fact, not "unknown". Null in cloud mode only when
     * the per-token price isn't configured (see LlmConfig.Groq) or token counts are missing —
     * genuinely unknown, deliberately not defaulted to 0 (which would silently under-report).
     * Scope note: this does NOT include the separate hallucination-check LLM call's own token
     * cost — tracked here as a known, documented gap rather than folded in silently.
     */
    private BigDecimal computeCostUsd(Integer promptTokens, Integer completionTokens) {
        if (!llmConfig.isCloud()) {
            return BigDecimal.ZERO;
        }
        LlmConfig.Groq groq = llmConfig.getGroq();
        BigDecimal inputPrice = groq.getPricePerMillionInputTokens();
        BigDecimal outputPrice = groq.getPricePerMillionOutputTokens();
        if (inputPrice == null || outputPrice == null || promptTokens == null || completionTokens == null) {
            return null;
        }
        BigDecimal inputCost = inputPrice.multiply(BigDecimal.valueOf(promptTokens))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = outputPrice.multiply(BigDecimal.valueOf(completionTokens))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    private Integer averageInt(List<EvaluationResult> results, Function<EvaluationResult, Integer> extractor) {
        List<Integer> values = results.stream().map(extractor).filter(v -> v != null).collect(Collectors.toList());
        if (values.isEmpty()) return null;
        long sum = values.stream().mapToLong(Integer::intValue).sum();
        return (int) Math.round((double) sum / values.size());
    }

    /** Sum of whatever per-query costs ARE known in this run; null only if none are. */
    private BigDecimal sumCost(List<EvaluationResult> results) {
        List<BigDecimal> values = results.stream()
                .map(EvaluationResult::getEstimatedCostUsd)
                .filter(v -> v != null)
                .collect(Collectors.toList());
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public EvaluationResult evaluateSingleQuery(TestQuery testQuery) {
        return evaluateSingleQuery(testQuery, DEFAULT_TOP_K, DEFAULT_MODE);
    }

    @Transactional
    public EvaluationResult evaluateSingleQuery(TestQuery testQuery, int topK, RetrievalMode mode) {
        try {
            log.info("Evaluating query: {} (topK={}, mode={})", testQuery.getQueryText(), topK, mode);

            // Step 1: Retrieve relevant chunks (timed for the Latency metric)
            long retrievalStart = System.currentTimeMillis();
            List<DocumentChunk> retrievedChunks = retrieverService.retrieveRelevantChunks(
                    testQuery.getQueryText(), topK, mode);
            int retrievalLatencyMs = (int) (System.currentTimeMillis() - retrievalStart);

            List<Long> retrievedChunkIds = retrievedChunks.stream()
                    .map(DocumentChunk::getId)
                    .collect(Collectors.toList());

            // Step 2: Generate answer — generateAnswerWithMeta also returns latency and token
            // usage (used below for the Latency and Cost metrics), measured around the actual
            // HTTP call inside GeneratorService, not approximated at this call site.
            StringBuilder contextBuilder = new StringBuilder();
            for (DocumentChunk chunk : retrievedChunks) {
                contextBuilder.append(chunk.getChunkText()).append("\n\n");
            }

            GeneratorService.LlmCallResult generationResult = generatorService.generateAnswerWithMeta(
                    testQuery.getQueryText(),
                    contextBuilder.toString()
            );
            String generatedAnswer = generationResult.text();

            // Step 3: Parse ground truth relevant docs
            List<Long> relevantChunkIds = parseRelevantDocIds(testQuery.getRelevantDocIds());
            // A test query created without relevantDocIds has no ground truth to compare
            // retrieval against. Reporting Precision@K/Recall/MRR/NDCG/Context Recall as 0.00
            // in that case previously looked identical to "retrieval found nothing relevant" —
            // a real bug found by testing against custom queries whose relevantDocIds didn't
            // match any real chunk. These five metrics are now left null (reported as
            // unavailable, not zero) whenever there's no ground truth to score against.
            boolean hasGroundTruth = !relevantChunkIds.isEmpty();

            // Step 4: Compute all metrics
            BigDecimal precisionAtK = hasGroundTruth
                    ? metricsService.computePrecisionAtK(retrievedChunkIds, relevantChunkIds, topK)
                    : null;

            BigDecimal recall = hasGroundTruth
                    ? metricsService.computeRecall(retrievedChunkIds, relevantChunkIds)
                    : null;

            BigDecimal hitRate = hasGroundTruth
                    ? metricsService.computeHitRate(retrievedChunkIds, relevantChunkIds)
                    : null;

            BigDecimal mrr = hasGroundTruth
                    ? metricsService.computeMRR(retrievedChunkIds, relevantChunkIds)
                    : null;

            BigDecimal ndcg = hasGroundTruth
                    ? metricsService.computeNDCG(retrievedChunkIds, relevantChunkIds, topK)
                    : null;

            BigDecimal answerRelevance = metricsService.computeAnswerRelevance(
                    testQuery.getQueryText(), generatedAnswer);

            BigDecimal faithfulness = metricsService.computeFaithfulness(
                    generatedAnswer, contextBuilder.toString());

            // Real, claim-level hallucination check (not derived from Faithfulness — see the
            // corrected Design Decisions entry #4: Faithfulness is a cosine-similarity proxy,
            // this is an independent LLM verification of each sentence in the answer).
            GeneratorService.HallucinationCheckResult hallucinationCheck =
                    generatorService.verifyClaims(generatedAnswer, contextBuilder.toString());
            BigDecimal hallucinationRate = hallucinationCheck.hallucinationRate();

            BigDecimal estimatedCostUsd = computeCostUsd(
                    generationResult.promptTokens(), generationResult.completionTokens());

            BigDecimal contextPrecision = metricsService.computeContextPrecision(
                    testQuery.getQueryText(), contextBuilder.toString());

            BigDecimal contextRecall = hasGroundTruth
                    ? metricsService.computeContextRecall(retrievedChunkIds, relevantChunkIds)
                    : null;

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
                    .hitRate(hitRate)
                    .mrr(mrr)
                    .ndcg(ndcg)
                    .answerRelevance(answerRelevance)
                    .faithfulness(faithfulness)
                    .contextPrecision(contextPrecision)
                    .contextRecall(contextRecall)
                    .ragScore(ragScore)
                    .hallucinationRate(hallucinationRate)
                    .retrievalLatencyMs(retrievalLatencyMs)
                    .generationLatencyMs((int) generationResult.latencyMs())
                    .promptTokens(generationResult.promptTokens())
                    .completionTokens(generationResult.completionTokens())
                    .estimatedCostUsd(estimatedCostUsd)
                    .build();

            result = resultRepository.save(result);

            log.info("Query evaluated with RAG score: {}", ragScore);
            return result;

        } catch (Exception e) {
            log.error("Error evaluating query", e);
            throw new RuntimeException("Failed to evaluate query", e);
        }
    }

    public EvaluationRun runBatchEvaluation(String runName) {
        return runBatchEvaluation(runName, DEFAULT_TOP_K, DEFAULT_MODE);
    }

    @Transactional
    public EvaluationRun runBatchEvaluation(String runName, int topK, RetrievalMode mode) {
        MDC.put("runName", runName);
        try {
            log.info("Starting batch evaluation: {} (topK={}, mode={})", runName, topK, mode);

            // Get all test queries
            List<TestQuery> testQueries = testQueryRepository.findAll();

            if (testQueries.isEmpty()) {
                throw new RuntimeException("No test queries found. Please seed test data first.");
            }

            int total = testQueries.size();
            List<EvaluationResult> results = new ArrayList<>();

            broadcastProgress(0, total, "started", null, runName);

            int current = 0;
            for (TestQuery testQuery : testQueries) {
                MDC.put("queryId", String.valueOf(testQuery.getId()));
                try {
                    EvaluationResult result = evaluateSingleQuery(testQuery, topK, mode);
                    results.add(result);
                    current++;
                    broadcastProgress(current, total, "in_progress", result, runName);
                } finally {
                    MDC.remove("queryId");
                }
            }

            // Compute averages FROM THIS RUN'S RESULTS ONLY (not a global table average —
            // that would make every run's averages converge to the same number and break
            // any attempt to compare two configurations against each other).
            EvaluationRun run = EvaluationRun.builder()
                    .runName(runName)
                    .totalQueries(testQueries.size())
                    .avgRagScore(average(results, EvaluationResult::getRagScore))
                    .avgPrecision(average(results, EvaluationResult::getPrecisionAtK))
                    .avgRecall(average(results, EvaluationResult::getRecall))
                    .avgHitRate(average(results, EvaluationResult::getHitRate))
                    .avgAnswerRelevance(average(results, EvaluationResult::getAnswerRelevance))
                    .avgFaithfulness(average(results, EvaluationResult::getFaithfulness))
                    .avgHallucinationRate(average(results, EvaluationResult::getHallucinationRate))
                    .avgRetrievalLatencyMs(averageInt(results, EvaluationResult::getRetrievalLatencyMs))
                    .avgGenerationLatencyMs(averageInt(results, EvaluationResult::getGenerationLatencyMs))
                    .totalCostUsd(sumCost(results))
                    .topK(topK)
                    .retrievalMode(mode)
                    .build();

            run = evaluationRunRepository.save(run);

            broadcastProgress(total, total, "complete", null, runName);

            log.info("Batch evaluation complete. Avg RAG Score: {}", run.getAvgRagScore());
            return run;

        } catch (Exception e) {
            log.error("Error running batch evaluation", e);
            broadcastProgress(-1, -1, "error", null, runName);
            throw new RuntimeException("Failed to run batch evaluation", e);
        } finally {
            MDC.remove("runName");
        }
    }

    private BigDecimal average(List<EvaluationResult> results, Function<EvaluationResult, BigDecimal> extractor) {
        List<BigDecimal> values = results.stream()
                .map(extractor)
                .filter(v -> v != null)
                .collect(Collectors.toList());
        // null (not 0.00) when nothing in this run had a value to average — e.g. every
        // query in the run had no ground-truth relevantDocIds, so Precision@K/Recall
        // were never computed for any of them. A 0.00 average there would look like
        // "we checked and found nothing relevant", which isn't what happened.
        if (values.isEmpty()) return null;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private void broadcastProgress(int current, int total, String status, EvaluationResult lastResult, String runName) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "evaluation-progress");
            payload.put("runName", runName);
            payload.put("current", current);
            payload.put("total", total);
            payload.put("status", status);
            if (lastResult != null) {
                payload.put("lastQuery", lastResult.getTestQuery().getQueryText());
                payload.put("lastRagScore", lastResult.getRagScore());
            }
            progressHandler.broadcast(payload);
        } catch (Exception e) {
            log.warn("Failed to broadcast progress: {}", e.getMessage());
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

    public List<EvaluationResult> getAllResults() {
        return resultRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<EvaluationRun> getAllRuns() {
        return evaluationRunRepository.findAllByOrderByCreatedAtDesc();
    }
}
