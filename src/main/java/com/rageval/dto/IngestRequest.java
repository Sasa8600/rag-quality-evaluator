package com.rageval.dto;

public record IngestRequest(String name, String content, String source, Integer chunkSize, Integer overlap) {
}
