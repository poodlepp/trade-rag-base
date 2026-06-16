package com.trade.ragbase.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.entity.IndexTask;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.DocChunkRepository;
import com.trade.ragbase.repository.IndexTaskRepository;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.service.loader.ParseResult;
import com.trade.ragbase.service.splitter.ChunkResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class IndexService {

    private static final int INSERT_BATCH_SIZE = 50;

    private final KbDocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final IndexTaskRepository taskRepository;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final IndexTaskLauncher taskLauncher;

    @Autowired
    public IndexService(
            KbDocumentRepository documentRepository,
            DocChunkRepository chunkRepository,
            IndexTaskRepository taskRepository,
            ChunkService chunkService,
            EmbeddingService embeddingService) {
        this(documentRepository, chunkRepository, taskRepository, chunkService, embeddingService, null);
    }

    public IndexService(
            KbDocumentRepository documentRepository,
            DocChunkRepository chunkRepository,
            IndexTaskRepository taskRepository,
            ChunkService chunkService,
            EmbeddingService embeddingService,
            IndexTaskLauncher taskLauncher) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.taskRepository = taskRepository;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.taskLauncher = taskLauncher;
    }

    public void submitIndexTask(Long docId, String textContent) {
        IndexTask task = new IndexTask();
        task.setDocId(docId);
        task.setTaskType("INDEX");
        taskRepository.save(task);

        if (taskLauncher == null) {
            executeWithText(task.getId(), docId, textContent);
            return;
        }
        taskLauncher.launchWithText(task.getId(), docId, textContent);
    }

    public void submitIndexTask(Long docId) {
        throw new UnsupportedOperationException("当前练习项目暂未迁移 MinIO 文件读取索引，请使用 submitIndexTask(docId, textContent)");
    }

    public void executeWithText(Long taskId, Long docId, String textContent) {
        KbDocument document = documentRepository.findById(docId).orElseThrow();
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .pages(List.of(ParseResult.PageContent.builder()
                        .pageNum(1)
                        .text(textContent)
                        .build()))
                .totalPages(1)
                .build();
        doIndex(taskId, docId, document, parseResult);
    }

    @Transactional
    protected void doIndex(Long taskId, Long docId, KbDocument document, ParseResult parseResult) {
        updateTaskStatus(taskId, IndexTask.TaskStatus.RUNNING);
        updateDocStatus(docId, KbDocument.DocumentStatus.PROCESSING);

        try {
            if (!parseResult.isSuccess()) {
                throw new IllegalStateException("文档解析失败：" + parseResult.getErrorMsg());
            }

            List<ChunkResult> chunks = chunkService.chunk(parseResult);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("分块结果为空，文档可能无有效文本内容");
            }
            log.info("[IndexService] docId={}，分块完成，共{}块", docId, chunks.size());

            List<String> texts = chunks.stream().map(ChunkResult::getContent).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            chunkRepository.deleteByDocIdAndDocVersionLessThan(docId, document.getVersion());
            batchInsertChunks(toDocChunks(docId, document, chunks, embeddings));

            int totalTokens = chunks.stream().mapToInt(ChunkResult::getEstimatedTokens).sum();
            document.setStatus(KbDocument.DocumentStatus.DONE);
            document.setErrorMsg(null);
            document.setChunkCount(chunks.size());
            document.setTokenCount(totalTokens);
            document.setIndexedAt(LocalDateTime.now());
            documentRepository.save(document);

            updateTaskStatus(taskId, IndexTask.TaskStatus.DONE);
            log.info("[IndexService] 索引完成：docId={}，chunks={}，tokens={}", docId, chunks.size(), totalTokens);
        } catch (Exception exception) {
            log.error("[IndexService] 索引失败：docId={}，error={}", docId, exception.getMessage(), exception);
            markFailed(taskId, docId, exception.getMessage());
        }
    }

    private List<DocChunk> toDocChunks(
            Long docId,
            KbDocument document,
            List<ChunkResult> chunks,
            List<float[]> embeddings) {
        List<DocChunk> docChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkResult chunk = chunks.get(i);
            DocChunk docChunk = new DocChunk();
            docChunk.setDocId(docId);
            docChunk.setKbId(document.getKbId());
            docChunk.setChunkIndex(chunk.getChunkIndex());
            docChunk.setContent(chunk.getContent());
            docChunk.setEmbedding(embeddings.get(i));
            docChunk.setPageNum(chunk.getPageNum());
            docChunk.setSectionTitle(chunk.getSectionTitle());
            docChunk.setTokenCount(chunk.getEstimatedTokens());
            docChunk.setDocVersion(document.getVersion());
            docChunks.add(docChunk);
        }
        return docChunks;
    }

    private void batchInsertChunks(List<DocChunk> chunks) {
        for (int start = 0; start < chunks.size(); start += INSERT_BATCH_SIZE) {
            List<DocChunk> batch = chunks.subList(start, Math.min(start + INSERT_BATCH_SIZE, chunks.size()));
            chunkRepository.saveAll(batch);
            log.debug("[IndexService] 写入批次 {}/{}",
                    start / INSERT_BATCH_SIZE + 1,
                    (chunks.size() + INSERT_BATCH_SIZE - 1) / INSERT_BATCH_SIZE);
        }
    }

    private void markFailed(Long taskId, Long docId, String errorMsg) {
        IndexTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(IndexTask.TaskStatus.FAILED);
        task.setErrorMsg(errorMsg);
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);

        documentRepository.findById(docId).ifPresent(document -> {
            document.setStatus(KbDocument.DocumentStatus.FAILED);
            document.setErrorMsg(errorMsg);
            documentRepository.save(document);
        });
    }

    private void updateTaskStatus(Long taskId, IndexTask.TaskStatus status) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            if (status == IndexTask.TaskStatus.RUNNING) {
                task.setStartedAt(LocalDateTime.now());
            }
            if (status == IndexTask.TaskStatus.DONE) {
                task.setFinishedAt(LocalDateTime.now());
            }
            taskRepository.save(task);
        });
    }

    private void updateDocStatus(Long docId, KbDocument.DocumentStatus status) {
        documentRepository.findById(docId).ifPresent(document -> {
            document.setStatus(status);
            documentRepository.save(document);
        });
    }
}
