package org.hvtra.fileservice.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.model.entity.FileMetadata;
import org.hvtra.fileservice.model.entity.FileReferences;
import org.hvtra.fileservice.model.entity.GeneratedReportFile;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.response.AiReportFileResponse;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.repository.FileReferenceRepository;
import org.hvtra.fileservice.repository.GeneratedReportFileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class GeneratedReportFileService {
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final FileMetadataRepository files;
    private final FileReferenceRepository references;
    private final GeneratedReportFileRepository exports;
    private final MinioClient minio;
    private final MinioProperties properties;
    private final AiReportDocumentValidator validator;
    private final TransactionTemplate transactions;

    public GeneratedReportFileService(FileMetadataRepository files, FileReferenceRepository references,
            GeneratedReportFileRepository exports, MinioClient minio, MinioProperties properties,
            AiReportDocumentValidator validator, PlatformTransactionManager transactionManager) {
        this.files = files; this.references = references; this.exports = exports;
        this.minio = minio; this.properties = properties; this.validator = validator;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AiReportFileResponse store(UUID reportId, UUID ownerId, String filename, String checksum, byte[] bytes) {
        if (ownerId == null || filename == null || !filename.matches("AI_Report_HuongVanTra_\\d{8}_\\d{8}\\.xlsx")
                || checksum == null || !checksum.matches("[0-9a-f]{64}")) throw error(HttpStatus.BAD_REQUEST, "Invalid generated report metadata");
        validator.validate(bytes, "XLSX");
        if (!checksum.equals(sha256(bytes))) throw error(HttpStatus.CONFLICT, "Generated report checksum mismatch");
        // Reservation commits before the storage operation. A lost response retries
        // the same file and object; different bytes/actor/filename are a conflict.
        try {
            transactions.executeWithoutResult(status -> {
                GeneratedReportFile existing = exports.findById(reportId).orElse(null);
                if (existing != null) { verify(existing, ownerId, filename, checksum); return; }
                if (files.existsById(reportId)) throw error(HttpStatus.CONFLICT, "File identity already exists");
                GeneratedReportFile reservation = new GeneratedReportFile();
                reservation.setReportId(reportId); reservation.setOwnerId(ownerId);
                reservation.setFileName(filename); reservation.setSha256(checksum);
                exports.saveAndFlush(reservation);
                FileMetadata file = FileMetadata.builder().id(reportId).ownerId(ownerId).ownerType("AI_REPORT_GENERATED")
                        .bucket(properties.getBuckets().getPrivateBucket()).objectKey("ai-report-generated/" + reportId + ".xlsx")
                        .originalFilename(filename).contentType(MIME).extension("xlsx").sizeBytes((long) bytes.length)
                        .purpose(FilePurpose.USER_DOCUMENT).visibility(FileVisibility.PRIVATE).status(FileStatus.PENDING)
                        .refId(reportId).build();
                file.setCreatedBy(ownerId); file.setUpdatedBy(ownerId);
                files.saveAndFlush(file);
            });
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "Concurrent export reservation; retry same request");
        }
        return transactions.execute(status -> {
            FileMetadata file = files.findForUpdate(reportId).orElseThrow(() -> error(HttpStatus.CONFLICT, "Missing reservation"));
            GeneratedReportFile reservation = exports.findById(reportId).orElseThrow();
            verify(reservation, ownerId, filename, checksum);
            if (Boolean.TRUE.equals(file.getIsDeleted()) || file.getVisibility() != FileVisibility.PRIVATE
                    || !Objects.equals(file.getOwnerId(), ownerId) || !"AI_REPORT_GENERATED".equals(file.getOwnerType())
                    || !Objects.equals(file.getSizeBytes(), (long) bytes.length)) throw error(HttpStatus.CONFLICT, "Generated file unavailable");
            if (file.getStatus() != FileStatus.PENDING && file.getStatus() != FileStatus.ACTIVE)
                throw error(HttpStatus.CONFLICT, "Generated file unavailable");
            try {
                // Recover a PUT that succeeded before DB finalization failed.
                String etag;
                try {
                    var stat = minio.statObject(StatObjectArgs.builder().bucket(file.getBucket()).object(file.getObjectKey()).build());
                    if (stat.size() != bytes.length || !checksum.equals(stat.userMetadata().getFirst("sha256")))
                        throw error(HttpStatus.CONFLICT, "Generated object differs from reserved content");
                    etag = stat.etag();
                } catch (ErrorResponseException missing) {
                    if (!"NoSuchKey".equals(missing.errorResponse().code()) && !"NoSuchObject".equals(missing.errorResponse().code())) throw missing;
                    if (file.getStatus() == FileStatus.ACTIVE) throw error(HttpStatus.CONFLICT, "Generated object missing");
                    var result = minio.putObject(PutObjectArgs.builder().bucket(file.getBucket()).object(file.getObjectKey())
                            .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).contentType(MIME)
                            .userMetadata(Map.of("sha256", checksum)).build());
                    etag = result.etag();
                }
                if (file.getStatus() == FileStatus.ACTIVE && !Objects.equals(file.getEtag(), etag))
                    throw error(HttpStatus.CONFLICT, "Generated object changed");
                file.setEtag(etag); file.setStatus(FileStatus.ACTIVE); files.save(file);
                var bindings = references.findByFile_IdAndIsDeletedFalse(reportId);
                if (bindings.isEmpty()) {
                    FileReferences binding = FileReferences.builder().file(file).refType("AI_REPORT")
                            .refId(reportId).usageType("GENERATED").build();
                    binding.setCreatedBy(ownerId); binding.setUpdatedBy(ownerId); references.saveAndFlush(binding);
                } else if (bindings.size() != 1 || !"GENERATED".equals(bindings.getFirst().getUsageType())
                        || !"AI_REPORT".equals(bindings.getFirst().getRefType()) || !reportId.equals(bindings.getFirst().getRefId())) {
                    throw error(HttpStatus.CONFLICT, "Generated binding differs");
                }
                return new AiReportFileResponse(file.getId(), ownerId, filename, "XLSX", MIME, file.getSizeBytes(),
                        "ACTIVE", "PRIVATE", false);
            } catch (ResponseStatusException exception) { throw exception; }
            catch (Exception exception) { throw error(HttpStatus.BAD_GATEWAY, "Generated storage unavailable; retry same export"); }
        });
    }

    private void verify(GeneratedReportFile existing, UUID owner, String name, String sha) {
        if (!owner.equals(existing.getOwnerId()) || !name.equals(existing.getFileName()) || !sha.equals(existing.getSha256()))
            throw error(HttpStatus.CONFLICT, "Export identity belongs to different content or owner");
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
