package com.trade.ragbase.service.loader;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ParseResult {

    private boolean success;

    private String errorMsg;

    private List<PageContent> pages;

    private int totalPages;

    private String title;

    public static ParseResult failure(String errorMsg) {
        return ParseResult.builder()
                .success(false)
                .errorMsg(errorMsg)
                .pages(List.of())
                .totalPages(0)
                .build();
    }

    public String getFullText() {
        if (pages == null) {
            return "";
        }
        return pages.stream()
                .map(PageContent::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce("", (left, right) -> left + "\n\n" + right)
                .strip();
    }

    @Data
    @Builder
    public static class PageContent {

        private int pageNum;

        private String text;

        private String sectionTitle;
    }
}
