package org.hvtra.fileservice.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.AppException;
import org.hvtra.fileservice.config.FileBucketResolver;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.model.entity.FileMetadata;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.model.response.CompleteUploadResponse;
import org.hvtra.fileservice.model.response.DownloadUrlResponse;
import org.hvtra.fileservice.model.response.InitUploadResponse;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final FileBucketResolver bucketResolver;
    private final FileMetadataRepository fileRepository;

    public InitUploadResponse initUpload(InitUploadRequest request, UUID currentUserId) {
        validateUploadRequest(request);

        UUID fileId = UUID.randomUUID();

        FileVisibility visibility = bucketResolver.resolveVisibility(request.purpose());
        String bucket = bucketResolver.resolveBucket(visibility);

        String extension = extractExtension(request.originalFilename());
        String objectKey = buildObjectKey(
                request.purpose(),
                request.refType(),
                request.refId(),
                currentUserId,
                fileId,
                extension
        );

        String publicUrl = null;

        if (visibility == FileVisibility.PUBLIC) {
            publicUrl = buildPublicUrl(bucket, objectKey);
        }

        FileMetadata file = FileMetadata.builder()
                .id(fileId)
                .bucket(bucket)
                .objectKey(objectKey)
                .publicUrl(publicUrl)
                .originalFilename(request.originalFilename())
                .contentType(request.contentType())
                .extension(extension)
                .sizeBytes(request.sizeBytes())
                .purpose(request.purpose())
                .visibility(visibility)
                .status(FileStatus.PENDING)
                .ownerId(request.ownerId() != null ? request.ownerId() : currentUserId)
                .ownerType(request.ownerType())
                .refType(request.refType())
                .refId(request.refId())
                .build();

        fileRepository.save(file);

        String uploadUrl = createPresignedUploadUrl(bucket, objectKey);

        return new InitUploadResponse(
                fileId,
                visibility,
                bucket,
                objectKey,
                uploadUrl,
                publicUrl,
                minioProperties.getPresignedUploadExpirySeconds()
        );
    }

    private String createPresignedUploadUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(minioProperties.getPresignedUploadExpirySeconds())
                            .build()
            );
        } catch (Exception e) {
            throw new AppException(8001, "Cannot create upload URL");
        }
    }


    private String buildPublicUrl(String bucket, String objectKey) {
        String base = minioProperties.getPublicEndpoint();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + "/" + bucket + "/" + objectKey;
    }


    public CompleteUploadResponse completeUpload(UUID fileId, UUID currentUserId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(7000, "File not found"));

        checkOwnerOrPermission(file, currentUserId);

        if (file.getStatus() == FileStatus.ACTIVE) {
            return new CompleteUploadResponse(
                    file.getId(),
                    file.getVisibility(),
                    file.getPublicUrl()
            );
        }

        if (file.getStatus() != FileStatus.PENDING) {
            throw new AppException(7008, "File is not pending");
        }

        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(file.getBucket())
                            .object(file.getObjectKey())
                            .build()
            );

            if (stat.size() != file.getSizeBytes()) {
                file.setStatus(FileStatus.FAILED);
                fileRepository.save(file);
                throw new AppException(7010, "Uploaded file size does not match");
            }

            file.setEtag(stat.etag());
            file.setStatus(FileStatus.ACTIVE);
            fileRepository.save(file);

            return new CompleteUploadResponse(
                    file.getId(),
                    file.getVisibility(),
                    file.getPublicUrl()
            );

        } catch (Exception e) {
            file.setStatus(FileStatus.FAILED);
            fileRepository.save(file);
            throw new AppException(8002, "Cannot complete upload");
        }
    }

    public DownloadUrlResponse createDownloadUrl(UUID fileId, UUID currentUserId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(7000, "File not found"));

        if (file.getIsDeleted() || file.getStatus() != FileStatus.ACTIVE) {
            throw new AppException(7009, "File is not available");
        }

        if (file.getVisibility() == FileVisibility.PUBLIC) {
            return new DownloadUrlResponse(
                    file.getPublicUrl(),
                    null,
                    FileVisibility.PUBLIC
            );
        }

        checkDownloadPermission(file, currentUserId);

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(file.getBucket())
                            .object(file.getObjectKey())
                            .expiry(minioProperties.getPresignedDownloadExpirySeconds())
                            .build()
            );

            return new DownloadUrlResponse(
                    url,
                    minioProperties.getPresignedDownloadExpirySeconds(),
                    FileVisibility.PRIVATE
            );

        } catch (Exception e) {
            throw new AppException(8003, "Cannot create download URL");
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


    private String buildObjectKey(
            FilePurpose purpose,
            String refType,
            UUID refId,
            UUID currentUserId,
            UUID fileId,
            String extension
    ) {
        String ext = extension == null ? "" : "." + extension;

        return switch (purpose) {
            case PRODUCT_IMAGE, ITEM_IMAGE -> {
                if (refId == null) {
                    throw new AppException(7001, "Product or item id is required");
                }
                yield "items/%s/%s%s".formatted(refId, fileId, ext);
            }

            case CATEGORY_IMAGE -> {
                if (refId == null) {
                    throw new AppException(7002, "Category id is required");
                }
                yield "categories/%s/%s%s".formatted(refId, fileId, ext);
            }

            case PROMOTION_IMAGE, BANNER_IMAGE -> {
                yield "public-assets/%s/%s%s".formatted(purpose.name().toLowerCase(), fileId, ext);
            }

            case USER_AVATAR -> {
                yield "users/%s/avatar/%s%s".formatted(currentUserId, fileId, ext);
            }

            case USER_DOCUMENT -> {
                yield "users/%s/documents/%s%s".formatted(currentUserId, fileId, ext);
            }

            case PAYMENT_PROOF -> {
                yield "payments/%s/proofs/%s%s".formatted(refId, fileId, ext);
            }

            case INVOICE -> {
                yield "invoices/%s%s".formatted(fileId, ext);
            }
        };
    }


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

    private void checkOwnerOrPermission(FileMetadata file, UUID currentUserId) {
        if (file.getOwnerId() != null && !file.getOwnerId().equals(currentUserId)) {
            throw new AppException(7003, "Forbidden");
        }
    }
}
