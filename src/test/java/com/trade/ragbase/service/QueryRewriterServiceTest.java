package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class QueryRewriterServiceTest {

    @Test
    void generateHypotheticalAnswerReturnsModelContentAndRecordsTokens() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        TokenMetrics tokenMetrics = new TokenMetrics();
        QueryRewriterService service = new QueryRewriterService(chatClient, tokenMetrics);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("员工年假通常按工龄分配，每年有固定天数。");

        String answer = service.generateHypotheticalAnswer("年假有几天");

        assertThat(answer).contains("员工年假");
        assertThat(tokenMetrics.getGenerationTokens()).isGreaterThan(0);
    }

    @Test
    void generateHypotheticalAnswerFallsBackToQuestionWhenModelFails() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        QueryRewriterService service = new QueryRewriterService(chatClient, new TokenMetrics());
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new IllegalStateException("model unavailable"));

        String answer = service.generateHypotheticalAnswer("年假有几天");

        assertThat(answer).isEqualTo("年假有几天");
    }
}
