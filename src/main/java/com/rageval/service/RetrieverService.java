package com.rageval.service;

import com.rageval.model.DocumentChunk;
import com.rageval.model.Embedding;
import com.rageval.repository.EmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RetrieverService {
    
    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    
    public RetrieverService(EmbeddingService embeddingService,
                           EmbeddingRepository embeddingRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }
    
    /**
     * Retrieve top-K most relevant chunks for given query
     */
    public List<DocumentChunk> retrieveRelevantChunks(String query, int topK) {
        try {
            // Generate query embedding
            var queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding == null) {
                log.error("Failed to generate query embedding");
                return List.of();
            }
            
            String queryVectorStr = embeddingService.embeddingToVectorString(queryEmbedding);
            
            // Find similar embeddings
            List<Embedding> similarEmbeddings = embeddingRepository.findMostSimilar(queryVectorStr, topK);
            
            // Extract chunks
            return similarEmbeddings.stream()
                    .map(Embedding::getDocumentChunk)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error retrieving relevant chunks", e);
            return List.of();
        }
    }
}
