package com.trade.ragbase.service.splitter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.trade.ragbase.service.ChunkService;
import com.trade.ragbase.service.loader.ParseResult;

class ChunkServiceTest {

    private final SlidingWindowChunkSplitter slidingWindowSplitter = new SlidingWindowChunkSplitter();
    private final ChunkService chunkService = new ChunkService(
            slidingWindowSplitter,
            new StructureAwareChunkSplitter(slidingWindowSplitter));

    @Test
    void slidingWindowChunksLongPageWithOverlapAndPageMetadata() {
        String text = "这是一段测试文本。".repeat(200);
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .pages(List.of(ParseResult.PageContent.builder()
                        .pageNum(3)
                        .sectionTitle("测试章节")
                        .text(text)
                        .build()))
                .totalPages(1)
                .build();

        List<ChunkResult> chunks = chunkService.chunk(parseResult,
                ChunkConfig.builder()
                        .chunkSize(120)
                        .chunkOverlap(20)
                        .structureAware(false)
                        .build());

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getContent()).hasSizeLessThanOrEqualTo(120);
            assertThat(chunk.getContent()).hasSizeGreaterThanOrEqualTo(20);
            assertThat(chunk.getPageNum()).isEqualTo(3);
            assertThat(chunk.getSectionTitle()).isEqualTo("测试章节");
            assertThat(chunk.getEstimatedTokens()).isGreaterThan(0);
        });

        String firstTail = chunks.get(0).getContent()
                .substring(chunks.get(0).getContent().length() - 20);
        assertThat(chunks.get(1).getContent()).contains(firstTail.substring(0, 10));
    }

    @Test
    void structureAwareKeepsSmallSectionTogetherAndPrefixesTitle() {
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .pages(List.of(ParseResult.PageContent.builder()
                        .pageNum(1)
                        .sectionTitle("入职流程")
                        .text("第一天领取工牌，配置账号，阅读员工手册。")
                        .build()))
                .totalPages(1)
                .build();

        List<ChunkResult> chunks = chunkService.chunk(parseResult,
                ChunkConfig.builder()
                        .chunkSize(200)
                        .chunkOverlap(20)
                        .structureAware(true)
                        .build());

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).startsWith("入职流程\n");
        assertThat(chunks.get(0).getContent()).contains("领取工牌");
        assertThat(chunks.get(0).getPageNum()).isEqualTo(1);
        assertThat(chunks.get(0).getSectionTitle()).isEqualTo("入职流程");
    }

    @Test
    void chunkReturnsEmptyForFailedParseResult() {
        List<ChunkResult> chunks = chunkService.chunk(ParseResult.failure("解析失败"));

        assertThat(chunks).isEmpty();
    }
}
