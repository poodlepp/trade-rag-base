package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.repository.DocChunkRepository;

class RagQueryServiceTest {

    @Test
    void queryRetrievesChunksFromKnowledgeBasesAndGeneratesAnswer() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        RagQueryService service = new RagQueryService(embeddingService, chunkRepository, chatClient, 2);
        when(embeddingService.embed("年假有几天")).thenReturn(new float[] {0.1f, 0.2f});
        when(chunkRepository.findByVectorSimilarity(1L, "[0.1,0.2]", 2))
                .thenReturn(List.of(chunk(11L, "员工年假为每年 10 天。", "休假制度")));
        when(chunkRepository.findByVectorSimilarity(2L, "[0.1,0.2]", 2))
                .thenReturn(List.of(chunk(22L, "请假需要提前提交申请。", null)));
        when(chatClient.prompt().system(anyString()).user("年假有几天").call().content())
                .thenReturn("员工年假为每年 10 天。");

        String answer = service.query("年假有几天", List.of(1L, 2L));

        assertThat(answer).isEqualTo("员工年假为每年 10 天。");
        verify(chunkRepository).findByVectorSimilarity(1L, "[0.1,0.2]", 2);
        verify(chunkRepository).findByVectorSimilarity(2L, "[0.1,0.2]", 2);
    }

    @Test
    void queryReturnsNotFoundMessageWhenNoChunksRetrieved() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        RagQueryService service = new RagQueryService(
                embeddingService, chunkRepository, mock(ChatClient.class, RETURNS_DEEP_STUBS), 5);
        when(embeddingService.embed("不存在的问题")).thenReturn(new float[] {0.3f});
        when(chunkRepository.findByVectorSimilarity(1L, "[0.3]", 5)).thenReturn(List.of());

        String answer = service.query("不存在的问题", List.of(1L));

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
