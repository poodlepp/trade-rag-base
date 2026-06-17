package com.trade.ragbase.service;

import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.KbDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class DocumentUpdateService {

    private final KbDocumentRepository documentRepository;
    private final MinioStorageService minioService;
    private final IndexService indexService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public DocumentUpdateService(
            KbDocumentRepository documentRepository,
            MinioStorageService minioService,
            IndexService indexService,
            PlatformTransactionManager transactionManager) {
        this(documentRepository, minioService, indexService, new TransactionTemplate(transactionManager));
    }

    DocumentUpdateService(
            KbDocumentRepository documentRepository,
            MinioStorageService minioService,
            IndexService indexService,
            TransactionTemplate transactionTemplate) {
        this.documentRepository = documentRepository;
        this.minioService = minioService;
        this.indexService = indexService;
        this.transactionTemplate = transactionTemplate;
    }

    public DocumentUpdateService(
            KbDocumentRepository documentRepository,
            MinioStorageService minioService,
            IndexService indexService) {
        this(documentRepository, minioService, indexService, (TransactionTemplate) null);
    }

    public KbDocument replaceDocument(Long docId, MultipartFile newFile) {
        String oldMinioPath = updateDocumentRecord(docId, newFile);

        indexService.submitIndexTask(docId);
        minioService.delete(oldMinioPath);

        return documentRepository.findById(docId)
                .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));
    }

    public String updateDocumentRecord(Long docId, MultipartFile newFile) {
        if (transactionTemplate == null) {
            return doUpdateDocumentRecord(docId, newFile);
        }
        return transactionTemplate.execute(status -> doUpdateDocumentRecord(docId, newFile));
    }

    public void forceReindexAndSubmit(Long docId) {
        forceReindex(docId);
        indexService.submitIndexTask(docId);
        log.info("[DocumentUpdate] 强制重建索引已提交：docId={}", docId);
    }

    public void forceReindex(Long docId) {
        Runnable operation = () -> {
            KbDocument document = documentRepository.findById(docId)
                    .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));
            document.setVersion(document.getVersion() + 1);
            document.setStatus(KbDocument.DocumentStatus.PENDING);
            document.setErrorMsg(null);
            documentRepository.save(document);
        };
        if (transactionTemplate == null) {
            operation.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> operation.run());
    }

    private String doUpdateDocumentRecord(Long docId, MultipartFile newFile) {
        KbDocument document = documentRepository.findById(docId)
                .orElseThrow(() -> BizException.notFound("文档不存在：" + docId));
        String fileName = newFile.getOriginalFilename();
        validateFileType(fileName);

        String oldMinioPath = document.getMinioPath();
        String newMinioPath = minioService.upload(document.getKbId(), newFile);

        document.setFileName(fileName);
        document.setFileType(detectFileType(fileName));
        document.setFileSize(newFile.getSize());
        document.setMinioPath(newMinioPath);
        document.setVersion(document.getVersion() + 1);
        document.setStatus(KbDocument.DocumentStatus.PENDING);
        document.setErrorMsg(null);
        documentRepository.save(document);

        log.info("[DocumentUpdate] 文档记录更新：docId={}，newVersion={}，newFile={}",
                docId, document.getVersion(), fileName);
        return oldMinioPath;
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
