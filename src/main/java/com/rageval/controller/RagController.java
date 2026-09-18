package com.rageval.controller;

import com.rageval.config.LlmConfig;
import com.rageval.dto.IngestRequest;
import com.rageval.dto.QueryRequest;
import com.rageval.model.Document;
import com.rageval.model.DocumentChunk;
import com.rageval.model.RetrievalMode;
import com.rageval.service.DocumentService;
import com.rageval.service.RetrieverService;
import com.rageval.service.GeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final LlmConfig llmConfig;

    public RagController(DocumentService documentService,
                        RetrieverService retrieverService,
                        GeneratorService generatorService,
                        LlmConfig llmConfig) {
        this.documentService = documentService;
        this.retrieverService = retrieverService;
        this.generatorService = generatorService;
        this.llmConfig = llmConfig;
    }

    @PostMapping(value = "/ingest", consumes = "application/json")
    public ResponseEntity<Map<String, String>> ingestDocument(@RequestBody IngestRequest request) {
        try {
            if (request.name() == null || request.name().isBlank()
                    || request.content() == null || request.content().isBlank()) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "name and content are required");
                return ResponseEntity.badRequest().body(response);
            }

            documentService.ingestDocument(request.name(), request.content(),
                    request.source() != null ? request.source() : "uploaded",
                    request.chunkSize(), request.overlap());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Document ingested: " + request.name());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error ingesting document", e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        try {
            List<Document> documents = documentService.getAllDocuments();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("documents", documents);
            response.put("count", documents.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching documents", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        try {
            documentService.deleteDocument(id);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Document deleted: " + id);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting document", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping(value = "/ingest-pdf", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> ingestPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer chunkSize,
            @RequestParam(required = false) Integer overlap) {
        Map<String, String> response = new HashMap<>();
        try {
            if (file.isEmpty()) {
                response.put("status", "error");
                response.put("message", "No file uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            String extractedText;
            try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                extractedText = stripper.getText(pdf);
            }

            if (extractedText == null || extractedText.isBlank()) {
                response.put("status", "error");
                response.put("message", "Could not extract any text from this PDF (it may be scanned/image-only)");
                return ResponseEntity.badRequest().body(response);
            }

            String docName = (name != null && !name.isBlank())
                    ? name
                    : file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded.pdf";

            documentService.ingestDocument(docName, extractedText,
                    source != null ? source : "pdf-upload", chunkSize, overlap);

            response.put("status", "success");
            response.put("message", "PDF ingested: " + docName + " (" + extractedText.length() + " chars extracted)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error ingesting PDF", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping(value = "/query", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> queryRag(@RequestBody QueryRequest request) {
        try {
            String query = request.query();
            if (query == null || query.isBlank()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "query is required");
                return ResponseEntity.badRequest().body(response);
            }

            int topK = (request.topK() != null && request.topK() > 0) ? request.topK() : 3;
            RetrievalMode mode = request.retrievalMode() != null ? request.retrievalMode() : RetrievalMode.VECTOR;

            // Retrieve relevant chunks
            List<DocumentChunk> relevantChunks = retrieverService.retrieveRelevantChunks(query, topK, mode);

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
            response.put("topK", topK);
            response.put("retrievalMode", mode.name());
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
        response.put("llmProvider", llmConfig.isCloud() ? "cloud" : "ollama");
        return ResponseEntity.ok(response);
    }
}
