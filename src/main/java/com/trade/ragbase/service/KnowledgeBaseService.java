package com.trade.ragbase.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.trade.ragbase.dto.KnowledgeBaseCreateRequest;
import com.trade.ragbase.dto.KnowledgeBaseVO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class KnowledgeBaseService {

    private static final Map<String, Integer> PERMISSION_LEVEL = Map.of(
            "READ", 1,
            "WRITE", 2,
            "ADMIN", 3);

    private final KnowledgeBaseRepository kbRepository;
    private final KbPermissionRepository permissionRepository;
    private final KbDocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final IndexTaskRepository taskRepository;
    private final MinioStorageService minioService;
    private final IndexService indexService;

    public KnowledgeBaseService(
            KnowledgeBaseRepository kbRepository,
            KbPermissionRepository permissionRepository,
            KbDocumentRepository documentRepository,
            DocChunkRepository chunkRepository,
            IndexTaskRepository taskRepository,
            MinioStorageService minioService,
            IndexService indexService) {
        this.kbRepository = kbRepository;
        this.permissionRepository = permissionRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.taskRepository = taskRepository;
        this.minioService = minioService;
        this.indexService = indexService;
    }

    @Transactional
    public KnowledgeBase create(KnowledgeBaseCreateRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setDepartmentId(request.getDepartmentId());
        knowledgeBase.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()));
        knowledgeBase.setCreatedBy(UserContext.getUserId());

        KnowledgeBase saved = kbRepository.save(knowledgeBase);

        KbPermission permission = new KbPermission();
        permission.setKbId(saved.getId());
        permission.setSubjectType("USER");
        permission.setSubjectId(String.valueOf(UserContext.getUserId()));
        permission.setPermission("ADMIN");
        permission.setGrantedBy(UserContext.getUserId());
        permissionRepository.save(permission);

        log.info("[KB] 知识库创建：id={}，name={}，creator={}",
                saved.getId(), saved.getName(), UserContext.getUserId());
        return saved;
    }

    public List<KnowledgeBaseVO> listAccessible() {
        if (UserContext.isAdmin()) {
            return kbRepository.findByIsDeletedFalse().stream()
                    .map(kb -> toVO(kb, "ADMIN"))
                    .toList();
        }

        String departmentId = UserContext.getDepartmentId();
        String userId = String.valueOf(UserContext.getUserId());
        Map<Long, String> permissionMap = new HashMap<>();

        permissionRepository.findBySubjectTypeAndSubjectId("DEPARTMENT", departmentId)
                .forEach(permission -> permissionMap.merge(
                        permission.getKbId(), permission.getPermission(), this::higherPermission));
        permissionRepository.findBySubjectTypeAndSubjectId("USER", userId)
                .forEach(permission -> permissionMap.merge(
                        permission.getKbId(), permission.getPermission(), this::higherPermission));

        Set<Long> accessibleIds = new HashSet<>(permissionMap.keySet());
        kbRepository.findByIsPublicTrueAndIsDeletedFalse().forEach(kb -> {
            accessibleIds.add(kb.getId());
            permissionMap.putIfAbsent(kb.getId(), "READ");
        });

        if (accessibleIds.isEmpty()) {
            return List.of();
        }

        return kbRepository.findAllById(accessibleIds).stream()
                .filter(kb -> !Boolean.TRUE.equals(kb.getIsDeleted()))
                .map(kb -> toVO(kb, permissionMap.getOrDefault(kb.getId(), "READ")))
                .toList();
    }

    @Transactional
    public KbDocument uploadDocument(Long kbId, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        validateFileType(fileName);

        String minioPath = minioService.upload(kbId, file);

        KbDocument document = new KbDocument();
        document.setKbId(kbId);
        document.setFileName(fileName);
        document.setFileType(detectFileType(fileName));
        document.setFileSize(file.getSize());
        document.setMinioPath(minioPath);
        document.setUploadedBy(UserContext.getUserId());
        KbDocument saved = documentRepository.save(document);

        indexService.submitIndexTask(saved.getId());
        log.info("[KB] 文档上传：docId={}，fileName={}，kbId={}", saved.getId(), fileName, kbId);
        return saved;
    }

    @Transactional
    public void deleteDocument(Long docId) {
        KbDocument document = documentRepository.findById(docId)
                .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));

        document.setIsDeleted(true);
        documentRepository.save(document);
        chunkRepository.deleteByDocId(docId);
        minioService.delete(document.getMinioPath());

        log.info("[KB] 文档删除：docId={}，fileName={}", docId, document.getFileName());
    }

    @Transactional
    public void reindex(Long docId) {
        KbDocument document = documentRepository.findById(docId)
                .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));

        document.setVersion(document.getVersion() + 1);
        document.setStatus(KbDocument.DocumentStatus.PENDING);
        document.setErrorMsg(null);
        documentRepository.save(document);

        indexService.submitIndexTask(docId);
        log.info("[KB] 触发重建索引：docId={}，newVersion={}", docId, document.getVersion());
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb, String permission) {
        return KnowledgeBaseVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .departmentId(kb.getDepartmentId())
                .isPublic(kb.getIsPublic())
                .createdBy(kb.getCreatedBy())
                .createdAt(kb.getCreatedAt())
                .permission(permission)
                .build();
    }

    private String higherPermission(String left, String right) {
        return PERMISSION_LEVEL.getOrDefault(left, 0) >= PERMISSION_LEVEL.getOrDefault(right, 0)
                ? left : right;
    }

    private void validateFileType(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw BizException.badRequest("文件名不能为空");
        }
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".pdf")
                && !lower.endsWith(".docx")
                && !lower.endsWith(".md")
                && !lower.endsWith(".txt")) {
            throw BizException.badRequest("不支持的文件类型，目前支持：PDF、DOCX、MD、TXT");
        }
    }

    private String detectFileType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".docx")) {
            return "DOCX";
        }
        if (lower.endsWith(".md")) {
            return "MD";
        }
        if (lower.endsWith(".txt")) {
            return "TXT";
        }
        return "UNKNOWN";
    }
}
