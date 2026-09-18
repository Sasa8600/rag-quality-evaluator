package com.rageval.service;

import com.rageval.model.DocumentChunk;
import com.rageval.model.Embedding;
import com.rageval.model.RetrievalMode;
import com.rageval.repository.DocumentChunkRepository;
import com.rageval.repository.EmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RetrieverService {

    // Reciprocal Rank Fusion constant — standard choice (dampens the impact of rank 1
    // vs rank 2 so one list can't completely dominate the other).
    private static final int RRF_K = 60;
    // How many candidates to pull from each individual list before fusing, relative to topK.
    private static final int CANDIDATE_MULTIPLIER = 3;

    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public RetrieverService(EmbeddingService embeddingService,
                           EmbeddingRepository embeddingRepository,
                           DocumentChunkRepository documentChunkRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    /** Backward-compatible overload: pure vector search, as before Phase 5. */
    public List<DocumentChunk> retrieveRelevantChunks(String query, int topK) {
        return retrieveRelevantChunks(query, topK, RetrievalMode.VECTOR);
    }

    public List<DocumentChunk> retrieveRelevantChunks(String query, int topK, RetrievalMode mode) {
        if (mode == null) mode = RetrievalMode.VECTOR;

        try {
            switch (mode) {
                case KEYWORD:
                    return documentChunkRepository.findByKeywordSearch(query, topK);
                case HYBRID:
                    return hybridRetrieve(query, topK);
                case VECTOR:
                default:
                    return vectorRetrieve(query, topK);
            }
        } catch (Exception e) {
            log.error("Error retrieving relevant chunks (mode={})", mode, e);
            return List.of();
        }
    }

    private List<DocumentChunk> vectorRetrieve(String query, int topK) {
        var queryEmbedding = embeddingService.generateEmbedding(query);
        if (queryEmbedding == null) {
            log.error("Failed to generate query embedding");
            return List.of();
        }
        String queryVectorStr = embeddingService.embeddingToVectorString(queryEmbedding);
        List<Embedding> similarEmbeddings = embeddingRepository.findMostSimilar(queryVectorStr, topK);
        return similarEmbeddings.stream()
                .map(Embedding::getDocumentChunk)
                .collect(Collectors.toList());
    }

    /**
     * Reciprocal Rank Fusion: combine an independently-ranked vector list and keyword
     * list into one ranking, without needing directly comparable similarity scores.
     * score(chunk) = sum over lists it appears in of 1 / (RRF_K + rank_in_that_list)
     */
    private List<DocumentChunk> hybridRetrieve(String query, int topK) {
        int candidatePool = Math.max(topK * CANDIDATE_MULTIPLIER, topK);

        List<DocumentChunk> vectorResults = vectorRetrieve(query, candidatePool);
        List<DocumentChunk> keywordResults = documentChunkRepository.findByKeywordSearch(query, candidatePool);

        Map<Long, Double> fusedScores = new LinkedHashMap<>();
        Map<Long, DocumentChunk> chunksById = new LinkedHashMap<>();

        addRankedScores(vectorResults, fusedScores, chunksById);
        addRankedScores(keywordResults, fusedScores, chunksById);

        return fusedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> chunksById.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    private void addRankedScores(List<DocumentChunk> ranked, Map<Long, Double> fusedScores,
                                  Map<Long, DocumentChunk> chunksById) {
        for (int i = 0; i < ranked.size(); i++) {
            DocumentChunk chunk = ranked.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            fusedScores.merge(chunk.getId(), rrfScore, Double::sum);
            chunksById.putIfAbsent(chunk.getId(), chunk);
        }
    }
}
