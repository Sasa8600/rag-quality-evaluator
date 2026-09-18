package com.rageval.service;

import com.rageval.model.Document;
import com.rageval.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DocumentService {

    private static final int DEFAULT_CHUNK_SIZE = 600;
    // Bumped from 50 (10% of chunk size) to 150 (25%). Recall/Context Recall were
    // stuck at 0.50 because a fact sitting near a chunk boundary existed in only ONE
    // chunk before — if that specific chunk wasn't the one retrieved, the fact was
    // effectively invisible to the LLM even though it was technically "in the corpus".
    // A bigger overlap duplicates boundary content into the neighboring chunk too, so
    // there are now two chances for a boundary fact to surface in the retrieved set
    // instead of one. Chunk size nudged up slightly (500 -> 600) alongside it so the
    // overlap-to-content ratio doesn't get too wasteful.
    private static final int DEFAULT_OVERLAP = 150;

    // Tried in order, biggest semantic boundary first: paragraph, line, sentence, word.
    // If none of these appear in a piece that is still too big, splitRecursive falls
    // back to a hard character cut so chunking always terminates.
    private static final String[] SEPARATORS = {"\n\n", "\n", ". ", " "};

    private final com.rageval.repository.DocumentRepository documentRepository;
    private final com.rageval.repository.DocumentChunkRepository documentChunkRepository;
    private final com.rageval.repository.EmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;

    public DocumentService(com.rageval.repository.DocumentRepository documentRepository,
                          com.rageval.repository.DocumentChunkRepository documentChunkRepository,
                          com.rageval.repository.EmbeddingRepository embeddingRepository,
                          EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
    }

    public void ingestDocument(String name, String content, String source) {
        ingestDocument(name, content, source, null, null);
    }

    @Transactional
    public void ingestDocument(String name, String content, String source, Integer chunkSize, Integer overlap) {
        int effectiveChunkSize = (chunkSize != null && chunkSize > 0) ? chunkSize : DEFAULT_CHUNK_SIZE;
        int effectiveOverlap = (overlap != null && overlap >= 0) ? overlap : DEFAULT_OVERLAP;

        // Guard against a degenerate/infinite chunking loop: overlap must be strictly
        // smaller than chunk size, otherwise the sliding window never advances.
        if (effectiveOverlap >= effectiveChunkSize) {
            throw new IllegalArgumentException(
                    "overlap (" + effectiveOverlap + ") must be smaller than chunkSize (" + effectiveChunkSize + ")");
        }

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
            List<DocumentChunk> chunks = chunkDocument(doc, content, effectiveChunkSize, effectiveOverlap);
            documentChunkRepository.saveAll(chunks);

            log.info("Document chunked into {} parts (chunkSize={}, overlap={})",
                    chunks.size(), effectiveChunkSize, effectiveOverlap);

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

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error ingesting document: {}", name, e);
            throw new RuntimeException("Failed to ingest document: " + e.getMessage(), e);
        }
    }

    private List<DocumentChunk> chunkDocument(Document document, String content, int chunkSize, int overlap) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> segments = splitRecursive(content, chunkSize);
        List<String> merged = mergeSegments(segments, chunkSize, overlap);

        List<DocumentChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String chunkText : merged) {
            chunks.add(DocumentChunk.builder()
                    .document(document)
                    .chunkText(chunkText)
                    .chunkIndex(index++)
                    .build());
        }
        return chunks;
    }

    private List<String> splitRecursive(String text, int chunkSize) {
        return splitRecursive(text, chunkSize, SEPARATORS, 0);
    }

    private List<String> splitRecursive(String content, int chunkSize, String[] separators, int separatorIndex) {
        List<String> result = new ArrayList<>();

        if (content.length() <= chunkSize) {
            result.add(content);
            return result;
        }

        if (separatorIndex >= separators.length) {
            for (int i = 0; i < content.length(); i += chunkSize)
                result.add(content.substring(i, Math.min(i + chunkSize, content.length())));
            return result;
        }

        String separator = separators[separatorIndex];
        if (!content.contains(separator))
            return splitRecursive(content, chunkSize, separators, separatorIndex + 1);

        String[] rawParts = content.split(Pattern.quote(separator), -1);
        for (int i = 0; i < rawParts.length; i++) {
            String part = (i < rawParts.length - 1) ? rawParts[i] + separator : rawParts[i]; //String.split() separator ko delete kar deta hai by default — agar wapas na jode, toh sentence ka full-stop ya paragraph ka \n\n hamesha ke liye gayab ho jaata. Yahi data-loss bug hia jo test se pakda tha.
            if (part.isEmpty())
                continue;
            if (part.length() <= chunkSize)
                result.add(part);
            else
                result.addAll(splitRecursive(part, chunkSize, separators, separatorIndex + 1));
        }
        return result;
        /*
            Poora example — text: "Hi there. This is a long sentence example.", chunkSize = 20, separators {"\n\n","\n",". "," "}.
            text.length()=44 > 20 → base case skip
            separatorIndex=0 (\n\n) → text mein \n\n hai hi nahi → next separator, separatorIndex=1
            separatorIndex=1 (\n) → text mein \n bhi nahi → next, separatorIndex=2
            separatorIndex=2 (. ) → ye mil gaya! Split: ["Hi there", "This is a long sentence example."]. Pehla piece separator jod ke banta hai "Hi there. " (len=10, chunkSize se chhota → seedha result mein). Doosra piece last hai (koi separator nahi jodta) "This is a long sentence example." (len=33, abhi bhi bada) → is pe recursion: splitRecursive("This is a long sentence example.", 20, separators, 3) call hota hai.
            Us recursive call mein: separatorIndex=3 (" " space) → split hoga words mein, har word chunkSize ke andar fit ho jaayega, result mein add ho jaayenge.
            Final result: ["Hi there. ", "This is a ", "long sentence ", "example."] jaisa kuch (exact word-grouping depends on space-splitting details) — matlab pehle sentence-boundary try hua, tabhi word-boundary pe gaya jab sentence khud bhi bada tha.
        */
    }

    private List<String> mergeSegments(List<String> segments, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segments) {
            boolean needsSpace = current.length() > 0 && !Character.isWhitespace(current.charAt(current.length() - 1));
            int prospectiveLength = current.length() + (needsSpace ? 1 : 0) + segment.length();

            if (current.length() > 0 && prospectiveLength > chunkSize) {
                chunks.add(current.toString());

                String flushed = current.toString();
                String overlapTail = flushed.length() > overlap
                        ? flushed.substring(flushed.length() - overlap)
                        : flushed;

                int maxTail = Math.max(0, chunkSize - segment.length() - 1);
                if (overlapTail.length() > maxTail) {
                    overlapTail = maxTail > 0 ? overlapTail.substring(overlapTail.length() - maxTail) : "";
                }
                current = new StringBuilder(overlapTail);
            }

            if (current.length() > 0 && !Character.isWhitespace(current.charAt(current.length() - 1))) {
                current.append(' ');
            }
            current.append(segment);
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
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
