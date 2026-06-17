package com.trade.ragbase.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TsQueryBuilder {

    private static final List<String> STOP_WORDS = List.of(
            "的", "了", "是", "在", "有", "和", "与", "或", "这", "那",
            "什么", "怎么", "如何", "为什么", "哪些", "怎样", "请问",
            "a", "an", "the", "is", "are", "what", "how");

    public String build(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String[] tokens = query.split("[\\s\\p{P}]+");
        List<String> keywords = Arrays.stream(tokens)
                .map(String::strip)
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token.toLowerCase()))
                .collect(Collectors.toList());

        if (keywords.isEmpty()) {
            keywords = List.of(query.substring(0, Math.min(20, query.length())));
        }

        String tsQuery = String.join(" & ", keywords);
        log.debug("[TsQuery] query='{}' -> tsQuery='{}'", query, tsQuery);
        return tsQuery;
    }
}
