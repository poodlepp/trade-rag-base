package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.entity.KnowledgeBase;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.DocChunkRepository;
import com.trade.ragbase.repository.KbPermissionRepository;
import com.trade.ragbase.repository.KnowledgeBaseRepository;
import com.trade.ragbase.security.UserContext;

class HybridRetrieverServiceTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void retrieveMergesVectorAndFulltextResultsWithRrf() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        TsQueryBuilder tsQueryBuilder = mock(TsQueryBuilder.class);
        HybridRetrieverService service = new HybridRetrieverService(
                embeddingService,
                chunkRepository,
                tsQueryBuilder,
                mock(KnowledgeBaseRepository.class),
                mock(KbPermissionRepository.class),
                3,
                3);
        DocChunk vectorFirst = chunk(1L, "向量第一");
        DocChunk both = chunk(2L, "两路命中");
        DocChunk fulltextFirst = chunk(3L, "全文第一");
        when(embeddingService.embed("API 限流")).thenReturn(new float[] {0.1f, 0.2f});
        when(tsQueryBuilder.build("API 限流")).thenReturn("API & 限流");
        when(chunkRepository.findByVectorSimilarity(7L, "[0.1,0.2]", 3))
                .thenReturn(List.of(vectorFirst, both));
        when(chunkRepository.findByFullTextSearch(7L, "API & 限流", 3))
                .thenReturn(List.of(fulltextFirst, both));

        List<HybridRetrieverService.ScoredChunk> result = service.retrieve("API 限流", List.of(7L), 2);

        assertThat(result).extracting(HybridRetrieverService.ScoredChunk::id)
                .containsExactly(2L, 1L);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    @Test
    void retrieveWithPermissionCheckRejectsWhenNoKnowledgeBaseAllowed() {
        KnowledgeBaseRepository kbRepository = mock(KnowledgeBaseRepository.class);
        KbPermissionRepository permissionRepository = mock(KbPermissionRepository.class);
        HybridRetrieverService service = new HybridRetrieverService(
                mock(EmbeddingService.class),
                mock(DocChunkRepository.class),
                mock(TsQueryBuilder.class),
                kbRepository,
                permissionRepository,
                3,
                3);
        UserContext.set(8L, "HR", "USER");
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setIsPublic(false);
        when(kbRepository.findById(7L)).thenReturn(java.util.Optional.of(knowledgeBase));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.retrieveWithPermissionCheck("API 限流", List.of(7L), 2))
                .isInstanceOf(BizException.class)
                .hasMessage("您对所请求的知识库没有访问权限");
    }

    private DocChunk chunk(Long id, String content) {
        DocChunk chunk = new DocChunk();
        chunk.setId(id);
        chunk.setDocId(100L + id);
        chunk.setKbId(7L);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setDocVersion(1);
        return chunk;
    }
}
