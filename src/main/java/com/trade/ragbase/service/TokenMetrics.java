package com.trade.ragbase.service;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * RAG Token 消耗统计器。
 *
 * <p>原项目会同时写 Micrometer 和按用户维度的 Redis Hash。本练习项目暂未迁移用户上下文，
 * 这里先保留服务内存级统计，等鉴权和用户体系明确后再扩展持久化维度。</p>
 */
@Component
public class TokenMetrics {

    private final AtomicLong embeddingTokens = new AtomicLong();
    private final AtomicLong contextTokens = new AtomicLong();
    private final AtomicLong generationTokens = new AtomicLong();

    public void recordEmbeddingTokens(int tokens) {
        if (tokens > 0) {
            embeddingTokens.addAndGet(tokens);
        }
    }

    public void recordContextTokens(int tokens) {
        if (tokens > 0) {
            contextTokens.addAndGet(tokens);
        }
    }

    public void recordGenerationTokens(int tokens) {
        if (tokens > 0) {
            generationTokens.addAndGet(tokens);
        }
    }

    public long getEmbeddingTokens() {
        return embeddingTokens.get();
    }

    public long getContextTokens() {
        return contextTokens.get();
    }

    public long getGenerationTokens() {
        return generationTokens.get();
    }
}
