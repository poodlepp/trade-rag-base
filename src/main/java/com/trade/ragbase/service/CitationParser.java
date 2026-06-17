package com.trade.ragbase.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class CitationParser {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[参考(\\d+)]");

    public Set<Integer> extractCitedIndices(String answer) {
        Set<Integer> cited = new LinkedHashSet<>();
        if (answer == null || answer.isBlank()) {
            return cited;
        }

        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            try {
                cited.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed citation numbers.
            }
        }
        return cited;
    }

    public String cleanCitations(String answer) {
        if (answer == null) {
            return "";
        }
        return answer
                .replaceAll("（来源：(?:\\[参考\\d+])+）", "")
                .replaceAll("\\[参考\\d+]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
