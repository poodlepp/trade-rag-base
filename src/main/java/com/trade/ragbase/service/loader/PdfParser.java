package com.trade.ragbase.service.loader;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PdfParser implements DocumentParser {

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+\\.|Chapter\\s+\\d+).*");
    private static final Pattern TOC_LINE_PATTERN =
            Pattern.compile(".*[.\\u2026·•\\-\\s]{4,}\\d{1,4}\\s*$");

    @Override
    public String supportedType() {
        return "PDF";
    }

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            int totalPages = document.getNumberOfPages();
            List<ParseResult.PageContent> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                try {
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    String text = cleanText(stripper.getText(document));
                    if (text.isBlank()) {
                        log.debug("[PDF解析] 第{}页内容为空，跳过", pageNum);
                        continue;
                    }
                    if (isTocPage(text)) {
                        log.info("[PDF解析] 第{}页疑似目录页，跳过索引", pageNum);
                        continue;
                    }
                    pages.add(ParseResult.PageContent.builder()
                            .pageNum(pageNum)
                            .text(text)
                            .sectionTitle(detectHeading(text))
                            .build());
                } catch (Exception e) {
                    log.warn("[PDF解析] 第{}页解析失败：{}", pageNum, e.getMessage());
                }
            }

            if (pages.isEmpty()) {
                return ParseResult.failure("PDF 解析后无有效文本内容，可能是纯图片 PDF，需要 OCR 处理");
            }

            log.info("[PDF解析] 文件={}，总页数={}，有效页数={}", fileName, totalPages, pages.size());
            return ParseResult.builder()
                    .success(true)
                    .pages(pages)
                    .totalPages(totalPages)
                    .title(extractTitle(pages))
                    .build();
        } catch (Exception e) {
            log.error("[PDF解析] 文件={}，解析失败：{}", fileName, e.getMessage(), e);
            return ParseResult.failure("PDF 解析失败：" + e.getMessage());
        }
    }

    private boolean isTocPage(String text) {
        String[] lines = text.split("\n");
        int nonBlank = 0;
        int tocLines = 0;
        boolean hasTocHeading = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            nonBlank++;
            String compact = line.replace(" ", "");
            if (compact.equals("目录") || compact.equalsIgnoreCase("contents")
                    || compact.equalsIgnoreCase("tableofcontents")) {
                hasTocHeading = true;
            }
            if (TOC_LINE_PATTERN.matcher(line).matches()) {
                tocLines++;
            }
        }

        if (nonBlank == 0) {
            return false;
        }
        double ratio = (double) tocLines / nonBlank;
        return (hasTocHeading && tocLines >= 2) || ratio >= 0.4;
    }

    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private String detectHeading(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String line = lines[i].strip();
            if (line.length() > 2 && line.length() < 80 && HEADING_PATTERN.matcher(line).matches()) {
                return line;
            }
        }
        return null;
    }

    private String extractTitle(List<ParseResult.PageContent> pages) {
        if (pages.isEmpty()) {
            return null;
        }
        for (String line : pages.get(0).getText().split("\n")) {
            line = line.strip();
            if (!line.isBlank() && line.length() < 100) {
                return line;
            }
        }
        return null;
    }
}
