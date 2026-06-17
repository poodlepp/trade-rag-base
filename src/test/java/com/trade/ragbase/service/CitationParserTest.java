package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationParserTest {

    @Test
    void extractCitedIndicesKeepsAnswerOrderAndRemovesDuplicates() {
        CitationParser parser = new CitationParser();

        assertThat(parser.extractCitedIndices("年假10天（来源：[参考2][参考1]），流程见[参考2]"))
                .containsExactly(2, 1);
    }

    @Test
    void cleanCitationsRemovesReferenceMarkers() {
        CitationParser parser = new CitationParser();

        assertThat(parser.cleanCitations("年假10天（来源：[参考1]） 请见[参考2]"))
                .isEqualTo("年假10天 请见");
    }
}
