package com.rageval.controller;

import com.rageval.model.EvaluationResult;
import com.rageval.model.EvaluationRun;
import com.rageval.model.TestQuery;
import com.rageval.service.EvaluationService;
import com.rageval.repository.TestQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(defaultValue = "batch-eval-run") String runName) {
        try {
            EvaluationRun run = evaluationService.runBatchEvaluation(runName);
            
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
