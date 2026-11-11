package com.example.backend.Service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.presigned-url-expiration-minutes:10}")
    private long presignedUrlExpirationMinutes;

    public String uploadFile(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename(); // 파일명 중복 방지

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        // ACL을 사용하지 않고 비공개 객체로 업로드한다.
        amazonS3.putObject(new PutObjectRequest(bucket, fileName, file.getInputStream(), metadata));

        // DB에는 만료되는 URL 대신 안정적인 S3 object key를 저장한다.
        return fileName;
    }

    public String generatePresignedGetUrl(String objectKeyOrUrl) {
        String objectKey = extractObjectKey(objectKeyOrUrl);
        Date expiration = new Date(
                System.currentTimeMillis() + presignedUrlExpirationMinutes * 60_000L
        );

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);

        return amazonS3.generatePresignedUrl(request).toString();
    }

    private String extractObjectKey(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            throw new IllegalArgumentException("S3 object key is required.");
        }

        if (!objectKeyOrUrl.startsWith("http://") && !objectKeyOrUrl.startsWith("https://")) {
            return objectKeyOrUrl;
        }

        try {
            String path = URI.create(objectKeyOrUrl).getPath();
            String key = path.startsWith("/") ? path.substring(1) : path;
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid S3 object URL.", e);
        }
    }
}
