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

class RagQueryServiceV2Test {

    @Test
    void queryUsesHybridRetrieverAndGeneratesAnswer() {
        HybridRetrieverService hybridRetriever = mock(HybridRetrieverService.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        RagQueryServiceV2 service = new RagQueryServiceV2(hybridRetriever, chatClient, 3);
        when(hybridRetriever.retrieve("API 限流策略是什么", List.of(1L), 3))
                .thenReturn(List.of(new HybridRetrieverService.ScoredChunk(
                        chunk(11L, "API 每分钟最多调用 60 次。", "接口规范"), 0.2)));
        when(chatClient.prompt().system(anyString()).user("API 限流策略是什么").call().content())
                .thenReturn("API 每分钟最多调用 60 次。");

        String answer = service.query("API 限流策略是什么", List.of(1L));

        assertThat(answer).isEqualTo("API 每分钟最多调用 60 次。");
    }

    @Test
    void queryReturnsNotFoundMessageWhenHybridRetrieverReturnsEmpty() {
        HybridRetrieverService hybridRetriever = mock(HybridRetrieverService.class);
        RagQueryServiceV2 service = new RagQueryServiceV2(
                hybridRetriever, mock(ChatClient.class, RETURNS_DEEP_STUBS), 3);
        when(hybridRetriever.retrieve("未知问题", List.of(1L), 3)).thenReturn(List.of());

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
