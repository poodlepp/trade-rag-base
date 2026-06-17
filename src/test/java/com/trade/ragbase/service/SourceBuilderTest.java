package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.trade.ragbase.dto.RagResponse;
import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.KbDocumentRepository;

class SourceBuilderTest {

    @Test
    void buildSourcesUsesCitedReferencesAndDocumentNames() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        SourceBuilder sourceBuilder = new SourceBuilder(new CitationParser(), documentRepository);
        KbDocument document = new KbDocument();
        document.setId(101L);
        document.setFileName("handbook.txt");
        when(documentRepository.findAllById(java.util.Set.of(101L, 102L))).thenReturn(List.of(document));

        List<RagResponse.Source> sources = sourceBuilder.buildSources(
                "年假为10天（来源：[参考1]）",
                List.of(scored(1L, 101L, "年假为10天"), scored(2L, 102L, "请假流程")));

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getChunkId()).isEqualTo(1L);
        assertThat(sources.get(0).getDocName()).isEqualTo("handbook.txt");
    }

    private HybridRetrieverService.ScoredChunk scored(Long chunkId, Long docId, String content) {
        DocChunk chunk = new DocChunk();
        chunk.setId(chunkId);
        chunk.setDocId(docId);
        chunk.setKbId(1L);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setDocVersion(1);
        return new HybridRetrieverService.ScoredChunk(chunk, 0.8);
    }
}
