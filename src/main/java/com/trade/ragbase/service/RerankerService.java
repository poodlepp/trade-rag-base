package com.trade.ragbase.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class RerankerService {

    private final WebClient.Builder webClientBuilder;
    private final TokenMetrics tokenMetrics;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;

    public RerankerService(
            WebClient.Builder webClientBuilder,
            TokenMetrics tokenMetrics,
            @Value("${reranker.endpoint:}") String endpoint,
            @Value("${reranker.api-key:}") String apiKey,
            @Value("${reranker.model:gte-rerank-v2}") String model,
            @Value("${reranker.timeout-ms:800}") long timeoutMs) {
        this.webClientBuilder = webClientBuilder;
        this.tokenMetrics = tokenMetrics;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    public List<HybridRetrieverService.ScoredChunk> rerank(
            String question,
            List<HybridRetrieverService.ScoredChunk> candidates,
            int topN) {
        if (candidates.isEmpty() || candidates.size() <= topN || apiKey == null || apiKey.isBlank()) {
            return candidates.stream().limit(topN).collect(Collectors.toList());
        }

        try {
            List<HybridRetrieverService.ScoredChunk> reranked = callRerankApi(question, candidates, topN);
            log.info("[Reranker] 精排完成：候选={}，返回={}", candidates.size(), reranked.size());
            return reranked;
        } catch (Exception exception) {
            log.warn("[Reranker] 精排失败或超时，降级使用 RRF 分数：{}", exception.getMessage());
            return candidates.stream().limit(topN).collect(Collectors.toList());
        }
    }

    @SuppressWarnings("unchecked")
    private List<HybridRetrieverService.ScoredChunk> callRerankApi(
            String question,
            List<HybridRetrieverService.ScoredChunk> candidates,
            int topN) {
        List<String> documents = candidates.stream()
                .map(HybridRetrieverService.ScoredChunk::content)
                .collect(Collectors.toList());
        Map<String, Object> request = Map.of(
                "model", model,
                "input", Map.of("query", question, "documents", documents),
                "parameters", Map.of("top_n", topN, "return_documents", false));

        Map<String, Object> response = webClientBuilder
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        if (response == null || !(response.get("output") instanceof Map<?, ?> output)
                || !(output.get("results") instanceof List<?> results)) {
            throw new IllegalStateException("Reranker API 返回空结果");
        }

        if (response.get("usage") instanceof Map<?, ?> usage
                && usage.get("total_tokens") instanceof Number totalTokens) {
            tokenMetrics.recordContextTokens(totalTokens.intValue());
        }

        return results.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(result -> {
                    int index = ((Number) result.get("index")).intValue();
                    double score = ((Number) result.get("relevance_score")).doubleValue();
                    HybridRetrieverService.ScoredChunk original = candidates.get(index);
                    return new HybridRetrieverService.ScoredChunk(original.chunk(), score);
                })
                .sorted(Comparator.comparingDouble(HybridRetrieverService.ScoredChunk::score).reversed())
                .collect(Collectors.toList());
    }
}
