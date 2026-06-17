package com.trade.ragbase.service;

import java.util.List;

import com.trade.ragbase.dto.RagResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FullRagPipeline {

    private final EnhancedRetrieverService enhancedRetriever;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final ContextTrimmerService contextTrimmer;
    private final SourceBuilder sourceBuilder;
    private final HallucinationChecker hallucinationChecker;
    private final ChatClient chatClient;
    private final int rerankerTopN;
    private final boolean hallucinationCheckEnabled;

    @Autowired
    public FullRagPipeline(
            EnhancedRetrieverService enhancedRetriever,
            RerankerService rerankerService,
            ConfidenceFilter confidenceFilter,
            ContextTrimmerService contextTrimmer,
            SourceBuilder sourceBuilder,
            HallucinationChecker hallucinationChecker,
            ChatClient chatClient,
            @Value("${reranker.top-n:5}") int rerankerTopN,
            @Value("${rag.hallucination-check.enabled:false}") boolean hallucinationCheckEnabled) {
        this.enhancedRetriever = enhancedRetriever;
        this.rerankerService = rerankerService;
        this.confidenceFilter = confidenceFilter;
        this.contextTrimmer = contextTrimmer;
        this.sourceBuilder = sourceBuilder;
        this.hallucinationChecker = hallucinationChecker;
        this.chatClient = chatClient;
        this.rerankerTopN = rerankerTopN;
        this.hallucinationCheckEnabled = hallucinationCheckEnabled;
    }

    public RagResponse query(String question, List<Long> kbIds) {
        long pipelineStart = System.currentTimeMillis();
        List<HybridRetrieverService.ScoredChunk> candidates =
                enhancedRetriever.retrieveWithHyde(question, kbIds, 20);
        if (candidates.isEmpty()) {
            return RagResponse.notFound();
        }

        List<HybridRetrieverService.ScoredChunk> reranked =
                rerankerService.rerank(question, candidates, rerankerTopN);
        List<HybridRetrieverService.ScoredChunk> filtered = confidenceFilter.filter(reranked);
        if (filtered.isEmpty()) {
            return RagResponse.notFound();
        }

        List<HybridRetrieverService.ScoredChunk> trimmed = contextTrimmer.trim(filtered);
        String context = buildContext(trimmed);
        String answer = generateAnswer(question, context, trimmed.size());
        List<RagResponse.Source> sources = sourceBuilder.buildSources(answer, trimmed);

        if (hallucinationCheckEnabled) {
            HallucinationChecker.FaithfulnessResult result =
                    hallucinationChecker.check(question, answer, context);
            if (!result.isFaithful()) {
                log.warn("[FullRagPipeline] 幻觉检测不通过：score={}，reason={}",
                        result.score(), result.reason());
            }
        }

        long elapsed = System.currentTimeMillis() - pipelineStart;
        log.info("[FullRagPipeline] 完成：question={}，elapsed={}ms，sources={}",
                question.substring(0, Math.min(30, question.length())), elapsed, sources.size());
        return RagResponse.builder()
                .answer(answer)
                .sources(sources)
                .latencyMs((int) elapsed)
                .build();
    }

    private String buildContext(List<HybridRetrieverService.ScoredChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            HybridRetrieverService.ScoredChunk scoredChunk = chunks.get(i);
            builder.append("[参考").append(i + 1).append("]");
            if (scoredChunk.chunk().getSectionTitle() != null) {
                builder.append(" ").append(scoredChunk.chunk().getSectionTitle());
            }
            builder.append("\n").append(scoredChunk.content()).append("\n\n");
        }
        return builder.toString().strip();
    }

    private String generateAnswer(String question, String context, int chunkCount) {
        return chatClient.prompt()
                .system(RagPromptTemplate.buildSystemPrompt(context, chunkCount))
                .user(question)
                .call()
                .content();
    }
}
