package com.trade.ragbase.web;

import com.trade.ragbase.common.ApiResponse;
import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.service.DocumentUpdateService;
import com.trade.ragbase.service.PermissionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kb")
public class DocumentUpdateController {

    private final PermissionService permissionService;
    private final DocumentUpdateService documentUpdateService;

    public DocumentUpdateController(
            PermissionService permissionService,
            DocumentUpdateService documentUpdateService) {
        this.permissionService = permissionService;
        this.documentUpdateService = documentUpdateService;
    }

    @PutMapping("/{kbId}/documents/{docId}/content")
    public ApiResponse<KbDocument> replaceContent(
            @PathVariable Long kbId,
            @PathVariable Long docId,
            @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        return ApiResponse.ok(documentUpdateService.replaceDocument(docId, file));
    }

    @PostMapping("/{kbId}/documents/{docId}/reindex-force")
    public ApiResponse<Void> forceReindex(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        documentUpdateService.forceReindexAndSubmit(docId);
        return ApiResponse.ok(null);
    }
}
