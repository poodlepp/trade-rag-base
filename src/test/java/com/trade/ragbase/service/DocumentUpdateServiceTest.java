package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.KbDocumentRepository;

class DocumentUpdateServiceTest {

    @Test
    void replaceDocumentKeepsDocumentIdAndSubmitsIndexForNewVersion() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        MinioStorageService minioService = mock(MinioStorageService.class);
        IndexService indexService = mock(IndexService.class);
        DocumentUpdateService service = new DocumentUpdateService(documentRepository, minioService, indexService);
        KbDocument document = document();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(minioService.upload(any(), any())).thenReturn("kb/2/new-guide.pdf");

        KbDocument updated = service.replaceDocument(9L,
                new MockMultipartFile("file", "new-guide.pdf", "application/pdf", "new".getBytes()));

        assertThat(updated.getId()).isEqualTo(9L);
        assertThat(updated.getFileName()).isEqualTo("new-guide.pdf");
        assertThat(updated.getFileType()).isEqualTo("PDF");
        assertThat(updated.getFileSize()).isEqualTo(3L);
        assertThat(updated.getMinioPath()).isEqualTo("kb/2/new-guide.pdf");
        assertThat(updated.getVersion()).isEqualTo(4);
        assertThat(updated.getStatus()).isEqualTo(KbDocument.DocumentStatus.PENDING);
        assertThat(updated.getErrorMsg()).isNull();
        verify(indexService).submitIndexTask(9L);
        verify(minioService).delete("kb/2/old-guide.txt");
    }

    @Test
    void forceReindexIncrementsVersionWithoutReplacingFile() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        DocumentUpdateService service = new DocumentUpdateService(
                documentRepository, mock(MinioStorageService.class), mock(IndexService.class));
        KbDocument document = document();
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.forceReindexAndSubmit(9L);

        assertThat(document.getVersion()).isEqualTo(4);
        assertThat(document.getStatus()).isEqualTo(KbDocument.DocumentStatus.PENDING);
        assertThat(document.getErrorMsg()).isNull();
    }

    private KbDocument document() {
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setKbId(2L);
        document.setFileName("old-guide.txt");
        document.setFileType("TXT");
        document.setFileSize(10L);
        document.setMinioPath("kb/2/old-guide.txt");
        document.setVersion(3);
        document.setStatus(KbDocument.DocumentStatus.FAILED);
        document.setErrorMsg("old error");
        document.setUploadedBy(7L);
        return document;
    }
}
