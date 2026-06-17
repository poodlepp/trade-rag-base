package com.trade.ragbase.service;

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.trade.ragbase.entity.DocChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ContextTrimmerService {

    private final TokenMetrics tokenMetrics;
    private final int maxContextTokens;
    private Encoding tokenizer;

    @Autowired
    public ContextTrimmerService(
            TokenMetrics tokenMetrics,
            @Value("${rag.context.max-tokens:3000}") int maxContextTokens) {
        this.tokenMetrics = tokenMetrics;
        this.maxContextTokens = maxContextTokens;
        init();
    }

    @PostConstruct
    public void init() {
        if (tokenizer == null) {
            tokenizer = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        }
    }

    public List<HybridRetrieverService.ScoredChunk> trim(
            List<HybridRetrieverService.ScoredChunk> candidates) {
        List<HybridRetrieverService.ScoredChunk> selected = new ArrayList<>();
        int usedTokens = 0;

        for (HybridRetrieverService.ScoredChunk scoredChunk : candidates) {
            int chunkTokens = countTokens(scoredChunk.content());
            if (usedTokens + chunkTokens <= maxContextTokens) {
                selected.add(scoredChunk);
                usedTokens += chunkTokens;
            } else if (selected.isEmpty()) {
                String truncated = truncateToTokens(scoredChunk.content(), maxContextTokens - usedTokens);
                if (!truncated.isBlank()) {
                    selected.add(new HybridRetrieverService.ScoredChunk(
                            copyWithContent(scoredChunk.chunk(), truncated), scoredChunk.score()));
                    usedTokens += countTokens(truncated);
                }
                break;
            } else {
                break;
            }
        }

        log.info("[ContextTrimmer] 候选={}，选取={}，usedTokens={}/{}",
                candidates.size(), selected.size(), usedTokens, maxContextTokens);
        tokenMetrics.recordContextTokens(usedTokens);
        return selected;
    }

    public int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return tokenizer == null ? Math.max(1, text.length() / 2) : tokenizer.encode(text).size();
    }

    private String truncateToTokens(String text, int maxTokens) {
        if (maxTokens <= 0) {
            return "";
        }
        if (countTokens(text) <= maxTokens) {
            return text;
        }

        String[] sentences = text.split("(?<=[。！？\\n])");
        StringBuilder result = new StringBuilder();
        int tokens = 0;
        for (String sentence : sentences) {
            int sentenceTokens = countTokens(sentence);
            if (tokens + sentenceTokens <= maxTokens) {
                result.append(sentence);
                tokens += sentenceTokens;
            } else {
                break;
            }
        }
        return result.isEmpty() ? text.substring(0, Math.min(text.length(), maxTokens)) : result.toString();
    }

    private DocChunk copyWithContent(DocChunk source, String content) {
        DocChunk chunk = new DocChunk();
        chunk.setId(source.getId());
        chunk.setDocId(source.getDocId());
        chunk.setKbId(source.getKbId());
        chunk.setChunkIndex(source.getChunkIndex());
        chunk.setContent(content);
        chunk.setEmbedding(source.getEmbedding());
        chunk.setPageNum(source.getPageNum());
        chunk.setSectionTitle(source.getSectionTitle());
        chunk.setTokenCount(countTokens(content));
        chunk.setDocVersion(source.getDocVersion());
        return chunk;
    }
}
