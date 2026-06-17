package com.trade.ragbase.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.trade.ragbase.dto.RagResponse;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.KbDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SourceBuilder {

    private final CitationParser citationParser;
    private final KbDocumentRepository documentRepository;

    public SourceBuilder(CitationParser citationParser, KbDocumentRepository documentRepository) {
        this.citationParser = citationParser;
        this.documentRepository = documentRepository;
    }

    public List<RagResponse.Source> buildSources(
            String answer,
            List<HybridRetrieverService.ScoredChunk> chunks) {
        Set<Integer> citedIndices = citationParser.extractCitedIndices(answer);
        if (citedIndices.isEmpty()) {
            log.debug("[SourceBuilder] 模型未标注引用，使用所有 chunk 作为来源");
            citedIndices = new LinkedHashSet<>();
            for (int index = 1; index <= chunks.size(); index++) {
                citedIndices.add(index);
            }
        }

        Set<Long> docIds = chunks.stream()
                .map(scoredChunk -> scoredChunk.chunk().getDocId())
                .collect(Collectors.toSet());
        Map<Long, KbDocument> docMap = documentRepository.findAllById(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, document -> document));

        List<RagResponse.Source> sources = new ArrayList<>();
        for (int index : citedIndices) {
            if (index < 1 || index > chunks.size()) {
                continue;
            }

            HybridRetrieverService.ScoredChunk scoredChunk = chunks.get(index - 1);
            KbDocument document = docMap.get(scoredChunk.chunk().getDocId());
            String content = scoredChunk.content();
            sources.add(RagResponse.Source.builder()
                    .chunkId(scoredChunk.id())
                    .docId(scoredChunk.chunk().getDocId())
                    .docName(document == null ? "未知文档" : document.getFileName())
                    .pageNum(scoredChunk.chunk().getPageNum())
                    .sectionTitle(scoredChunk.chunk().getSectionTitle())
                    .excerpt(content.substring(0, Math.min(200, content.length())))
                    .score(scoredChunk.score())
                    .build());
        }

        return sources;
    }
}
