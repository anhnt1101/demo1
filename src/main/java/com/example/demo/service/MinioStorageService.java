package com.example.demo.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${export.presigned-url-expiry-minutes:30}")
    private int expiryMinutes;

    public void upload(Path file, String objectKey) throws Exception {
        ensureBucket();
        minioClient.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(objectKey).filename(file.toString()).contentType(XLSX_CONTENT_TYPE).build());
    }

    public String createDownloadUrl(String objectKey) throws Exception {
        /*
         * MinIO 9.x dùng io.minio.Http.Method.
         */
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET).bucket(bucket).object(objectKey).expiry(expiryMinutes * 60).build());
    }

    public LocalDateTime calculateExpiredAt() {
        return LocalDateTime.now().plusMinutes(expiryMinutes);
    }

    public void deleteQuietly(String objectKey) {

        if (objectKey == null) {
            return;
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ignored) {
        }
    }


    private void ensureBucket() throws Exception {

        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());

        if (!exists) {

            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}