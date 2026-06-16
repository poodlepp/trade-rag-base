package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.trade.ragbase.config.MinioProperties;

class MinioStorageServiceTest {

    @Test
    void uploadCreatesBucketWhenMissingAndReturnsKbScopedObjectPath() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MinioStorageService storageService = new MinioStorageService(minioClient, properties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", "text/plain", "hello".getBytes());

        String objectPath = storageService.upload(7L, file);

        assertThat(objectPath).startsWith("kb/7/");
        assertThat(objectPath).endsWith("-guide.txt");
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        ArgumentCaptor<PutObjectArgs> putArgs = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putArgs.capture());
        assertThat(putArgs.getValue().bucket()).isEqualTo("trade-rag");
        assertThat(putArgs.getValue().object()).isEqualTo(objectPath);
        assertThat(putArgs.getValue().contentType().toString()).isEqualTo("text/plain");
    }

    @Test
    void downloadReadsObjectBytes() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        GetObjectResponse response = new GetObjectResponse(
                Headers.of(), "trade-rag", null, "kb/1/a.txt", new ByteArrayInputStream("content".getBytes()));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);
        MinioStorageService storageService = new MinioStorageService(minioClient, properties());

        byte[] bytes = storageService.download("kb/1/a.txt");

        assertThat(new String(bytes)).isEqualTo("content");
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void deleteRemovesObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioStorageService storageService = new MinioStorageService(minioClient, properties());

        storageService.delete("kb/1/a.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    private static MinioProperties properties() {
        return new MinioProperties("http://localhost:9000", "minioadmin", "minioadmin", "trade-rag");
    }
}
