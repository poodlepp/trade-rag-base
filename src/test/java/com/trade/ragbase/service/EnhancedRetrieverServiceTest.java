package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.repository.DocChunkRepository;

class EnhancedRetrieverServiceTest {

    @Test
    void retrieveWithHydeMergesOriginalAndHypotheticalVectorResults() {
        HybridRetrieverService hybridRetriever = mock(HybridRetrieverService.class);
        QueryRewriterService queryRewriter = mock(QueryRewriterService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        EnhancedRetrieverService service = new EnhancedRetrieverService(
                hybridRetriever, queryRewriter, embeddingService, chunkRepository, 3);
        DocChunk originalOnly = chunk(1L, "原始命中");
        DocChunk both = chunk(2L, "两路命中");
        DocChunk hydeOnly = chunk(3L, "HyDE 命中");
        when(hybridRetriever.retrieve("年假有几天", List.of(7L), 3))
                .thenReturn(List.of(
                        new HybridRetrieverService.ScoredChunk(originalOnly, 0.1),
                        new HybridRetrieverService.ScoredChunk(both, 0.1)));
        when(queryRewriter.generateHypotheticalAnswer("年假有几天"))
                .thenReturn("员工年假一般为每年十天。");
        when(embeddingService.embed("员工年假一般为每年十天。")).thenReturn(new float[] {0.4f, 0.5f});
        when(chunkRepository.findByVectorSimilarity(7L, "[0.4,0.5]", 3))
                .thenReturn(List.of(hydeOnly, both));

        List<HybridRetrieverService.ScoredChunk> result =
                service.retrieveWithHyde("年假有几天", List.of(7L), 2);

        assertThat(result).extracting(HybridRetrieverService.ScoredChunk::id)
                .containsExactly(2L, 1L);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
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
