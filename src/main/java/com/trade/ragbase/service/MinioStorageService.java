package com.trade.ragbase.service;

import java.io.InputStream;
import java.util.UUID;

import com.trade.ragbase.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.bucket();
    }

    public String upload(Long kbId, MultipartFile file) {
        String objectPath = String.format("kb/%d/%s-%s",
                kbId, UUID.randomUUID().toString().substring(0, 8), file.getOriginalFilename());
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(file.getInputStream(), file.getSize(), -1L)
                    .contentType(file.getContentType())
                    .build());
            log.info("[MinIO] 上传成功：path={}", objectPath);
            return objectPath;
        } catch (Exception exception) {
            log.error("[MinIO] 上传失败：path={}，error={}", objectPath, exception.getMessage(), exception);
            throw new IllegalStateException("文件上传失败：" + exception.getMessage(), exception);
        }
    }

    public byte[] download(String objectPath) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectPath)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            log.error("[MinIO] 下载失败：path={}，error={}", objectPath, exception.getMessage(), exception);
            throw new IllegalStateException("文件下载失败：" + exception.getMessage(), exception);
        }
    }

    public void delete(String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            log.info("[MinIO] 删除成功：path={}", objectPath);
        } catch (Exception exception) {
            log.warn("[MinIO] 删除失败（可能已不存在）：path={}，error={}", objectPath, exception.getMessage());
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[MinIO] Bucket 已创建：{}", bucket);
        }
    }
}
