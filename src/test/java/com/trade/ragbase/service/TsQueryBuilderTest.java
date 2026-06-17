package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TsQueryBuilderTest {

    @Test
    void buildFiltersStopWordsAndJoinsKeywords() {
        TsQueryBuilder builder = new TsQueryBuilder();

        String tsQuery = builder.build("API 限流 策略 是 什么");

        assertThat(tsQuery).isEqualTo("API & 限流 & 策略");
    }

    @Test
    void buildReturnsNullForBlankQuery() {
        TsQueryBuilder builder = new TsQueryBuilder();

        assertThat(builder.build(" ")).isNull();
    }
}
