package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.trade.ragbase.entity.DocChunk;

class ContextTrimmerServiceTest {

    @Test
    void trimKeepsChunksWithinTokenBudgetAndRecordsTokens() {
        TokenMetrics tokenMetrics = new TokenMetrics();
        ContextTrimmerService trimmer = new ContextTrimmerService(tokenMetrics, 6);

        List<HybridRetrieverService.ScoredChunk> result = trimmer.trim(List.of(
                scored(1L, "one two three"),
                scored(2L, "four five six seven")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(tokenMetrics.getContextTokens()).isGreaterThan(0);
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
