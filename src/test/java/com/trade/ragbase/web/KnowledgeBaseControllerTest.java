package com.trade.ragbase.web;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.trade.ragbase.config.GlobalExceptionHandler;
import com.trade.ragbase.dto.KnowledgeBaseVO;
import com.trade.ragbase.entity.IndexTask;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.IndexTaskRepository;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.service.KnowledgeBaseService;
import com.trade.ragbase.service.MinioStorageService;
import com.trade.ragbase.service.PermissionService;

class KnowledgeBaseControllerTest {

    @Test
    void listReturnsAccessibleKnowledgeBases() throws Exception {
        KnowledgeBaseService kbService = mock(KnowledgeBaseService.class);
        MockMvc mockMvc = mockMvc(kbService);
        when(kbService.listAccessible()).thenReturn(List.of(KnowledgeBaseVO.builder()
                .id(1L)
                .name("技术知识库")
                .permission("ADMIN")
                .build()));

        mockMvc.perform(get("/api/v1/kb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].name", is("技术知识库")))
                .andExpect(jsonPath("$.data[0].permission", is("ADMIN")));
    }

    @Test
    void uploadDocumentRequiresWriteAndReturnsSubmittedResponse() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        KnowledgeBaseService kbService = mock(KnowledgeBaseService.class);
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setFileName("demo.txt");
        when(kbService.uploadDocument(any(), any())).thenReturn(document);
        MockMvc mockMvc = mockMvc(permissionService, kbService);

        mockMvc.perform(multipart("/api/v1/kb/2/documents")
                        .file("file", "hello".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.docId", is(9)))
                .andExpect(jsonPath("$.data.status", is("PENDING")));
        verify(permissionService).requireWrite(2L);
    }

    @Test
    void getStatusReturnsLatestTaskRetryCount() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        IndexTaskRepository taskRepository = mock(IndexTaskRepository.class);
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setFileName("demo.txt");
        document.setStatus(KbDocument.DocumentStatus.DONE);
        document.setChunkCount(3);
        document.setTokenCount(120);
        document.setIndexedAt(LocalDateTime.of(2026, 6, 16, 12, 0));
        IndexTask task = new IndexTask();
        task.setRetryCount(2);
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        when(taskRepository.findTopByDocIdOrderByCreatedAtDesc(9L)).thenReturn(Optional.of(task));
        MockMvc mockMvc = mockMvc(permissionService, mock(KnowledgeBaseService.class),
                documentRepository, taskRepository, mock(MinioStorageService.class));

        mockMvc.perform(get("/api/v1/kb/2/documents/9/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DONE")))
                .andExpect(jsonPath("$.data.retryCount", is(2)));
        verify(permissionService).requireRead(2L);
    }

    @Test
    void downloadReturnsAttachment() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setFileName("demo file.txt");
        document.setMinioPath("kb/2/demo.txt");
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        when(minioStorageService.download("kb/2/demo.txt")).thenReturn("hello".getBytes());
        MockMvc mockMvc = mockMvc(permissionService, mock(KnowledgeBaseService.class),
                documentRepository, mock(IndexTaskRepository.class), minioStorageService);

        mockMvc.perform(get("/api/v1/kb/2/documents/9/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''demo%20file.txt"));
        verify(permissionService).requireRead(2L);
    }

    @Test
    void deleteDocumentRequiresWrite() throws Exception {
        PermissionService permissionService = mock(PermissionService.class);
        KnowledgeBaseService kbService = mock(KnowledgeBaseService.class);
        MockMvc mockMvc = mockMvc(permissionService, kbService);

        mockMvc.perform(delete("/api/v1/kb/2/documents/9"))
                .andExpect(status().isOk());
        verify(permissionService).requireWrite(2L);
        verify(kbService).deleteDocument(9L);
    }

    private MockMvc mockMvc(KnowledgeBaseService kbService) {
        return mockMvc(mock(PermissionService.class), kbService);
    }

    private MockMvc mockMvc(PermissionService permissionService, KnowledgeBaseService kbService) {
        return mockMvc(permissionService, kbService, mock(KbDocumentRepository.class),
                mock(IndexTaskRepository.class), mock(MinioStorageService.class));
    }

    private MockMvc mockMvc(
            PermissionService permissionService,
            KnowledgeBaseService kbService,
            KbDocumentRepository documentRepository,
            IndexTaskRepository taskRepository,
            MinioStorageService minioStorageService) {
        return MockMvcBuilders.standaloneSetup(new KnowledgeBaseController(
                        permissionService, kbService, documentRepository, taskRepository, minioStorageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
