package com.rageval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rageval.config.LlmConfig;
import com.rageval.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GeneratorService {

    // Cost/latency safety cap: don't send more than this many claims in one
    // verification call, no matter how long the generated answer is.
    private static final int MAX_CLAIMS_TO_VERIFY = 20;

    private final OllamaConfig ollamaConfig;
    private final LlmConfig llmConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeneratorService(OllamaConfig ollamaConfig, LlmConfig llmConfig) {
        this.ollamaConfig = ollamaConfig;
        this.llmConfig = llmConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /** Raw text + latency + token usage from a single LLM call, whichever provider served it. */
    public record LlmCallResult(String text, Integer promptTokens, Integer completionTokens, long latencyMs) {}

    /**
     * hallucinationRate is null (not 0.00) when the verification response couldn't be parsed
     * at all — "unavailable" rather than a fabricated number, same convention already used for
     * the ground-truth-dependent retrieval metrics (see EvaluationService.hasGroundTruth).
     */
    public record HallucinationCheckResult(BigDecimal hallucinationRate, int totalClaims, int checkedClaims,
                                            int unsupportedClaims, Long latencyMs,
                                            Integer promptTokens, Integer completionTokens) {}

    /**
     * Generate an answer for the given query+context — via local Ollama, or via the
     * Groq chat completions API when llm.provider=cloud (see LlmConfig).
     */
    public String generateAnswer(String query, String context) {
        return generateAnswerWithMeta(query, context).text();
    }

    /** Same as generateAnswer, but also returns latency and token usage for cost/latency tracking. */
    public LlmCallResult generateAnswerWithMeta(String query, String context) {
        return generateRaw(buildPrompt(query, context));
    }

    /**
     * Real (not proxy) hallucination check: splits the generated answer into individual
     * sentence-level claims and asks the LLM, in a single batched call, which of them are
     * actually supported by the retrieved context. This is a genuinely different signal from
     * Faithfulness (which is just cosine similarity between whole-answer and whole-context
     * embeddings, see the corrected Design Decisions entry #4) — a claim can be topically
     * similar to the context (high cosine) while still being unsupported or contradicted by it,
     * which only a claim-by-claim check like this one can catch.
     *
     * Deliberately one LLM call for ALL claims (not one call per claim) to keep the added cost
     * and latency bounded — important since this runs once per evaluated query, on top of the
     * generation call, and cloud-mode calls have a real per-token cost.
     */
    public HallucinationCheckResult verifyClaims(String answer, String context) {
        List<String> claims = splitIntoClaims(answer);
        if (claims.isEmpty()) {
            return new HallucinationCheckResult(BigDecimal.ZERO, 0, 0, 0, null, null, null);
        }

        LlmCallResult callResult = generateRaw(buildVerificationPrompt(claims, context));
        return parseVerificationResponse(callResult, claims);
    }

    // ---------- shared low-level call, used by both answer generation and claim verification ----------

    private LlmCallResult generateRaw(String prompt) {
        return llmConfig.isCloud() ? callGroq(prompt) : callOllama(prompt);
    }

    private LlmCallResult callOllama(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String url = ollamaConfig.getBaseUrl() + "/api/generate";

            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaConfig.getGenerationModel());
            body.put("prompt", prompt);
            body.put("stream", false);
            // 0 = greedy decoding, for reproducible eval runs. Previously unset, which meant
            // Ollama used the model's own default temperature (commonly ~0.8) — real cause of
            // the same test query producing different Answer Relevance/Faithfulness/RAG Score
            // on repeat runs, since those metrics are computed from the generated answer's text.
            body.put("options", Map.of("temperature", 0));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(url, request, String.class);
            long latencyMs = System.currentTimeMillis() - start;
            JsonNode jsonNode = objectMapper.readTree(response);

            String text = jsonNode.path("response").asText("");
            Integer promptTokens = jsonNode.has("prompt_eval_count") ? jsonNode.get("prompt_eval_count").asInt() : null;
            Integer completionTokens = jsonNode.has("eval_count") ? jsonNode.get("eval_count").asInt() : null;

            log.info("LLM call via Ollama ({}ms, promptTokens={}, completionTokens={})", latencyMs, promptTokens, completionTokens);
            return new LlmCallResult(text, promptTokens, completionTokens, latencyMs);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.error("Error calling Ollama", e);
            return new LlmCallResult("Error generating answer", null, null, latencyMs);
        }
    }

    private LlmCallResult callGroq(String prompt) {
        long start = System.currentTimeMillis();
        try {
            LlmConfig.Groq groq = llmConfig.getGroq();
            if (groq.getApiKey() == null || groq.getApiKey().isBlank()) {
                log.error("llm.provider=cloud but llm.groq.api-key is not set");
                return new LlmCallResult("Error generating answer: GROQ_API_KEY is not configured",
                        null, null, System.currentTimeMillis() - start);
            }

            String url = groq.getBaseUrl() + "/chat/completions";

            Map<String, Object> body = new HashMap<>();
            body.put("model", groq.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("temperature", 0); // 0 = greedy decoding, for reproducible eval runs (was 0.3)
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groq.getApiKey());
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(url, request, String.class);
            long latencyMs = System.currentTimeMillis() - start;
            JsonNode jsonNode = objectMapper.readTree(response);

            JsonNode contentNode = jsonNode.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.error("Failed to extract answer from Groq response: {}", response);
                return new LlmCallResult("Error generating answer", null, null, latencyMs);
            }

            JsonNode usageNode = jsonNode.path("usage");
            Integer promptTokens = usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : null;
            Integer completionTokens = usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : null;

            log.info("LLM call via Groq ({}ms, promptTokens={}, completionTokens={})", latencyMs, promptTokens, completionTokens);
            return new LlmCallResult(contentNode.asText(), promptTokens, completionTokens, latencyMs);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.error("Error calling Groq", e);
            return new LlmCallResult("Error generating answer", null, null, latencyMs);
        }
    }

    // ---------- claim decomposition + verification-response parsing ----------

    /**
     * Naive sentence-level claim decomposition (split on '.', '!', '?' followed by whitespace) —
     * not full LLM-based atomic-claim extraction like RAGAS does internally. Deliberate trade-off:
     * a proper claim extractor is itself an LLM call, which would double the cost/latency this
     * metric already adds on top of generation. A sentence is a reasonable proxy for "one claim"
     * for most short RAG answers; it will under- or over-split compound sentences. Documented
     * here, and in Design Decisions, as a known limitation rather than glossed over.
     */
    private List<String> splitIntoClaims(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        String[] rough = answer.trim().split("(?<=[.!?])\\s+");
        List<String> claims = new ArrayList<>();
        for (String s : rough) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                claims.add(trimmed);
            }
        }
        if (claims.size() > MAX_CLAIMS_TO_VERIFY) {
            return claims.subList(0, MAX_CLAIMS_TO_VERIFY);
        }
        return claims;
    }

    private String buildVerificationPrompt(List<String> claims, String context) {
        StringBuilder claimsBlock = new StringBuilder();
        for (int i = 0; i < claims.size(); i++) {
            claimsBlock.append(i + 1).append(". ").append(claims.get(i)).append("\n");
        }
        return String.format("""
                You are a strict fact-checker. Below is a CONTEXT and a numbered list of CLAIMS
                taken from an answer that was generated from that context.

                For EACH claim, decide whether it is directly supported by the CONTEXT.

                Respond with EXACTLY one line per claim, and nothing else, in this format:
                <number>: SUPPORTED
                or
                <number>: UNSUPPORTED

                CONTEXT:
                %s

                CLAIMS:
                %s
                """, context, claimsBlock);
    }

    private static final Pattern VERDICT_LINE = Pattern.compile("(?i)(\\d+)\\s*:\\s*(SUPPORTED|UNSUPPORTED)");

    private HallucinationCheckResult parseVerificationResponse(LlmCallResult callResult, List<String> claims) {
        Matcher matcher = VERDICT_LINE.matcher(callResult.text() == null ? "" : callResult.text());
        Map<Integer, Boolean> verdicts = new HashMap<>(); // value = true means UNSUPPORTED
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            boolean unsupported = matcher.group(2).equalsIgnoreCase("UNSUPPORTED");
            verdicts.put(index, unsupported); // last write wins — a duplicate line for the same
            // claim number overwrites rather than double-counts, so a chatty/repeating model
            // response can't inflate the denominator.
        }

        if (verdicts.isEmpty()) {
            log.warn("Could not parse any claim verdicts from hallucination-check response: {}", callResult.text());
            return new HallucinationCheckResult(null, claims.size(), 0, 0,
                    callResult.latencyMs(), callResult.promptTokens(), callResult.completionTokens());
        }

        long unsupportedCount = verdicts.values().stream().filter(Boolean::booleanValue).count();
        int checkedCount = verdicts.size();
        if (checkedCount < claims.size()) {
            log.warn("Only got verdicts for {}/{} claims in hallucination check", checkedCount, claims.size());
        }

        BigDecimal rate = BigDecimal.valueOf((double) unsupportedCount / checkedCount).setScale(2, RoundingMode.HALF_UP);
        return new HallucinationCheckResult(rate, claims.size(), checkedCount, (int) unsupportedCount,
                callResult.latencyMs(), callResult.promptTokens(), callResult.completionTokens());
    }

    private String buildPrompt(String query, String context) {
        // Strict grounding instruction — this is the actual fix for the Hallucination
        // Rate metric being non-trivial (was 0.31). Previously this prompt only said
        // "answer based on the context", which doesn't forbid the model from adding
        // outside knowledge; the claim-verification prompt (buildVerificationPrompt)
        // was already strict, but that only measures hallucination after the fact —
        // it doesn't prevent it. Telling the model up front to answer ONLY from the
        // context, and to say so when the context is insufficient, is what actually
        // reduces the rate instead of just detecting it.
        return String.format("""
                Answer the question using ONLY the information in the CONTEXT below.
                Do not use any outside knowledge, and do not add facts, numbers, names,
                or details that are not explicitly stated in the CONTEXT.
                If the CONTEXT does not contain enough information to answer fully,
                say so explicitly instead of filling the gap with your own knowledge.
                
                Query: %s
                
                Context:
                %s
                
                Answer:""", query, context);
    }
}
