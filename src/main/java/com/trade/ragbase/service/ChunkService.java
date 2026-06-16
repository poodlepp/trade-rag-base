package com.trade.ragbase.service;

import com.trade.ragbase.service.loader.ParseResult;
import com.trade.ragbase.service.splitter.ChunkConfig;
import com.trade.ragbase.service.splitter.ChunkResult;
import com.trade.ragbase.service.splitter.ChunkSplitter;
import com.trade.ragbase.service.splitter.SlidingWindowChunkSplitter;
import com.trade.ragbase.service.splitter.StructureAwareChunkSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ChunkService {

    private final SlidingWindowChunkSplitter slidingWindowSplitter;
    private final StructureAwareChunkSplitter structureAwareSplitter;
    private final int defaultChunkSize;
    private final int defaultOverlap;

    @Autowired
    public ChunkService(
            SlidingWindowChunkSplitter slidingWindowSplitter,
            StructureAwareChunkSplitter structureAwareSplitter,
            @Value("${rag.chunk.size:512}") int defaultChunkSize,
            @Value("${rag.chunk.overlap:64}") int defaultOverlap) {
        this.slidingWindowSplitter = slidingWindowSplitter;
        this.structureAwareSplitter = structureAwareSplitter;
        this.defaultChunkSize = defaultChunkSize;
        this.defaultOverlap = defaultOverlap;
    }

    public ChunkService(
            SlidingWindowChunkSplitter slidingWindowSplitter,
            StructureAwareChunkSplitter structureAwareSplitter) {
        this(slidingWindowSplitter, structureAwareSplitter, 512, 64);
    }

    public List<ChunkResult> chunk(ParseResult parseResult) {
        return chunk(parseResult, ChunkConfig.builder()
                .chunkSize(defaultChunkSize)
                .chunkOverlap(defaultOverlap)
                .build());
    }

    public List<ChunkResult> chunk(ParseResult parseResult, ChunkConfig config) {
        if (parseResult == null || !parseResult.isSuccess()) {
            return List.of();
        }

        boolean hasStructure = parseResult.getPages() != null
                && parseResult.getPages().stream()
                .anyMatch(page -> page.getSectionTitle() != null && !page.getSectionTitle().isBlank());
        ChunkSplitter splitter = hasStructure && config.isStructureAware()
                ? structureAwareSplitter
                : slidingWindowSplitter;

        List<ChunkResult> chunks = splitter.split(parseResult, config).stream()
                .filter(chunk -> chunk.getContent() != null && chunk.getContent().length() >= 20)
                .toList();

        log.debug("[分块] 完成分块：策略={}，共{}块，总字符={}",
                splitter.getClass().getSimpleName(),
                chunks.size(),
                chunks.stream().mapToInt(chunk -> chunk.getContent().length()).sum());

        return chunks;
    }
}
