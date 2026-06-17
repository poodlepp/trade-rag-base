package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.trade.ragbase.dto.RagResponse;
import com.trade.ragbase.entity.DocChunk;

class FullRagPipelineTest {

    @Test
    void queryRunsFullPipelineAndReturnsAnswerWithSources() {
        EnhancedRetrieverService enhancedRetriever = mock(EnhancedRetrieverService.class);
        RerankerService rerankerService = mock(RerankerService.class);
        ConfidenceFilter confidenceFilter = mock(ConfidenceFilter.class);
        ContextTrimmerService contextTrimmer = mock(ContextTrimmerService.class);
        SourceBuilder sourceBuilder = mock(SourceBuilder.class);
        HallucinationChecker hallucinationChecker = mock(HallucinationChecker.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        FullRagPipeline pipeline = new FullRagPipeline(
                enhancedRetriever, rerankerService, confidenceFilter, contextTrimmer,
                sourceBuilder, hallucinationChecker, chatClient, 5, false);
        HybridRetrieverService.ScoredChunk scoredChunk = scored(1L, "员工年假为每年 10 天。");
        when(enhancedRetriever.retrieveWithHyde("年假有几天", List.of(1L), 20))
                .thenReturn(List.of(scoredChunk));
        when(rerankerService.rerank("年假有几天", List.of(scoredChunk), 5))
                .thenReturn(List.of(scoredChunk));
        when(confidenceFilter.filter(List.of(scoredChunk))).thenReturn(List.of(scoredChunk));
        when(contextTrimmer.trim(List.of(scoredChunk))).thenReturn(List.of(scoredChunk));
        when(chatClient.prompt().system(anyString()).user("年假有几天").call().content())
                .thenReturn("员工年假为每年 10 天。（来源：[参考1]）");
        RagResponse.Source source = RagResponse.Source.builder()
                .chunkId(1L)
                .docId(101L)
                .docName("handbook.txt")
                .excerpt("员工年假为每年 10 天。")
                .score(0.9)
                .build();
        when(sourceBuilder.buildSources("员工年假为每年 10 天。（来源：[参考1]）", List.of(scoredChunk)))
                .thenReturn(List.of(source));

        RagResponse response = pipeline.query("年假有几天", List.of(1L));

        assertThat(response.isNotFound()).isFalse();
        assertThat(response.getAnswer()).contains("员工年假");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void queryReturnsNotFoundWhenNoCandidates() {
        EnhancedRetrieverService enhancedRetriever = mock(EnhancedRetrieverService.class);
        FullRagPipeline pipeline = new FullRagPipeline(
                enhancedRetriever,
                mock(RerankerService.class),
                mock(ConfidenceFilter.class),
                mock(ContextTrimmerService.class),
                mock(SourceBuilder.class),
                mock(HallucinationChecker.class),
                mock(ChatClient.class, RETURNS_DEEP_STUBS),
                5,
                false);
        when(enhancedRetriever.retrieveWithHyde("未知问题", List.of(1L), 20)).thenReturn(List.of());

        RagResponse response = pipeline.query("未知问题", List.of(1L));

        assertThat(response.isNotFound()).isTrue();
        assertThat(response.getSources()).isEmpty();
    }

    private HybridRetrieverService.ScoredChunk scored(Long id, String content) {
        DocChunk chunk = new DocChunk();
        chunk.setId(id);
        chunk.setDocId(100L + id);
        chunk.setKbId(1L);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setDocVersion(1);
        return new HybridRetrieverService.ScoredChunk(chunk, 0.9);
    }
}
