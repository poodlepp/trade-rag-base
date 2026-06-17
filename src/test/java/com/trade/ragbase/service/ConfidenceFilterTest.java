package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.trade.ragbase.entity.DocChunk;

class ConfidenceFilterTest {

    @Test
    void filterKeepsScoresAboveThreshold() {
        ConfidenceFilter filter = new ConfidenceFilter(0.3);

        List<HybridRetrieverService.ScoredChunk> result = filter.filter(List.of(
                scored(1L, 0.2),
                scored(2L, 0.5)));

        assertThat(result).extracting(HybridRetrieverService.ScoredChunk::id)
                .containsExactly(2L);
    }

    @Test
    void filterKeepsBestWhenAllBelowThreshold() {
        ConfidenceFilter filter = new ConfidenceFilter(0.8);

        List<HybridRetrieverService.ScoredChunk> result = filter.filter(List.of(
                scored(1L, 0.2),
                scored(2L, 0.5)));

        assertThat(result).extracting(HybridRetrieverService.ScoredChunk::id)
                .containsExactly(2L);
    }

    private HybridRetrieverService.ScoredChunk scored(Long id, double score) {
        DocChunk chunk = new DocChunk();
        chunk.setId(id);
        chunk.setContent("content " + id);
        chunk.setDocVersion(1);
        return new HybridRetrieverService.ScoredChunk(chunk, score);
    }
}
