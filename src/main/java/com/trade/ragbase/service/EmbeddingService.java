package com.trade.ragbase.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmbeddingService {

    private static final String CACHE_PREFIX = "emb:v1:";
    private static final int DEFAULT_BATCH_SIZE = 10;

    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final TokenMetrics tokenMetrics;
    private final Duration embeddingTtl;
    private final int batchSize;

    @Autowired
    public EmbeddingService(
            EmbeddingModel embeddingModel,
            StringRedisTemplate redisTemplate,
            TokenMetrics tokenMetrics,
            @Value("${rag.cache.embedding-ttl:7d}") Duration embeddingTtl) {
        this(embeddingModel, redisTemplate, tokenMetrics, embeddingTtl, DEFAULT_BATCH_SIZE);
    }

    EmbeddingService(
            EmbeddingModel embeddingModel,
            StringRedisTemplate redisTemplate,
            TokenMetrics tokenMetrics,
            Duration embeddingTtl,
            int batchSize) {
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.tokenMetrics = tokenMetrics;
        this.embeddingTtl = embeddingTtl;
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * 批量向量化，带 Redis 缓存。缓存命中直接返回，未命中的文本批量调用模型并写回缓存。
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<Integer, float[]> vectorsByIndex = new HashMap<>();
        List<Integer> missedIndices = new ArrayList<>();
        List<String> missedTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cacheKey = buildCacheKey(text);
            String cachedVector = redisTemplate.opsForValue().get(cacheKey);
            if (cachedVector == null) {
                missedIndices.add(i);
                missedTexts.add(text);
            } else {
                vectorsByIndex.put(i, deserializeVector(cachedVector));
            }
        }

        log.debug("[Embedding] 总数={}，缓存命中={}，需要调用模型={}",
                texts.size(), vectorsByIndex.size(), missedTexts.size());

        if (!missedTexts.isEmpty()) {
            List<float[]> newVectors = embedFromApi(missedTexts);
            for (int i = 0; i < missedIndices.size(); i++) {
                int originalIndex = missedIndices.get(i);
                float[] vector = newVectors.get(i);
                vectorsByIndex.put(originalIndex, vector);
                redisTemplate.opsForValue().set(
                        buildCacheKey(texts.get(originalIndex)),
                        serializeVector(vector),
                        embeddingTtl);
            }
        }

        return IntStream.range(0, texts.size())
                .mapToObj(vectorsByIndex::get)
                .toList();
    }

    /**
     * 调用 Embedding 模型。按批次提交，避免单次请求过大。
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<float[]> embedFromApi(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>(texts.size());
        int totalTokens = 0;

        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batch = texts.subList(start, end);

            long batchStart = System.currentTimeMillis();
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
            long elapsed = System.currentTimeMillis() - batchStart;

            totalTokens += extractTotalTokens(response);
            response.getResults().stream()
                    .sorted(Comparator.comparingInt(embedding -> embedding.getIndex()))
                    .forEach(embedding -> result.add(embedding.getOutput()));

            log.debug("[Embedding] 批次{}/{}，size={}，耗时={}ms",
                    start / batchSize + 1,
                    (texts.size() + batchSize - 1) / batchSize,
                    batch.size(),
                    elapsed);
        }

        tokenMetrics.recordEmbeddingTokens(totalTokens);
        log.info("[Embedding] API 调用完成，共{}条，消耗 Token={}", texts.size(), totalTokens);
        return result;
    }

    @Recover
    public List<float[]> embedFromApiFallback(Exception exception, List<String> texts) {
        int textCount = texts == null ? 0 : texts.size();
        log.error("[Embedding] 重试 3 次后仍失败，texts.size={}，error={}", textCount, exception.getMessage());
        throw new IllegalStateException("Embedding API 调用失败，已重试 3 次：" + exception.getMessage(), exception);
    }

    public float[] embed(String text) {
        List<float[]> vectors = embedBatch(List.of(text));
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    public static String buildCacheKey(String text) {
        return CACHE_PREFIX + toMd5(text == null ? "" : text);
    }

    private static int extractTotalTokens(EmbeddingResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return usage.getTotalTokens();
    }

    private static String serializeVector(float[] vector) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.toString();
    }

    private static float[] deserializeVector(String value) {
        String normalizedValue = value.replace("[", "").replace("]", "").replace(" ", "");
        String[] parts = normalizedValue.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    private static String toMd5(String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(messageDigest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
