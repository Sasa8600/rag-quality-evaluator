package com.rageval.dto;

import com.rageval.model.RetrievalMode;

public record QueryRequest(String query, Integer topK, RetrievalMode retrievalMode) {
}
