package com.trade.ragbase.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.DocChunkRepository;
import com.trade.ragbase.repository.KbPermissionRepository;
import com.trade.ragbase.repository.KnowledgeBaseRepository;
import com.trade.ragbase.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HybridRetrieverService {

    private static final int RRF_K = 60;

    private final EmbeddingService embeddingService;
    private final DocChunkRepository chunkRepository;
    private final TsQueryBuilder tsQueryBuilder;
    private final KnowledgeBaseRepository kbRepository;
    private final KbPermissionRepository permissionRepository;
    private final int vectorTopK;
    private final int fulltextTopK;

    @Autowired
    public HybridRetrieverService(
            EmbeddingService embeddingService,
            DocChunkRepository chunkRepository,
            TsQueryBuilder tsQueryBuilder,
            KnowledgeBaseRepository kbRepository,
            KbPermissionRepository permissionRepository,
            @Value("${rag.retrieval.vector-top-k:20}") int vectorTopK,
            @Value("${rag.retrieval.fulltext-top-k:20}") int fulltextTopK) {
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.tsQueryBuilder = tsQueryBuilder;
        this.kbRepository = kbRepository;
        this.permissionRepository = permissionRepository;
        this.vectorTopK = vectorTopK;
        this.fulltextTopK = fulltextTopK;
    }

    public List<ScoredChunk> retrieve(String question, List<Long> kbIds, int topN) {
        float[] queryEmbedding = embeddingService.embed(question);
        String embeddingText = toVectorString(queryEmbedding);

        List<DocChunk> vectorResults = kbIds.stream()
                .flatMap(kbId -> chunkRepository.findByVectorSimilarity(kbId, embeddingText, vectorTopK).stream())
                .collect(Collectors.toList());

        String tsQuery = tsQueryBuilder.build(question);
        List<DocChunk> fulltextResults = new ArrayList<>();
        if (tsQuery != null) {
            fulltextResults = kbIds.stream()
                    .flatMap(kbId -> chunkRepository.findByFullTextSearch(kbId, tsQuery, fulltextTopK).stream())
                    .collect(Collectors.toList());
        }

        log.debug("[HybridRetriever] 向量检索召回={}，全文检索召回={}",
                vectorResults.size(), fulltextResults.size());

        List<ScoredChunk> topResults = rrfMerge(vectorResults, fulltextResults).stream()
                .limit(topN)
                .collect(Collectors.toList());
        log.info("[HybridRetriever] RRF 融合后 TopN={}，返回 {} 条", topN, topResults.size());
        return topResults;
    }

    public List<ScoredChunk> retrieveWithPermissionCheck(String question, List<Long> requestedKbIds, int topN) {
        List<Long> allowedKbIds = filterAllowedKbIds(requestedKbIds);
        if (allowedKbIds.isEmpty()) {
            throw BizException.forbidden("您对所请求的知识库没有访问权限");
        }

        if (allowedKbIds.size() < requestedKbIds.size()) {
            List<Long> denied = requestedKbIds.stream()
                    .filter(id -> !allowedKbIds.contains(id))
                    .toList();
            log.warn("[权限过滤] userId={} 无权访问 kbIds={}，已过滤", UserContext.getUserId(), denied);
        }

        return retrieve(question, allowedKbIds, topN);
    }

    private List<Long> filterAllowedKbIds(List<Long> kbIds) {
        if (UserContext.isAdmin()) {
            return kbIds;
        }

        String userId = String.valueOf(UserContext.getUserId());
        String departmentId = UserContext.getDepartmentId();
        return kbIds.stream()
                .filter(kbId -> {
                    boolean isPublic = kbRepository.findById(kbId)
                            .map(kb -> Boolean.TRUE.equals(kb.getIsPublic()))
                            .orElse(false);
                    if (isPublic) {
                        return true;
                    }
                    return permissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "USER", userId)
                            || permissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(
                            kbId, "DEPARTMENT", departmentId);
                })
                .toList();
    }

    private List<ScoredChunk> rrfMerge(List<DocChunk> vectorList, List<DocChunk> fulltextList) {
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        Map<Long, DocChunk> chunkMap = new HashMap<>();

        for (int rank = 0; rank < vectorList.size(); rank++) {
            DocChunk chunk = vectorList.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(chunk.getId(), rrfScore, Double::sum);
            chunkMap.put(chunk.getId(), chunk);
        }

        for (int rank = 0; rank < fulltextList.size(); rank++) {
            DocChunk chunk = fulltextList.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(chunk.getId(), rrfScore, Double::sum);
            chunkMap.put(chunk.getId(), chunk);
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(entry -> new ScoredChunk(chunkMap.get(entry.getKey()), entry.getValue()))
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

    public record ScoredChunk(DocChunk chunk, double score) {

        public Long id() {
            return chunk.getId();
        }

        public String content() {
            return chunk.getContent();
        }
    }
}
