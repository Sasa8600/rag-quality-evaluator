package com.rageval.controller;

import com.rageval.model.DocumentChunk;
import com.rageval.service.DocumentService;
import com.rageval.service.RetrieverService;
import com.rageval.service.GeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
@Slf4j
@CrossOrigin(origins = "*")
public class RagController {
    
    private final DocumentService documentService;
    private final RetrieverService retrieverService;
    private final GeneratorService generatorService;
    
    public RagController(DocumentService documentService,
                        RetrieverService retrieverService,
                        GeneratorService generatorService) {
        this.documentService = documentService;
        this.retrieverService = retrieverService;
        this.generatorService = generatorService;
    }
    
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> ingestDocument(
            @RequestParam String name,
            @RequestParam String content,
            @RequestParam(required = false) String source) {
        try {
            documentService.ingestDocument(name, content, source != null ? source : "uploaded");
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Document ingested: " + name);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error ingesting document", e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryRag(@RequestParam String query) {
        try {
            // Retrieve relevant chunks
            List<DocumentChunk> relevantChunks = retrieverService.retrieveRelevantChunks(query, 3);
            
            // Build context from chunks
            StringBuilder contextBuilder = new StringBuilder();
            for (DocumentChunk chunk : relevantChunks) {
                contextBuilder.append(chunk.getChunkText()).append("\n\n");
            }
            
            // Generate answer
            String answer = generatorService.generateAnswer(query, contextBuilder.toString());
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("answer", answer);
            response.put("retrievedChunks", relevantChunks.size());
            response.put("status", "success");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing query", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "RAG Evaluator");
        return ResponseEntity.ok(response);
    }
}
