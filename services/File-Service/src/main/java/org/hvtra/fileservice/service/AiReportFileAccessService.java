package org.hvtra.fileservice.service;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.model.entity.FileMetadata;
import org.hvtra.fileservice.model.entity.FileReferences;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.response.AiReportDownloadResponse;
import org.hvtra.fileservice.model.response.AiReportFileResponse;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.repository.FileReferenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AiReportFileAccessService {
    private final FileMetadataRepository files;
    private final FileReferenceRepository references;
    private final MinioClient minio;
    private final MinioProperties properties;
    private final AiReportDocumentValidator validator;

    public AiReportFileResponse metadata(UUID fileId, UUID ownerId) {
        FileMetadata file = ownedActive(fileId, ownerId);
        String type = type(file);
        validateObject(file, type);
        return new AiReportFileResponse(file.getId(), file.getOwnerId(), file.getOriginalFilename(),
                type, file.getContentType(), file.getSizeBytes(), file.getStatus().name(),
                file.getVisibility().name(), Boolean.TRUE.equals(file.getIsDeleted()));
    }

    public void bind(UUID reportId, UUID fileId, UUID ownerId) {
        FileMetadata file = ownedActive(fileId, ownerId);
        List<FileReferences> bindings = references.findByFile_IdAndIsDeletedFalse(fileId);
        if (!bindings.isEmpty()) {
            if (bindings.size() == 1 && matches(bindings.getFirst(), reportId)
                    && reportId.equals(file.getRefId())) return;
            throw error(HttpStatus.CONFLICT, "File already referenced");
        }
        if (file.getRefId() != null) throw error(HttpStatus.CONFLICT, "File already attached");
        if (references.existsByRefTypeAndRefIdAndUsageTypeAndIsDeletedFalseAndFile_IdNot(
                "AI_REPORT", reportId, "ORIGINAL", fileId)) throw error(HttpStatus.CONFLICT, "Report already has an original file");
        validateObject(file, type(file));
        FileReferences reference = FileReferences.builder().file(file).refType("AI_REPORT")
                .refId(reportId).usageType("ORIGINAL").build();
        // Service principals are not UUIDs; set human auditing explicitly.
        reference.setCreatedBy(ownerId);
        reference.setUpdatedBy(ownerId);
        try {
            references.saveAndFlush(reference);
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "Concurrent report binding conflict");
        }
        file.setRefId(reportId);
        files.save(file);
    }

    public AiReportDownloadResponse download(UUID reportId, UUID fileId) {
        FileMetadata file = active(fileId);
        List<FileReferences> bindings = references.findByFile_IdAndIsDeletedFalse(fileId);
        if (bindings.size() != 1 || !matches(bindings.getFirst(), reportId)
                || !reportId.equals(file.getRefId())) throw error(HttpStatus.FORBIDDEN, "Report binding mismatch");
        String type = type(file);
        validateObject(file, type);
        try {
            int ttl = Math.min(300, properties.getPresignedDownloadExpirySeconds());
            String url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(file.getBucket()).object(file.getObjectKey()).expiry(ttl).build());
            return new AiReportDownloadResponse(url, ttl, file.getOriginalFilename(),
                    file.getContentType(), file.getSizeBytes(), file.getEtag());
        } catch (Exception exception) {
            throw error(HttpStatus.BAD_GATEWAY, "Storage unavailable");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> orphanCandidates() {
        // Read-only hook, no deletion. Scope only explicitly marked AI Report uploads.
        return references.findOrphanCandidates(Instant.now().minus(24, ChronoUnit.HOURS), PageRequest.of(0, 100));
    }

    private boolean matches(FileReferences binding, UUID reportId) {
        return "AI_REPORT".equals(binding.getRefType()) && "ORIGINAL".equals(binding.getUsageType())
                && reportId.equals(binding.getRefId());
    }

    private FileMetadata ownedActive(UUID id, UUID ownerId) {
        FileMetadata file = active(id);
        if (!Objects.equals(ownerId, file.getOwnerId())) throw error(HttpStatus.FORBIDDEN, "File owner mismatch");
        return file;
    }

    private FileMetadata active(UUID id) {
        FileMetadata file = files.findForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "File not found"));
        if (Boolean.TRUE.equals(file.getIsDeleted()) || file.getStatus() != FileStatus.ACTIVE
                || file.getVisibility() != FileVisibility.PRIVATE || file.getPurpose() != FilePurpose.USER_DOCUMENT
                || file.getSizeBytes() == null || file.getSizeBytes() <= 0
                || file.getSizeBytes() > AiReportDocumentValidator.MAX_BYTES) {
            throw error(HttpStatus.CONFLICT, "File unavailable for AI Report");
        }
        return file;
    }

    private String type(FileMetadata file) {
        if ("pdf".equalsIgnoreCase(file.getExtension()) && "application/pdf".equals(file.getContentType())) return "PDF";
        if ("xlsx".equalsIgnoreCase(file.getExtension())
                && "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(file.getContentType())) return "XLSX";
        throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF/XLSX reports supported");
    }

    private void validateObject(FileMetadata file, String type) {
        try {
            var stat = minio.statObject(StatObjectArgs.builder().bucket(file.getBucket()).object(file.getObjectKey()).build());
            if (stat.size() != file.getSizeBytes() || file.getEtag() == null
                    || !file.getEtag().equals(stat.etag())) throw error(HttpStatus.CONFLICT, "Original file changed");
            try (var stream = minio.getObject(GetObjectArgs.builder().bucket(file.getBucket())
                    .object(file.getObjectKey()).extraHeaders(Map.of("If-Match", "\"" + file.getEtag() + "\"")).build())) {
                byte[] bytes = stream.readNBytes(AiReportDocumentValidator.MAX_BYTES + 1);
                if (bytes.length != file.getSizeBytes()) throw error(HttpStatus.CONFLICT, "Original file size mismatch");
                validator.validate(bytes, type);
            }
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception exception) {
            throw error(HttpStatus.BAD_GATEWAY, "Cannot validate original file");
        }
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
