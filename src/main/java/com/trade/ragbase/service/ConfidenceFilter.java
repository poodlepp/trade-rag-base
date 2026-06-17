package com.trade.ragbase.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConfidenceFilter {

    private final double minScore;

    @Autowired
    public ConfidenceFilter(@Value("${rag.retrieval.min-score:0.3}") double minScore) {
        this.minScore = minScore;
    }

    public List<HybridRetrieverService.ScoredChunk> filter(
            List<HybridRetrieverService.ScoredChunk> chunks) {
        List<HybridRetrieverService.ScoredChunk> filtered = chunks.stream()
                .filter(chunk -> chunk.score() >= minScore)
                .collect(Collectors.toList());

        if (filtered.isEmpty() && !chunks.isEmpty()) {
            HybridRetrieverService.ScoredChunk best = chunks.stream()
                    .max(Comparator.comparingDouble(HybridRetrieverService.ScoredChunk::score))
                    .orElse(chunks.get(0));
            log.debug("[ConfidenceFilter] 所有 chunk 低于阈值 {}，保留最高分1条（score={}）",
                    minScore, best.score());
            filtered = List.of(best);
        }

        int filteredCount = chunks.size() - filtered.size();
        if (filteredCount > 0) {
            log.debug("[ConfidenceFilter] 过滤低置信度 chunk：{}条", filteredCount);
        }
        return filtered;
    }
}
