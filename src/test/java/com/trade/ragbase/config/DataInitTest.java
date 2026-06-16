package com.trade.ragbase.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.service.IndexService;

class DataInitTest {

    @Test
    void skipsWhenDisabled() throws Exception {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        IndexService indexService = mock(IndexService.class);
        DataInitializer initializer = new DataInitializer(documentRepository, indexService, false);

        initializer.run(new DefaultApplicationArguments());

        verify(documentRepository, never()).count();
        verify(indexService, never()).submitIndexTask(any(), any());
    }

    @Test
    void skipsWhenDocumentsAlreadyExist() throws Exception {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        IndexService indexService = mock(IndexService.class);
        when(documentRepository.count()).thenReturn(1L);
        DataInitializer initializer = new DataInitializer(documentRepository, indexService, true);

        initializer.run(new DefaultApplicationArguments());

        verify(documentRepository).count();
        verify(documentRepository, never()).save(any());
        verify(indexService, never()).submitIndexTask(any(), any());
    }

    @Test
    void createsSampleDocumentsAndSubmitsIndexTasks() throws Exception {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        IndexService indexService = mock(IndexService.class);
        when(documentRepository.count()).thenReturn(0L);
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(document.getKbId() * 10);
            return document;
        });
        DataInitializer initializer = new DataInitializer(documentRepository, indexService, true);

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(documentRepository, org.mockito.Mockito.times(3)).save(documentCaptor.capture());
        assertThat(documentCaptor.getAllValues())
                .extracting(KbDocument::getKbId)
                .containsExactly(1L, 2L, 3L);
        assertThat(documentCaptor.getAllValues())
                .extracting(KbDocument::getFileName)
                .containsExactly("employee-handbook.txt", "tech-specification.txt", "product-faq.txt");
        assertThat(documentCaptor.getAllValues())
                .allSatisfy(document -> {
                    assertThat(document.getFileType()).isEqualTo("TXT");
                    assertThat(document.getFileSize()).isPositive();
                    assertThat(document.getUploadedBy()).isEqualTo(document.getKbId());
                });

        verify(indexService).submitIndexTask(org.mockito.Mockito.eq(10L), org.mockito.Mockito.contains("员工"));
        verify(indexService).submitIndexTask(org.mockito.Mockito.eq(20L), org.mockito.Mockito.contains("技术"));
        verify(indexService).submitIndexTask(org.mockito.Mockito.eq(30L), org.mockito.Mockito.contains("产品"));
    }
}
