package org.hvtra.fileservice.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import io.minio.Http.Method;
import org.hvtra.commonservice.exception.AppException;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.model.entity.FileMetadata;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.model.response.InitUploadResponse;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final FileMetadataRepository fileRepository;

    public InitUploadResponse initUpload(InitUploadRequest request, UUID userId) {
        validateUploadRequest(request);

        UUID fileId = UUID.randomUUID();
        String extension = extractExtension(request.originalFilename());
        String objectKey = buildObjectKey(userId, fileId, extension);

        FileMetadata metadata = FileMetadata.builder()
                .id(fileId)
                .bucket(minioProperties.getBucket())
                .objectKey(objectKey)
                .originalFilename(request.originalFilename())
                .contentType(request.contentType())
                .extension(extension)
                .sizeBytes(request.sizeBytes())
                .ownerId(userId)
                .ownerType("USER")
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.PENDING)
                .build();

        metadata = fileRepository.save(metadata);

        String uploadUrl = createUploadUrl(objectKey);

        return new InitUploadResponse(
                fileId,
                objectKey,
                uploadUrl,
                minioProperties.getPresignedUploadExpirySeconds()
        );
    }

    private String createUploadUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry(minioProperties.getPresignedUploadExpirySeconds())
                            .build()
            );
        } catch (Exception e) {
            throw new AppException(7001, "Cannot create upload URL");
        }
    }

    public void completeUpload(UUID fileId, UUID userId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(7001, "File not found"));

        if (!file.getOwnerId().equals(userId)) {
            throw new AppException(7009, "File is not available");
        }

        if (file.getStatus() != FileStatus.PENDING) {
            return;
        }

        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(file.getBucket())
                            .object(file.getObjectKey())
                            .build()
            );

//            if (stat.size() != file.getSizeBytes()) {
//                file.setStatus(FileStatus.FAILED);
//                fileRepository.save(file);
//                throw new AppException(7867, "Uploaded file size does not match");
//            }

            file.setEtag(stat.etag());
            file.setStatus(FileStatus.ACTIVE);

            fileRepository.save(file);

        } catch (Exception e) {
            file.setStatus(FileStatus.FAILED);
            fileRepository.save(file);
            throw new AppException(7007, "Cannot complete upload");
        }
    }

    public String createDownloadUrl(UUID fileId, UUID userId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(7000, "File not found"));

        if (file.getIsDeleted() || file.getStatus() != FileStatus.ACTIVE) {
            throw new AppException(7009, "File is not available");
        }

        checkDownloadPermission(file, userId);

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(file.getBucket())
                            .object(file.getObjectKey())
                            .expiry(minioProperties.getPresignedDownloadExpirySeconds())
                            .build()
            );
        } catch (Exception e) {
            throw new AppException(8003,"Cannot create download URL");
        }
    }
    private void validateUploadRequest(InitUploadRequest request) {
        if (request.sizeBytes() <= 0) {
            throw new AppException(7004,"File is empty");
        }

        if (request.sizeBytes() > 10 * 1024 * 1024) {
            throw new AppException(7005, "File too large");
        }

        Set<String> allowed = Set.of(
                "image/png",
                "image/jpeg",
                "application/pdf"
        );

        if (!allowed.contains(request.contentType())) {
            throw new AppException(7006, "Unsupported content type");
        }
    }
    private String buildObjectKey(UUID userId, UUID fileId, String extension) {
        LocalDate now = LocalDate.now();

        return "users/%s/%d/%02d/%02d/%s%s".formatted(
                userId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                fileId,
                extension == null ? "" : "." + extension
        );
    }
//private String buildObjectKey(UUID userId, String extension) {
//    LocalDate now = LocalDate.now();
//
//    return "users/%s/%d/%02d/%02d/%s".formatted(
//            userId,
//            now.getYear(),
//            now.getMonthValue(),
//            now.getDayOfMonth(),
//            extension == null ? "" : "." + extension
//    );
//}

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }

        String ext = filename.substring(filename.lastIndexOf(".") + 1)
                .toLowerCase(Locale.ROOT);

        if (!ext.matches("[a-z0-9]{1,10}")) {
            throw new AppException(7003, "Invalid file extension");
        }

        return ext;
    }

    private void checkDownloadPermission(FileMetadata file, UUID userId) {
        if (file.getVisibility() == FileVisibility.PUBLIC) {
            return;
        }

        if (!file.getOwnerId().equals(userId)) {
            throw new AppException(7002, "Forbidden to access upload resource");
        }
    }
}
