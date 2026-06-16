package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.trade.ragbase.entity.IndexTask;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.DocChunkRepository;
import com.trade.ragbase.repository.IndexTaskRepository;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.security.UserContext;
import com.trade.ragbase.service.loader.ParseResult;

class IndexServiceMinioTest {

    @Test
    void submitIndexTaskDelegatesMinioTaskWithCurrentUserContext() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        IndexTaskRepository taskRepository = mock(IndexTaskRepository.class);
        DocumentLoaderService loaderService = mock(DocumentLoaderService.class);
        ChunkService chunkService = mock(ChunkService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        IndexTaskLauncher taskLauncher = mock(IndexTaskLauncher.class);
        when(taskRepository.save(any(IndexTask.class))).thenAnswer(invocation -> {
            IndexTask task = invocation.getArgument(0);
            task.setId(88L);
            return task;
        });
        UserContext.set(42L, "TECH", "ADMIN");
        IndexService indexService = new IndexService(
                documentRepository, chunkRepository, taskRepository, loaderService,
                chunkService, embeddingService, minioStorageService, taskLauncher);

        indexService.submitIndexTask(9L);

        verify(taskLauncher).launchFromMinio(88L, 9L, 42L, "TECH", "ADMIN");
        UserContext.clear();
    }

    @Test
    void executeFromMinioDownloadsAndParsesDocumentBeforeIndexing() throws Exception {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        DocChunkRepository chunkRepository = mock(DocChunkRepository.class);
        IndexTaskRepository taskRepository = mock(IndexTaskRepository.class);
        DocumentLoaderService loaderService = mock(DocumentLoaderService.class);
        ChunkService chunkService = mock(ChunkService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);

        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setKbId(2L);
        document.setFileName("manual.txt");
        document.setMinioPath("kb/2/manual.txt");
        document.setVersion(1);

        IndexTask task = new IndexTask();
        task.setId(88L);
        task.setDocId(9L);

        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        when(taskRepository.findById(88L)).thenReturn(Optional.of(task));
        when(minioStorageService.download("kb/2/manual.txt"))
                .thenReturn("manual content".getBytes(StandardCharsets.UTF_8));
        when(loaderService.load(any(InputStream.class), any()))
                .thenReturn(ParseResult.builder()
                        .success(true)
                        .pages(List.of(ParseResult.PageContent.builder()
                                .pageNum(1)
                                .text("manual content")
                                .build()))
                        .totalPages(1)
                        .build());
        when(chunkService.chunk(any())).thenReturn(List.of());

        IndexService indexService = new IndexService(
                documentRepository, chunkRepository, taskRepository, loaderService,
                chunkService, embeddingService, minioStorageService, null);

        indexService.executeFromMinio(88L, 9L);

        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(loaderService).load(streamCaptor.capture(), org.mockito.Mockito.eq("manual.txt"));
        assertThat(new String(streamCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("manual content");
        assertThat(document.getStatus()).isEqualTo(KbDocument.DocumentStatus.FAILED);
        assertThat(document.getErrorMsg()).contains("分块结果为空");
    }
}
