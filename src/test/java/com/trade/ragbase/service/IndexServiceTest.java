package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.entity.IndexTask;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.DocChunkRepository;
import com.trade.ragbase.repository.IndexTaskRepository;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.service.splitter.ChunkResult;

class IndexServiceTest {

    @Test
    void executeWithTextIndexesDocumentAndKeepsOldChunksUntilEmbeddingSucceeds() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        IndexTaskRepository taskRepository = mock(IndexTaskRepository.class);
        ChunkService chunkService = mock(ChunkService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        KbDocument document = new KbDocument();
        document.setId(11L);
        document.setKbId(7L);
        document.setFileName("manual.txt");
        document.setFileType("TXT");
        document.setFileSize(1024L);
        document.setMinioPath("kb/7/manual.txt");
        document.setUploadedBy(100L);
        document.setVersion(3);

        IndexTask task = new IndexTask();
        task.setId(99L);
        task.setDocId(11L);

        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));
        when(taskRepository.findById(99L)).thenReturn(Optional.of(task));
        when(chunkService.chunk(any())).thenReturn(List.of(
                ChunkResult.builder()
                        .chunkIndex(0)
                        .content("第一段内容足够长，用于模拟一个有效 chunk。")
                        .pageNum(1)
                        .sectionTitle("第一章")
                        .estimatedTokens(18)
                        .build(),
                ChunkResult.builder()
                        .chunkIndex(1)
                        .content("第二段内容也足够长，用于模拟另一个有效 chunk。")
                        .pageNum(2)
                        .sectionTitle("第二章")
                        .estimatedTokens(20)
                        .build()));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(
                new float[]{0.1f, 0.2f},
                new float[]{0.3f, 0.4f}));

        IndexService indexService = new IndexService(
                documentRepository,
                chunkRepository,
                taskRepository,
                chunkService,
                embeddingService);

        indexService.executeWithText(99L, 11L, "这是一段直接传入的测试文本。");

        InOrder order = inOrder(embeddingService, chunkRepository);
        order.verify(embeddingService).embedBatch(List.of(
                "第一段内容足够长，用于模拟一个有效 chunk。",
                "第二段内容也足够长，用于模拟另一个有效 chunk。"));
        order.verify(chunkRepository).deleteByDocIdAndDocVersionLessThan(11L, 3);

        ArgumentCaptor<List<DocChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocChunk> savedChunks = chunksCaptor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks.get(0).getDocId()).isEqualTo(11L);
        assertThat(savedChunks.get(0).getKbId()).isEqualTo(7L);
        assertThat(savedChunks.get(0).getChunkIndex()).isZero();
        assertThat(savedChunks.get(0).getContent()).isEqualTo("第一段内容足够长，用于模拟一个有效 chunk。");
        assertThat(savedChunks.get(0).getEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(savedChunks.get(0).getPageNum()).isEqualTo(1);
        assertThat(savedChunks.get(0).getSectionTitle()).isEqualTo("第一章");
        assertThat(savedChunks.get(0).getTokenCount()).isEqualTo(18);
        assertThat(savedChunks.get(0).getDocVersion()).isEqualTo(3);

        assertThat(document.getStatus()).isEqualTo(KbDocument.DocumentStatus.DONE);
        assertThat(document.getChunkCount()).isEqualTo(2);
        assertThat(document.getTokenCount()).isEqualTo(38);
        assertThat(document.getIndexedAt()).isNotNull();
        assertThat(task.getStatus()).isEqualTo(IndexTask.TaskStatus.DONE);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getFinishedAt()).isNotNull();
        verify(documentRepository, times(2)).save(document);
        verify(taskRepository, times(2)).save(task);
    }

    @Test
    void submitIndexTaskCreatesTaskAndDelegatesToLauncher() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        IndexTaskRepository taskRepository = mock(IndexTaskRepository.class);
        ChunkService chunkService = mock(ChunkService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        IndexTaskLauncher taskLauncher = mock(IndexTaskLauncher.class);

        when(taskRepository.save(any(IndexTask.class))).thenAnswer(invocation -> {
            IndexTask task = invocation.getArgument(0);
            task.setId(123L);
            return task;
        });

        IndexService indexService = new IndexService(
                documentRepository,
                chunkRepository,
                taskRepository,
                chunkService,
                embeddingService,
                taskLauncher);

        indexService.submitIndexTask(11L, "直接索引文本");

        ArgumentCaptor<IndexTask> taskCaptor = ArgumentCaptor.forClass(IndexTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getDocId()).isEqualTo(11L);
        assertThat(taskCaptor.getValue().getTaskType()).isEqualTo("INDEX");
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(IndexTask.TaskStatus.PENDING);
        verify(taskLauncher).launchWithText(123L, 11L, "直接索引文本");
    }
}
