package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.trade.ragbase.dto.KnowledgeBaseCreateRequest;
import com.trade.ragbase.dto.KnowledgeBaseVO;
import com.trade.ragbase.entity.DocChunk;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.entity.KbPermission;
import com.trade.ragbase.entity.KnowledgeBase;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.DocChunkRepository;
import com.trade.ragbase.repository.IndexTaskRepository;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.repository.KbPermissionRepository;
import com.trade.ragbase.repository.KnowledgeBaseRepository;
import com.trade.ragbase.security.UserContext;

class KnowledgeBaseServiceTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createKnowledgeBaseGrantsCreatorAdminPermission() {
        KnowledgeBaseRepository kbRepository = mock(KnowledgeBaseRepository.class);
        KbPermissionRepository permissionRepository = mock(KbPermissionRepository.class);
        KnowledgeBaseService service = service(kbRepository, permissionRepository);
        UserContext.set(42L, "TECH", "USER");
        when(kbRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase kb = invocation.getArgument(0);
            kb.setId(10L);
            return kb;
        });
        when(permissionRepository.save(any(KbPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setName("技术知识库");
        request.setDescription("技术文档");
        request.setDepartmentId("TECH");
        request.setIsPublic(false);

        KnowledgeBase saved = service.create(request);

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getCreatedBy()).isEqualTo(42L);
        verify(permissionRepository).save(org.mockito.Mockito.argThat(permission ->
                permission.getKbId().equals(10L)
                        && permission.getSubjectType().equals("USER")
                        && permission.getSubjectId().equals("42")
                        && permission.getPermission().equals("ADMIN")
                        && permission.getGrantedBy().equals(42L)));
    }

    @Test
    void listAccessibleReturnsAdminPermissionForAdminUser() {
        KnowledgeBaseRepository kbRepository = mock(KnowledgeBaseRepository.class);
        KbPermissionRepository permissionRepository = mock(KbPermissionRepository.class);
        KnowledgeBaseService service = service(kbRepository, permissionRepository);
        UserContext.set(1L, "TECH", "ADMIN");
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("公共知识库");
        kb.setDepartmentId("ALL");
        kb.setIsPublic(true);
        kb.setCreatedBy(1L);
        when(kbRepository.findByIsDeletedFalse()).thenReturn(List.of(kb));

        List<KnowledgeBaseVO> result = service.listAccessible();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPermission()).isEqualTo("ADMIN");
    }

    @Test
    void uploadDocumentStoresFileAndSubmitsIndexTask() {
        KbDocumentRepository documentRepository = mock(KbDocumentRepository.class);
        MinioStorageService minioService = mock(MinioStorageService.class);
        IndexService indexService = mock(IndexService.class);
        KnowledgeBaseService service = service(
                mock(KnowledgeBaseRepository.class),
                mock(KbPermissionRepository.class),
                documentRepository,
                mock(DocChunkRepository.class),
                mock(IndexTaskRepository.class),
                minioService,
                indexService);
        UserContext.set(7L, "TECH", "USER");
        when(minioService.upload(any(), any())).thenReturn("kb/2/demo.txt");
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(99L);
            return document;
        });

        KbDocument document = service.uploadDocument(
                2L,
                new MockMultipartFile("file", "demo.txt", "text/plain", "hello".getBytes()));

        assertThat(document.getId()).isEqualTo(99L);
        assertThat(document.getKbId()).isEqualTo(2L);
        assertThat(document.getFileType()).isEqualTo("TXT");
        assertThat(document.getUploadedBy()).isEqualTo(7L);
        verify(indexService).submitIndexTask(99L);
    }

    @Test
    void permissionServiceRejectsUserWithoutReadPermission() {
        KnowledgeBaseRepository kbRepository = mock(KnowledgeBaseRepository.class);
        KbPermissionRepository permissionRepository = mock(KbPermissionRepository.class);
        UserContext.set(8L, "HR", "USER");
        KnowledgeBase kb = new KnowledgeBase();
        kb.setIsPublic(false);
        when(kbRepository.findById(3L)).thenReturn(Optional.of(kb));
        PermissionService service = new PermissionService(permissionRepository, kbRepository);

        assertThatThrownBy(() -> service.requireRead(3L))
                .isInstanceOf(BizException.class)
                .hasMessage("无权访问该知识库");
    }

    private KnowledgeBaseService service(
            KnowledgeBaseRepository kbRepository,
            KbPermissionRepository permissionRepository) {
        return service(kbRepository, permissionRepository, mock(KbDocumentRepository.class),
                mock(DocChunkRepository.class), mock(IndexTaskRepository.class),
                mock(MinioStorageService.class), mock(IndexService.class));
    }

    private KnowledgeBaseService service(
            KnowledgeBaseRepository kbRepository,
            KbPermissionRepository permissionRepository,
            KbDocumentRepository documentRepository,
            DocChunkRepository chunkRepository,
            IndexTaskRepository taskRepository,
            MinioStorageService minioService,
            IndexService indexService) {
        return new KnowledgeBaseService(kbRepository, permissionRepository, documentRepository,
                chunkRepository, taskRepository, minioService, indexService);
    }
}
