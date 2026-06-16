package com.trade.ragbase.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.trade.ragbase.common.ApiResponse;
import com.trade.ragbase.dto.DocumentUploadResponse;
import com.trade.ragbase.dto.IndexStatusResponse;
import com.trade.ragbase.dto.KnowledgeBaseCreateRequest;
import com.trade.ragbase.dto.KnowledgeBaseVO;
import com.trade.ragbase.entity.IndexTask;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.entity.KnowledgeBase;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.IndexTaskRepository;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.service.KnowledgeBaseService;
import com.trade.ragbase.service.MinioStorageService;
import com.trade.ragbase.service.PermissionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeBaseController {

    private final PermissionService permissionService;
    private final KnowledgeBaseService kbService;
    private final KbDocumentRepository documentRepository;
    private final IndexTaskRepository taskRepository;
    private final MinioStorageService minioService;

    public KnowledgeBaseController(
            PermissionService permissionService,
            KnowledgeBaseService kbService,
            KbDocumentRepository documentRepository,
            IndexTaskRepository taskRepository,
            MinioStorageService minioService) {
        this.permissionService = permissionService;
        this.kbService = kbService;
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.minioService = minioService;
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        return ApiResponse.ok(kbService.listAccessible());
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@RequestBody KnowledgeBaseCreateRequest request) {
        return ApiResponse.ok(kbService.create(request));
    }

    @PostMapping("/{kbId}/documents")
    public ApiResponse<DocumentUploadResponse> upload(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        KbDocument document = kbService.uploadDocument(kbId, file);
        return ApiResponse.ok(DocumentUploadResponse.submitted(document.getId(), document.getFileName()));
    }

    @GetMapping("/{kbId}/documents/{docId}/status")
    public ApiResponse<IndexStatusResponse> getStatus(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireRead(kbId);
        KbDocument document = documentRepository.findById(docId)
                .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));
        IndexTask latestTask = taskRepository.findTopByDocIdOrderByCreatedAtDesc(docId).orElse(null);

        IndexStatusResponse response = new IndexStatusResponse();
        response.setDocId(document.getId());
        response.setFileName(document.getFileName());
        response.setStatus(document.getStatus().name());
        response.setErrorMsg(document.getErrorMsg());
        response.setChunkCount(document.getChunkCount());
        response.setTokenCount(document.getTokenCount());
        response.setIndexedAt(document.getIndexedAt() == null ? null : document.getIndexedAt().toString());
        response.setRetryCount(latestTask == null ? 0 : latestTask.getRetryCount());
        return ApiResponse.ok(response);
    }

    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<KbDocument>> listDocuments(@PathVariable Long kbId) {
        permissionService.requireRead(kbId);
        return ApiResponse.ok(documentRepository.findByKbIdAndIsDeletedFalse(kbId));
    }

    @DeleteMapping("/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        kbService.deleteDocument(docId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{kbId}/documents/{docId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireRead(kbId);
        KbDocument document = documentRepository.findById(docId)
                .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));
        byte[] content = minioService.download(document.getMinioPath());
        String encodedName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @PostMapping("/{kbId}/documents/{docId}/reindex")
    public ApiResponse<String> reindex(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        kbService.reindex(docId);
        return ApiResponse.ok("重建索引任务已提交，请通过 /status 接口查询进度");
    }
}
