package com.rageval.controller;

import com.rageval.dto.TestQueryRequest;
import com.rageval.model.EvaluationResult;
import com.rageval.model.EvaluationRun;
import com.rageval.model.RetrievalMode;
import com.rageval.model.TestQuery;
import com.rageval.service.EvaluationService;
import com.rageval.repository.TestQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/evaluation")
@Slf4j
@CrossOrigin(origins = "*")
public class EvaluationController {
    
    private final EvaluationService evaluationService;
    private final TestQueryRepository testQueryRepository;
    
    public EvaluationController(EvaluationService evaluationService,
                               TestQueryRepository testQueryRepository) {
        this.evaluationService = evaluationService;
        this.testQueryRepository = testQueryRepository;
    }
    
    @PostMapping("/evaluate-query/{queryId}")
    public ResponseEntity<Map<String, Object>> evaluateQuery(@PathVariable Long queryId) {
        try {
            TestQuery testQuery = testQueryRepository.findById(queryId)
                    .orElseThrow(() -> new RuntimeException("Query not found"));
            
            EvaluationResult result = evaluationService.evaluateSingleQuery(testQuery);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("result", result);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error evaluating query", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/batch-evaluate")
    public ResponseEntity<Map<String, Object>> batchEvaluate(
            @RequestParam(defaultValue = "batch-eval-run") String runName,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(defaultValue = "VECTOR") RetrievalMode retrievalMode) {
        try {
            EvaluationRun run = evaluationService.runBatchEvaluation(runName, topK, retrievalMode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("run", run);
            response.put("message", String.format("Evaluated %d queries", run.getTotalQueries()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error running batch evaluation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/runs")
    public ResponseEntity<Map<String, Object>> getRuns() {
        try {
            List<EvaluationRun> runs = evaluationService.getAllRuns();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("runs", runs);
            response.put("count", runs.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching evaluation runs", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getAllResults() {
        try {
            List<EvaluationResult> results = evaluationService.getAllResults();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("results", results);
            response.put("count", results.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching results", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/results/{queryId}")
    public ResponseEntity<Map<String, Object>> getResults(@PathVariable Long queryId) {
        try {
            List<EvaluationResult> results = evaluationService.getResultsByTestQuery(queryId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("results", results);
            response.put("count", results.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching results", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/results/export")
    public ResponseEntity<byte[]> exportResultsCsv() {
        List<EvaluationResult> results = evaluationService.getAllResults();

        StringBuilder csv = new StringBuilder();
        csv.append("id,test_query_id,query_text,precision_at_k,recall,hit_rate,mrr,ndcg,answer_relevance,")
           .append("faithfulness,hallucination_rate,context_precision,context_recall,rag_score,")
           .append("retrieval_latency_ms,generation_latency_ms,prompt_tokens,completion_tokens,estimated_cost_usd,created_at\n");

        for (EvaluationResult r : results) {
            csv.append(r.getId()).append(",")
               .append(r.getTestQuery() != null ? r.getTestQuery().getId() : "").append(",")
               .append(csvEscape(r.getTestQuery() != null ? r.getTestQuery().getQueryText() : "")).append(",")
               .append(nullSafe(r.getPrecisionAtK())).append(",")
               .append(nullSafe(r.getRecall())).append(",")
               .append(nullSafe(r.getHitRate())).append(",")
               .append(nullSafe(r.getMrr())).append(",")
               .append(nullSafe(r.getNdcg())).append(",")
               .append(nullSafe(r.getAnswerRelevance())).append(",")
               .append(nullSafe(r.getFaithfulness())).append(",")
               .append(nullSafe(r.getHallucinationRate())).append(",")
               .append(nullSafe(r.getContextPrecision())).append(",")
               .append(nullSafe(r.getContextRecall())).append(",")
               .append(nullSafe(r.getRagScore())).append(",")
               .append(nullSafe(r.getRetrievalLatencyMs())).append(",")
               .append(nullSafe(r.getGenerationLatencyMs())).append(",")
               .append(nullSafe(r.getPromptTokens())).append(",")
               .append(nullSafe(r.getCompletionTokens())).append(",")
               .append(nullSafe(r.getEstimatedCostUsd())).append(",")
               .append(r.getCreatedAt()).append("\n");
        }

        return csvResponse(csv.toString(), "evaluation-results.csv");
    }

    @GetMapping("/runs/export")
    public ResponseEntity<byte[]> exportRunsCsv() {
        List<EvaluationRun> runs = evaluationService.getAllRuns();

        StringBuilder csv = new StringBuilder();
        csv.append("id,run_name,total_queries,avg_rag_score,avg_precision,avg_recall,avg_hit_rate,")
           .append("avg_answer_relevance,avg_faithfulness,avg_hallucination_rate,")
           .append("avg_retrieval_latency_ms,avg_generation_latency_ms,total_cost_usd,created_at\n");

        for (EvaluationRun r : runs) {
            csv.append(r.getId()).append(",")
               .append(csvEscape(r.getRunName())).append(",")
               .append(r.getTotalQueries()).append(",")
               .append(nullSafe(r.getAvgRagScore())).append(",")
               .append(nullSafe(r.getAvgPrecision())).append(",")
               .append(nullSafe(r.getAvgRecall())).append(",")
               .append(nullSafe(r.getAvgHitRate())).append(",")
               .append(nullSafe(r.getAvgAnswerRelevance())).append(",")
               .append(nullSafe(r.getAvgFaithfulness())).append(",")
               .append(nullSafe(r.getAvgHallucinationRate())).append(",")
               .append(nullSafe(r.getAvgRetrievalLatencyMs())).append(",")
               .append(nullSafe(r.getAvgGenerationLatencyMs())).append(",")
               .append(nullSafe(r.getTotalCostUsd())).append(",")
               .append(r.getCreatedAt()).append("\n");
        }

        return csvResponse(csv.toString(), "evaluation-runs.csv");
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    @GetMapping("/test-queries")
    public ResponseEntity<Map<String, Object>> getTestQueries() {
        try {
            List<TestQuery> queries = testQueryRepository.findAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("queries", queries);
            response.put("count", queries.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching test queries", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/test-queries")
    public ResponseEntity<Map<String, Object>> createTestQuery(@RequestBody TestQueryRequest request) {
        try {
            if (request.queryText() == null || request.queryText().isBlank()) {
                throw new IllegalArgumentException("queryText is required");
            }

            TestQuery testQuery = TestQuery.builder()
                    .queryText(request.queryText())
                    .expectedAnswer(request.expectedAnswer())
                    // Blank is treated the same as absent: evaluateSingleQuery skips
                    // Precision@K/Recall/MRR/NDCG/Context Recall (reports them as
                    // unavailable) rather than silently scoring against an empty list.
                    .relevantDocIds(request.relevantDocIds() != null && !request.relevantDocIds().isBlank()
                            ? request.relevantDocIds().trim() : null)
                    .build();
            testQuery = testQueryRepository.save(testQuery);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("testQuery", testQuery);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error creating test query", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/test-queries/{id}")
    public ResponseEntity<Map<String, Object>> deleteTestQuery(@PathVariable Long id) {
        try {
            if (!testQueryRepository.existsById(id)) {
                throw new RuntimeException("Test query not found: " + id);
            }
            testQueryRepository.deleteById(id);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting test query", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/seed-test-data")
    public ResponseEntity<Map<String, Object>> seedTestData() {
        try {
            List<TestQuery> testData = createSampleTestQueries();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("seeded", testData.size());
            response.put("message", String.format("Seeded %d test queries", testData.size()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error seeding test data", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    private List<TestQuery> createSampleTestQueries() {
        // Sample test queries (will be populated with real data)
        List<TestQuery> testQueries = List.of(
            TestQuery.builder()
                    .queryText("What is fraud detection?")
                    .expectedAnswer("Fraud detection involves monitoring transactions for unusual patterns")
                    .relevantDocIds("1,2,3")
                    .build(),
            TestQuery.builder()
                    .queryText("How do payment systems work?")
                    .expectedAnswer("Payment systems involve authorization, clearing, and settlement phases")
                    .relevantDocIds("2,4")
                    .build(),
            TestQuery.builder()
                    .queryText("What is KYC compliance?")
                    .expectedAnswer("KYC (Know Your Customer) is verifying customer identity")
                    .relevantDocIds("3,5")
                    .build()
        );
        
        return testQueryRepository.saveAll(testQueries);
    }
}
