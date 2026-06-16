package com.trade.ragbase.service.splitter;

import com.trade.ragbase.service.loader.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("structureAwareSplitter")
public class StructureAwareChunkSplitter implements ChunkSplitter {

    private static final Pattern EXPLICIT_HEADING = Pattern.compile(
            "^(#{1,3}\\s+|第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、).{1,60}$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(\\d+(?:\\.\\d+)*)\\.?\\s+(.{1,40})$");
    private static final Pattern SENTENCE_PUNCT = Pattern.compile("[，。！？；,.!?;]");
    private static final int SINGLE_LEVEL_TITLE_MAX = 8;

    private final SlidingWindowChunkSplitter slidingSplitter;

    public StructureAwareChunkSplitter(SlidingWindowChunkSplitter slidingSplitter) {
        this.slidingSplitter = slidingSplitter;
    }

    @Override
    public List<ChunkResult> split(ParseResult parseResult, ChunkConfig config) {
        List<TextSection> sections = extractSections(parseResult);
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (TextSection section : sections) {
            if (section.text().length() <= config.getChunkSize()) {
                String content = withTitle(section.title(), section.text());
                chunks.add(ChunkResult.builder()
                        .chunkIndex(chunkIndex++)
                        .content(content)
                        .pageNum(section.pageNum())
                        .sectionTitle(section.title())
                        .estimatedTokens(estimateTokens(content))
                        .build());
                continue;
            }

            ParseResult sectionResult = ParseResult.builder()
                    .success(true)
                    .pages(List.of(ParseResult.PageContent.builder()
                            .pageNum(section.pageNum())
                            .text(section.text())
                            .sectionTitle(section.title())
                            .build()))
                    .totalPages(1)
                    .build();

            for (ChunkResult sub : slidingSplitter.split(sectionResult, config)) {
                sub.setChunkIndex(chunkIndex++);
                if (sub.getSectionTitle() == null) {
                    sub.setSectionTitle(section.title());
                }
                sub.setContent(withTitle(section.title(), sub.getContent()));
                sub.setEstimatedTokens(estimateTokens(sub.getContent()));
                chunks.add(sub);
            }
        }

        return chunks;
    }

    private List<TextSection> extractSections(ParseResult parseResult) {
        List<TextSection> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentTitle = null;
        int sectionStartPage = 1;

        for (ParseResult.PageContent page : parseResult.getPages()) {
            if (page.getSectionTitle() != null && current.isEmpty()) {
                currentTitle = page.getSectionTitle();
                sectionStartPage = page.getPageNum();
            }

            String text = page.getText() == null ? "" : page.getText();
            for (String line : text.split("\n")) {
                String stripped = line.strip();
                if (isHeading(stripped)) {
                    if (current.length() > 50) {
                        sections.add(new TextSection(currentTitle, current.toString().strip(), sectionStartPage));
                        current = new StringBuilder();
                        sectionStartPage = page.getPageNum();
                    }
                    currentTitle = stripped;
                    continue;
                }
                current.append(line).append("\n");
            }
        }

        if (!current.isEmpty()) {
            sections.add(new TextSection(currentTitle, current.toString().strip(), sectionStartPage));
        }
        return sections;
    }

    private boolean isHeading(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty()) {
            return false;
        }
        if (EXPLICIT_HEADING.matcher(stripped).matches()) {
            return true;
        }

        Matcher matcher = NUMBERED_HEADING.matcher(stripped);
        if (matcher.matches()) {
            String number = matcher.group(1);
            String title = matcher.group(2).strip();
            if (number.contains(".")) {
                return true;
            }
            return title.length() <= SINGLE_LEVEL_TITLE_MAX
                    && !SENTENCE_PUNCT.matcher(title).find();
        }
        return false;
    }

    private String withTitle(String title, String body) {
        if (title == null || title.isBlank()) {
            return body;
        }
        String strippedTitle = title.strip();
        if (body != null && body.stripLeading().startsWith(strippedTitle)) {
            return body;
        }
        return strippedTitle + "\n" + (body == null ? "" : body);
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

    record TextSection(String title, String text, int pageNum) {
    }
}
