package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.trade.ragbase.entity.DocChunk;

class RagQueryServiceV3Test {

    @Test
    void queryUsesEnhancedRetrieverAndGeneratesAnswer() {
        EnhancedRetrieverService enhancedRetriever = mock(EnhancedRetrieverService.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        RagQueryServiceV3 service = new RagQueryServiceV3(enhancedRetriever, chatClient, 5);
        when(enhancedRetriever.retrieveWithHyde("年假有几天", List.of(1L), 5))
                .thenReturn(List.of(new HybridRetrieverService.ScoredChunk(
                        chunk(11L, "员工年假为每年 10 天。", "休假制度"), 0.2)));
        when(chatClient.prompt().system(anyString()).user("年假有几天").call().content())
                .thenReturn("员工年假为每年 10 天。");

        String answer = service.query("年假有几天", List.of(1L));

        assertThat(answer).isEqualTo("员工年假为每年 10 天。");
    }

    @Test
    void queryReturnsNotFoundWhenEnhancedRetrieverReturnsEmpty() {
        EnhancedRetrieverService enhancedRetriever = mock(EnhancedRetrieverService.class);
        RagQueryServiceV3 service = new RagQueryServiceV3(
                enhancedRetriever, mock(ChatClient.class, RETURNS_DEEP_STUBS), 5);
        when(enhancedRetriever.retrieveWithHyde("未知问题", List.of(1L), 5)).thenReturn(List.of());

        String answer = service.query("未知问题", List.of(1L));

        assertThat(answer).contains("未找到与该问题相关的内容");
    }

    private DocChunk chunk(Long id, String content, String sectionTitle) {
        DocChunk chunk = new DocChunk();
        chunk.setId(id);
        chunk.setDocId(100L + id);
        chunk.setKbId(1L);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setSectionTitle(sectionTitle);
        chunk.setDocVersion(1);
        return chunk;
    }
}
