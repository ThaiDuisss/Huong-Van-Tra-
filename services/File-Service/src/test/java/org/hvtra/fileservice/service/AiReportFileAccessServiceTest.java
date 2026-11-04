package org.hvtra.fileservice.service;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.fileservice.config.FileBucketResolver;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.model.entity.FileMetadata;
import org.hvtra.fileservice.model.entity.FileReferences;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.request.FileAttach;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.repository.FileReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiReportFileAccessServiceTest {
    @Mock FileMetadataRepository files;
    @Mock FileReferenceRepository references;
    @Mock MinioClient minio;
    @Mock FileBucketResolver buckets;
    private final UUID owner = UUID.randomUUID();
    private final UUID otherStaff = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private FileMetadata file;
    private MinioProperties properties;
    private AiReportFileAccessService service;

    @BeforeEach
    void setUp() {
        properties = new MinioProperties();
        file = FileMetadata.builder().id(UUID.randomUUID()).ownerId(owner).purpose(FilePurpose.USER_DOCUMENT)
                .visibility(FileVisibility.PRIVATE).status(FileStatus.ACTIVE).sizeBytes(15L)
                .extension("pdf").contentType("application/pdf").originalFilename("Báo cáo.pdf")
                .bucket("private").objectKey("original.pdf").etag("original-etag").build();
        service = new AiReportFileAccessService(files, references, minio, properties, new AiReportDocumentValidator());
    }

    void objectAvailable() throws Exception {
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(15L);
        when(stat.etag()).thenReturn("original-etag");
        when(minio.statObject(any())).thenReturn(stat);
        GetObjectResponse stream = mock(GetObjectResponse.class);
        when(stream.readNBytes(anyInt())).thenReturn("%PDF-1.7\nreport".getBytes(StandardCharsets.UTF_8));
        when(minio.getObject(any())).thenReturn(stream);
    }

    FileReferences binding(UUID id) {
        return FileReferences.builder().file(file).refType("AI_REPORT").refId(id).usageType("ORIGINAL").build();
    }

    @Test
    void trustedMetadataChecksActualOwnerAndBytes() throws Exception {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        objectAvailable();
        var result = service.metadata(file.getId(), owner);
        assertEquals(owner, result.ownerId());
        assertEquals("PDF", result.fileType());
        assertEquals("Báo cáo.pdf", result.fileName());
        verify(minio).getObject(any());
    }

    @Test
    void wrongOwnerCannotReadOrBind() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.metadata(file.getId(), otherStaff)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.bind(reportId, file.getId(), otherStaff)).getStatusCode().value());
        verifyNoInteractions(minio, references);
    }

    @Test
    void bindingIsTypedAndIdempotent() throws Exception {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of(), List.of(binding(reportId)));
        objectAvailable();
        service.bind(reportId, file.getId(), owner);
        service.bind(reportId, file.getId(), owner);
        ArgumentCaptor<FileReferences> captor = ArgumentCaptor.forClass(FileReferences.class);
        verify(references, times(1)).saveAndFlush(captor.capture());
        assertEquals("AI_REPORT", captor.getValue().getRefType());
        assertEquals(reportId, captor.getValue().getRefId());
        assertEquals(owner, captor.getValue().getCreatedBy());
        assertEquals(reportId, file.getRefId());
    }

    @Test
    void missingTypedReferenceCanBeRecoveredOnlyForMatchingReportId() throws Exception {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of());
        file.setRefId(reportId);
        objectAvailable();
        service.bind(reportId, file.getId(), owner);
        verify(references).saveAndFlush(any());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.bind(UUID.randomUUID(), file.getId(), owner)).getStatusCode().value());
        assertEquals(reportId, file.getRefId());
    }

    @Test
    void differentBindingDenied() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of(binding(UUID.randomUUID())));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.bind(reportId, file.getId(), owner)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.download(reportId, file.getId())).getStatusCode().value());
        verifyNoInteractions(minio);
    }

    @Test
    void genericAttachCannotMoveTypedBinding() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of(binding(reportId)));
        FileService generic = new FileService(minio, properties, buckets, files, references);
        assertThrows(AppException.class, () -> generic.attachFile(new FileAttach(file.getId(), UUID.randomUUID()), owner));
        verify(files, never()).save(any());
    }

    @Test
    void sharedReportAccessDoesNotGrantGenericPrivateAccess() throws Exception {
        when(files.findById(file.getId())).thenReturn(Optional.of(file));
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of(binding(reportId)));
        file.setRefId(reportId);
        objectAvailable();
        when(minio.getPresignedObjectUrl(any())).thenReturn("http://storage.test/original?sig=short-lived");
        FileService generic = new FileService(minio, properties, buckets, files, references);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                otherStaff, null, List.of(new SimpleGrantedAuthority("ROLE_SALE_STAFF"))));
        try {
            assertNotNull(generic.createDownloadUrl(file.getId(), owner));
            assertThrows(AppException.class, () -> generic.createDownloadUrl(file.getId(), otherStaff));
            var reportAccess = service.download(reportId, file.getId());
            assertNotNull(reportAccess.url());
            assertEquals(300, reportAccess.expiresInSeconds());
            assertEquals("original-etag", reportAccess.etag());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void administratorDoesNotBypassGenericOwnership() {
        when(files.findById(file.getId())).thenReturn(Optional.of(file));
        FileService generic = new FileService(minio, properties, buckets, files, references);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                otherStaff, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThrows(AppException.class, () -> generic.createDownloadUrl(file.getId(), otherStaff));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void inactiveDeletedOrPublicFileDenied() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        file.setStatus(FileStatus.PENDING);
        assertThrows(ResponseStatusException.class, () -> service.metadata(file.getId(), owner));
        file.setStatus(FileStatus.ACTIVE);
        file.setIsDeleted(true);
        assertThrows(ResponseStatusException.class, () -> service.download(reportId, file.getId()));
        file.setIsDeleted(false);
        file.setVisibility(FileVisibility.PUBLIC);
        assertThrows(ResponseStatusException.class, () -> service.metadata(file.getId(), owner));
        verifyNoInteractions(minio);
    }

    @Test
    void extensionMimeMismatchDenied() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        file.setExtension("xlsx");
        assertEquals(415, assertThrows(ResponseStatusException.class,
                () -> service.metadata(file.getId(), owner)).getStatusCode().value());
    }

    @Test
    void changedObjectEtagDenied() throws Exception {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(15L);
        when(stat.etag()).thenReturn("changed-etag");
        when(minio.statObject(any())).thenReturn(stat);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.metadata(file.getId(), owner)).getStatusCode().value());
        verify(minio, never()).getObject(any());
    }

    @Test
    void uploadCannotAssignUserDocumentToAnotherOwner() {
        FileService generic = new FileService(minio, properties, buckets, files, references);
        InitUploadRequest request = new InitUploadRequest("r.pdf", "application/pdf", 14L,
                FilePurpose.USER_DOCUMENT, "AI_REPORT", otherStaff, null);
        assertThrows(AppException.class, () -> generic.initUpload(request, owner));
        verifyNoInteractions(files, minio);
    }

    @Test
    void staffUploadCompleteBindAndSharedDownloadKeepsGenericOwnership() throws Exception {
        AtomicReference<FileReferences> savedReference = new AtomicReference<>();
        when(buckets.resolveVisibility(FilePurpose.USER_DOCUMENT)).thenReturn(FileVisibility.PRIVATE);
        when(buckets.resolveBucket(FileVisibility.PRIVATE)).thenReturn("private");
        when(minio.getPresignedObjectUrl(any())).thenReturn("http://storage.test/original?sig=temporary");
        when(files.save(any())).thenAnswer(invocation -> { file = invocation.getArgument(0); return file; });
        when(files.findById(any())).thenAnswer(invocation -> Optional.of(file));
        when(files.findForUpdate(any())).thenAnswer(invocation -> Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(any())).thenAnswer(invocation ->
                savedReference.get() == null ? List.of() : List.of(savedReference.get()));
        when(references.saveAndFlush(any())).thenAnswer(invocation -> {
            FileReferences reference = invocation.getArgument(0);
            savedReference.set(reference);
            return reference;
        });
        FileService generic = new FileService(minio, properties, buckets, files, references);
        var upload = generic.initUpload(new InitUploadRequest("Báo cáo.pdf", "application/pdf", 15L,
                FilePurpose.USER_DOCUMENT, "AI_REPORT", null, null), owner);
        assertEquals(FileStatus.PENDING, file.getStatus());
        assertEquals(owner, file.getOwnerId());
        String originalKey = file.getObjectKey();
        objectAvailable(); // Simulates browser PUT of the original bytes, not a second blob.
        generic.completeUpload(upload.fileId(), owner);
        assertEquals(FileStatus.ACTIVE, file.getStatus());
        service.bind(reportId, upload.fileId(), owner);
        assertNotNull(service.download(reportId, upload.fileId()).url());
        assertThrows(AppException.class, () -> generic.createDownloadUrl(upload.fileId(), otherStaff));
        assertNotNull(generic.createDownloadUrl(upload.fileId(), owner).url());
        assertEquals(originalKey, file.getObjectKey());
        assertEquals(upload.fileId(), savedReference.get().getFile().getId());
    }

    @Test
    void reportCannotBindASecondOriginal() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of());
        when(references.existsByRefTypeAndRefIdAndUsageTypeAndIsDeletedFalseAndFile_IdNot(
                "AI_REPORT", reportId, "ORIGINAL", file.getId())).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.bind(reportId, file.getId(), owner)).getStatusCode().value());
        verifyNoInteractions(minio);
    }

    @Test
    void missingFileAndOversizedDocumentDenied() {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.empty(), Optional.of(file));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.metadata(file.getId(), owner)).getStatusCode().value());
        file.setSizeBytes((long) AiReportDocumentValidator.MAX_BYTES + 1);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.metadata(file.getId(), owner)).getStatusCode().value());
    }

    @Test
    void orphanHookOnlyReviewsOldCandidatesAndDoesNotDelete() {
        when(references.findOrphanCandidates(any(), any())).thenReturn(List.of(file.getId()));
        assertEquals(List.of(file.getId()), service.orphanCandidates());
        verifyNoInteractions(files, minio);
    }

    @Test
    void deletedFileCannotCompleteOrAttach() {
        file.setIsDeleted(true);
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        FileService generic = new FileService(minio, properties, buckets, files, references);
        assertThrows(AppException.class, () -> generic.completeUpload(file.getId(), owner));
        assertThrows(AppException.class, () -> generic.attachFile(new FileAttach(file.getId(), reportId), owner));
        verify(files, never()).save(any());
        verifyNoInteractions(minio);
    }

    @Test
    void missingOriginalObjectIsPermanentNotTemporaryFailure() throws Exception {
        when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
        ErrorResponse response = mock(ErrorResponse.class);
        when(response.code()).thenReturn("NoSuchKey");
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(response);
        when(minio.statObject(any())).thenThrow(exception);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.metadata(file.getId(), owner)).getStatusCode().value());
        verify(minio, never()).getObject(any());
    }
}
