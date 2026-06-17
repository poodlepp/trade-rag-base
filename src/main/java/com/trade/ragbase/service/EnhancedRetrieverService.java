package com.trade.ragbase.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.repository.DocChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EnhancedRetrieverService {

    private static final int RRF_K = 60;

    private final HybridRetrieverService hybridRetriever;
    private final QueryRewriterService queryRewriter;
    private final EmbeddingService embeddingService;
    private final DocChunkRepository chunkRepository;
    private final int vectorTopK;

    @Autowired
    public EnhancedRetrieverService(
            HybridRetrieverService hybridRetriever,
            QueryRewriterService queryRewriter,
            EmbeddingService embeddingService,
            DocChunkRepository chunkRepository,
            @Value("${rag.retrieval.vector-top-k:20}") int vectorTopK) {
        this.hybridRetriever = hybridRetriever;
        this.queryRewriter = queryRewriter;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.vectorTopK = vectorTopK;
    }

    public List<HybridRetrieverService.ScoredChunk> retrieveWithHyde(
            String question,
            List<Long> kbIds,
            int topN) {
        List<HybridRetrieverService.ScoredChunk> originalResults =
                hybridRetriever.retrieve(question, kbIds, vectorTopK);

        String hydeAnswer = queryRewriter.generateHypotheticalAnswer(question);
        float[] hydeEmbedding = embeddingService.embed(hydeAnswer);
        String hydeEmbeddingText = toVectorString(hydeEmbedding);
        List<DocChunk> hydeResults = kbIds.stream()
                .flatMap(kbId -> chunkRepository
                        .findByVectorSimilarity(kbId, hydeEmbeddingText, vectorTopK)
                        .stream())
                .collect(Collectors.toList());

        log.debug("[EnhancedRetriever] 原始检索={}，HyDE检索={}", originalResults.size(), hydeResults.size());
        return rrfMerge(originalResults, hydeResults).stream()
                .limit(topN)
                .collect(Collectors.toList());
    }

    private List<HybridRetrieverService.ScoredChunk> rrfMerge(
            List<HybridRetrieverService.ScoredChunk> originalResults,
            List<DocChunk> hydeResults) {
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        Map<Long, DocChunk> chunkMap = new HashMap<>();

        for (int rank = 0; rank < originalResults.size(); rank++) {
            HybridRetrieverService.ScoredChunk scoredChunk = originalResults.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(scoredChunk.id(), rrfScore, Double::sum);
            chunkMap.put(scoredChunk.id(), scoredChunk.chunk());
        }

        for (int rank = 0; rank < hydeResults.size(); rank++) {
            DocChunk chunk = hydeResults.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(chunk.getId(), rrfScore, Double::sum);
            chunkMap.put(chunk.getId(), chunk);
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(entry -> new HybridRetrieverService.ScoredChunk(
                        chunkMap.get(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
    }

    private String toVectorString(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(embedding[i]);
        }
        builder.append("]");
        return builder.toString();
    }
}
