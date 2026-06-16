package com.trade.ragbase.service.splitter;

import com.trade.ragbase.service.loader.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SlidingWindowChunkSplitter implements ChunkSplitter {

    private static final int MAX_BACKTRACK = 100;

    @Override
    public List<ChunkResult> split(ParseResult parseResult, ChunkConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ParseResult.PageContent page : parseResult.getPages()) {
            String text = page.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            List<String> pageChunks = splitText(text, config.getChunkSize(), config.getChunkOverlap());
            for (String chunkText : pageChunks) {
                if (chunkText.isBlank()) {
                    continue;
                }
                chunks.add(ChunkResult.builder()
                        .chunkIndex(chunkIndex++)
                        .content(chunkText)
                        .pageNum(page.getPageNum())
                        .sectionTitle(page.getSectionTitle())
                        .estimatedTokens(estimateTokens(chunkText))
                        .build());
            }
        }

        log.debug("[分块] 文档分块完成，共{}块，avgSize={}字符",
                chunks.size(),
                chunks.isEmpty()
                        ? 0
                        : chunks.stream().mapToInt(chunk -> chunk.getContent().length()).average().orElse(0));
        return chunks;
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                end = findGoodBreakPoint(text, end);
            }

            String chunk = text.substring(start, end).strip();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            int nextStart = end - overlap;
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return result;
    }

    private int findGoodBreakPoint(String text, int position) {
        int searchLimit = position - MAX_BACKTRACK;
        String[] breakChars = {"\n\n", "\n", "。", "！", "？", "；", "，", " "};

        for (String breakChar : breakChars) {
            int idx = text.lastIndexOf(breakChar, position);
            if (idx > searchLimit && idx > 0) {
                return idx + breakChar.length();
            }
        }
        return position;
    }

    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char ch : text.toCharArray()) {
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                chineseChars++;
            } else if (!Character.isWhitespace(ch)) {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.3);
    }
}
