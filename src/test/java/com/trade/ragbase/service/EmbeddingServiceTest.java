package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class EmbeddingServiceTest {

    @Test
    void embedBatchUsesCacheForHitsAndPreservesInputOrder() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.get(EmbeddingService.buildCacheKey("cached"))).thenReturn("9.0,9.1");

        EmbeddingService service = new EmbeddingService(
                embeddingModel,
                redisTemplate,
                new TokenMetrics(),
                Duration.ofDays(7),
                10);

        List<float[]> vectors = service.embedBatch(List.of("first", "cached", "second"));

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)).containsExactly(1.0f, 1.5f);
        assertThat(vectors.get(1)).containsExactly(9.0f, 9.1f);
        assertThat(vectors.get(2)).containsExactly(2.0f, 2.5f);
        assertThat(embeddingModel.requests).containsExactly(List.of("first", "second"));
        verify(valueOperations, times(2)).set(anyString(), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void embedBatchReturnsCachedVectorsWithoutCallingModel() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("1.0,2.0,3.0");

        EmbeddingService service = new EmbeddingService(
                embeddingModel,
                redisTemplate,
                new TokenMetrics(),
                Duration.ofMinutes(10),
                10);

        List<float[]> vectors = service.embedBatch(List.of("a", "b"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(vectors.get(1)).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(embeddingModel.callCount.get()).isZero();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void embedFromApiBatchesRequestsAndRecordsTokenUsage() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        TokenMetrics tokenMetrics = new TokenMetrics();
        EmbeddingService service = new EmbeddingService(
                embeddingModel,
                mock(StringRedisTemplate.class),
                tokenMetrics,
                Duration.ofDays(7),
                2);

        List<float[]> vectors = service.embedFromApi(List.of("a", "b", "c"));

        assertThat(vectors).hasSize(3);
        assertThat(embeddingModel.requests).containsExactly(List.of("a", "b"), List.of("c"));
        assertThat(tokenMetrics.getEmbeddingTokens()).isEqualTo(30);
    }

    @Test
    void embedReturnsSingleVector() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        EmbeddingService service = new EmbeddingService(
                embeddingModel,
                redisTemplate,
                new TokenMetrics(),
                Duration.ofDays(7),
                10);

        float[] vector = service.embed("query");

        assertThat(vector).containsExactly(1.0f, 1.5f);
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {

        private final AtomicInteger callCount = new AtomicInteger();
        private final List<List<String>> requests = new ArrayList<>();

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            callCount.incrementAndGet();
            List<String> texts = request.getInstructions();
            requests.add(List.copyOf(texts));
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                int sequence = requests.stream().mapToInt(List::size).sum() - texts.size() + i + 1;
                embeddings.add(new Embedding(new float[]{sequence, sequence + 0.5f}, i));
            }
            return new EmbeddingResponse(
                    embeddings,
                    new EmbeddingResponseMetadata("test-embedding-model", new FixedUsage(texts.size() * 10)));
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{0.0f};
        }
    }

    private record FixedUsage(Integer totalTokens) implements Usage {

        @Override
        public Integer getPromptTokens() {
            return totalTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return 0;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
