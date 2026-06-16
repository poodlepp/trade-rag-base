package com.trade.ragbase.service.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MarkdownParser implements DocumentParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+(.+)");

    @Override
    public String supportedType() {
        return "MD";
    }

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        try {
            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            List<ParseResult.PageContent> pages = new ArrayList<>();
            StringBuilder currentSection = new StringBuilder();
            String currentTitle = null;
            int sectionCount = 0;
            boolean inCodeBlock = false;

            for (String line : markdown.split("\n")) {
                if (line.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    currentSection.append(line).append("\n");
                    continue;
                }
                if (inCodeBlock) {
                    currentSection.append(line).append("\n");
                    continue;
                }

                var matcher = HEADING_PATTERN.matcher(line);
                if (matcher.matches() && (line.startsWith("# ") || line.startsWith("## "))) {
                    if (!currentSection.isEmpty()) {
                        pages.add(ParseResult.PageContent.builder()
                                .pageNum(++sectionCount)
                                .text(stripMarkdownSyntax(currentSection.toString()))
                                .sectionTitle(currentTitle)
                                .build());
                        currentSection = new StringBuilder();
                    }
                    currentTitle = matcher.group(1);
                }
                currentSection.append(line).append("\n");
            }

            if (!currentSection.isEmpty()) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNum(++sectionCount)
                        .text(stripMarkdownSyntax(currentSection.toString()))
                        .sectionTitle(currentTitle)
                        .build());
            }

            if (pages.isEmpty()) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNum(1)
                        .text(stripMarkdownSyntax(markdown))
                        .build());
            }

            log.info("[MD解析] 文件={}，分节={}节", fileName, pages.size());
            return ParseResult.builder()
                    .success(true)
                    .pages(pages)
                    .totalPages(pages.size())
                    .build();
        } catch (Exception e) {
            log.error("[MD解析] 文件={}，解析失败：{}", fileName, e.getMessage(), e);
            return ParseResult.failure("Markdown 解析失败：" + e.getMessage());
        }
    }

    private String stripMarkdownSyntax(String markdown) {
        return markdown
                .replaceAll("```[\\s\\S]*?```", " [代码块] ")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("!\\[.*?]\\(.*?\\)", " [图片] ")
                .replaceAll("\\[([^]]+)]\\(.*?\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\*([^*]+)\\*", "$1")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
