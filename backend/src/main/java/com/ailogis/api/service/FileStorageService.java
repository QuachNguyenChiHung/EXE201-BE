package com.ailogis.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    /**
     * Upload file lên AWS S3 và trả về URL Public
     */
    public String storeFile(MultipartFile file, String subFolder) {
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        try {
            // 1. Tạo tên file duy nhất (UUID) để tránh trùng lặp
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String uniqueFileName = subFolder + "/" + UUID.randomUUID().toString() + fileExtension;

            // 2. Build cấu hình gửi file (PutObjectRequest)
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uniqueFileName)
                    .contentType(file.getContentType())
                    .build();

            // 3. Thực thi việc đẩy file lên S3
            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            // 4. Trình bày URL để trả về cho Database lưu trữ
            // Format URL của S3: https://{bucketName}.s3.{region}.amazonaws.com/{key}
            String publicUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, uniqueFileName);

            log.info("✅ Upload thành công lên S3: {}", publicUrl);
            return publicUrl;

        } catch (IOException ex) {
            log.error("Lỗi khi đọc file đính kèm: {}", ex.getMessage());
            throw new RuntimeException("Không thể đọc file " + originalFileName + " để upload!", ex);
        } catch (Exception ex) {
            log.error("Lỗi từ AWS S3: {}", ex.getMessage());
            throw new RuntimeException("Lỗi máy chủ khi upload lên Cloud!", ex);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            if (fileUrl != null && fileUrl.contains(".amazonaws.com/")) {
                // Tách URL để lấy chính xác Key của file (VD: "pdfs/uuid.pdf")
                String[] parts = fileUrl.split("\\.amazonaws\\.com/");
                if (parts.length == 2) {
                    String key = parts[1];
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build());
                    log.info("🗑Đã xóa file cũ trên S3 thành công: {}", key);
                }
            }
        } catch (Exception ex) {
            log.error("Lỗi khi xóa file trên S3: {}", ex.getMessage());
        }
    }
}