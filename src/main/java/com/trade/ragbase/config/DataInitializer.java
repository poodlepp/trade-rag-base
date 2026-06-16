package com.trade.ragbase.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.trade.ragbase.entity.KbDocument;
import com.trade.ragbase.repository.KbDocumentRepository;
import com.trade.ragbase.service.IndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 开发环境数据初始化器：导入 classpath 下的样本文档，并提交索引任务。
 */
@Component
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final KbDocumentRepository documentRepository;
    private final IndexService indexService;
    private final boolean enabled;

    public DataInitializer(
            KbDocumentRepository documentRepository,
            IndexService indexService,
            @Value("${rag.data-initializer.enabled:true}") boolean enabled) {
        this.documentRepository = documentRepository;
        this.indexService = indexService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("[DataInit] 数据初始化已关闭");
            return;
        }

        if (documentRepository.count() > 0) {
            log.info("[DataInit] 已有文档数据，跳过初始化");
            return;
        }

        log.info("[DataInit] 开始初始化测试文档...");
        initDocument(1L, "hr-handbook.txt", "employee-handbook.txt", "TXT", 1L, "test-docs/hr-handbook.txt");
        initDocument(2L, "tech-spec.txt", "tech-specification.txt", "TXT", 2L, "test-docs/tech-spec.txt");
        initDocument(3L, "product-faq.txt", "product-faq.txt", "TXT", 3L, "test-docs/product-faq.txt");
        log.info("[DataInit] 测试文档初始化完成，等待异步索引...");
    }

    private void initDocument(
            Long kbId,
            String minioPath,
            String fileName,
            String fileType,
            Long uploadedBy,
            String classpath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        byte[] content = resource.getInputStream().readAllBytes();

        KbDocument document = new KbDocument();
        document.setKbId(kbId);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSize((long) content.length);
        document.setMinioPath(minioPath);
        document.setUploadedBy(uploadedBy);
        KbDocument savedDocument = documentRepository.save(document);

        String text = new String(content, StandardCharsets.UTF_8);
        indexService.submitIndexTask(savedDocument.getId(), text);
        log.info("[DataInit] 文档已提交索引：id={}, fileName={}", savedDocument.getId(), fileName);
    }
}
