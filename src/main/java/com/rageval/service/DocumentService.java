package com.rageval.service;

import com.rageval.model.Document;
import com.rageval.model.DocumentChunk;
import com.rageval.model.Embedding;
import com.rageval.repository.DocumentRepository;
import com.rageval.repository.DocumentChunkRepository;
import com.rageval.repository.EmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    
    public DocumentService(DocumentRepository documentRepository,
                          DocumentChunkRepository documentChunkRepository,
                          EmbeddingRepository embeddingRepository,
                          EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
    }
    
    @Transactional
    public void ingestDocument(String name, String content, String source) {
        try {
            // Save document
            Document doc = Document.builder()
                    .name(name)
                    .content(content)
                    .source(source)
                    .build();
            doc = documentRepository.save(doc);
            
            log.info("Document saved: {} (ID: {})", name, doc.getId());
            
            // Chunk document
            List<DocumentChunk> chunks = chunkDocument(doc, content);
            documentChunkRepository.saveAll(chunks);
            
            log.info("Document chunked into {} parts", chunks.size());
            
            // Generate embeddings for chunks
            int embeddingCount = 0;
            for (DocumentChunk chunk : chunks) {
                try {
                    embedChunk(chunk);
                    embeddingCount++;
                } catch (Exception e) {
                    log.error("Failed to embed chunk {}: {}", chunk.getId(), e.getMessage());
                }
            }
            
            log.info("Successfully generated {} embeddings for document: {}", embeddingCount, name);
            
        } catch (Exception e) {
            log.error("Error ingesting document: {}", name, e);
            throw new RuntimeException("Failed to ingest document: " + e.getMessage(), e);
        }
    }
    
    private List<DocumentChunk> chunkDocument(Document document, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkSize = 500;
        int overlap = 50;
        int index = 0;
        
        for (int i = 0; i < content.length(); i += chunkSize - overlap) {
            int end = Math.min(i + chunkSize, content.length());
            String chunkText = content.substring(i, end);
            
            chunks.add(DocumentChunk.builder()
                    .document(document)
                    .chunkText(chunkText)
                    .chunkIndex(index++)
                    .build());
        }
        
        return chunks;
    }
    
    private void embedChunk(DocumentChunk chunk) {
        try {
            List<Double> embedding = embeddingService.generateEmbedding(chunk.getChunkText());
            if (embedding == null || embedding.isEmpty()) {
                log.warn("No embedding generated for chunk {}", chunk.getId());
                return;
            }
            
            String vectorStr = embeddingService.embeddingToVectorString(embedding);
            log.debug("Saving embedding for chunk {}: {}", chunk.getId(), vectorStr.substring(0, Math.min(50, vectorStr.length())));
            
            embeddingRepository.saveEmbeddingWithVector(chunk.getId(), vectorStr);
            
            log.debug("Embedding saved successfully for chunk {}", chunk.getId());
        } catch (Exception e) {
            log.error("Error embedding chunk {}: {}", chunk.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to embed chunk: " + e.getMessage(), e);
        }
    }
    
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new RuntimeException("Document not found: " + id);
        }
        documentRepository.deleteById(id);
        log.info("Document deleted: {}", id);
    }
}
