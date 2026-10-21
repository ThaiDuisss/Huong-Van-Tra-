package org.hvtra.fileservice.service;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import io.minio.Http.Headers;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.repository.FileReferenceRepository;
import org.hvtra.fileservice.repository.GeneratedReportFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real H2 transactions/reservations; synthetic MinIO only. */
@SpringBootTest
@ActiveProfiles("ai-report-test")
class GeneratedReportFileServiceTest {
    @Autowired GeneratedReportFileService service;
    @Autowired FileMetadataRepository files;
    @Autowired FileReferenceRepository references;
    @Autowired GeneratedReportFileRepository exports;
    @MockitoBean MinioClient minio;
    private byte[] bytes;
    private String checksum;
    private final String filename = "AI_Report_HuongVanTra_20261001_20261012.xlsx";
    private UUID id, owner;

    @BeforeEach void setUp() throws Exception {
        id = UUID.randomUUID(); owner = UUID.randomUUID();
        bytes = new AiReportDocumentValidatorTest().workbook(Map.of());
        checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private ErrorResponseException missing() {
        ErrorResponse error = mock(ErrorResponse.class); when(error.code()).thenReturn("NoSuchKey");
        ErrorResponseException exception = mock(ErrorResponseException.class); when(exception.errorResponse()).thenReturn(error);
        return exception;
    }
    private StatObjectResponse existing() {
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn((long) bytes.length); when(stat.etag()).thenReturn("synthetic-etag");
        Headers metadata = new Headers(Map.of("sha256", checksum));
        when(stat.userMetadata()).thenReturn(metadata); return stat;
    }
    private ObjectWriteResponse uploaded() {
        ObjectWriteResponse response = mock(ObjectWriteResponse.class); when(response.etag()).thenReturn("synthetic-etag"); return response;
    }

    @Test void privateGenerationAndRetryHaveOneFileAndGeneratedBinding() throws Exception {
        var missing = missing(); var stat = existing(); var upload = uploaded();
        when(minio.statObject(any())).thenThrow(missing).thenReturn(stat);
        when(minio.putObject(any())).thenReturn(upload);
        var first = service.store(id, owner, filename, checksum, bytes);
        var retry = service.store(id, owner, filename, checksum, bytes);
        assertEquals(first, retry); assertEquals(id, first.fileId()); assertEquals("PRIVATE", first.visibility());
        var file = files.findById(id).orElseThrow();
        assertEquals(FileStatus.ACTIVE, file.getStatus()); assertNull(file.getPublicUrl());
        assertEquals("fixture-private", file.getBucket()); assertEquals(owner, file.getOwnerId());
        var bindings = references.findByFile_IdAndIsDeletedFalse(id);
        assertEquals(1, bindings.size()); assertEquals("GENERATED", bindings.getFirst().getUsageType());
        verify(minio, times(1)).putObject(any()); verify(minio, never()).makeBucket(any()); verify(minio, never()).setBucketPolicy(any());
    }

    @Test void putSuccessLostResponseRecoversReservedObjectWithoutSecondPut() throws Exception {
        var missing = missing(); var stat = existing();
        when(minio.statObject(any())).thenThrow(missing).thenReturn(stat);
        when(minio.putObject(any())).thenThrow(new IllegalStateException("Synthetic lost response after storage commit"));
        assertEquals(502, assertThrows(ResponseStatusException.class,
            () -> service.store(id, owner, filename, checksum, bytes)).getStatusCode().value());
        assertTrue(exports.existsById(id)); assertEquals(FileStatus.PENDING, files.findById(id).orElseThrow().getStatus());
        assertTrue(references.findByFile_IdAndIsDeletedFalse(id).isEmpty());
        assertEquals("ACTIVE", service.store(id, owner, filename, checksum, bytes).status());
        verify(minio, times(1)).putObject(any());
    }

    @Test void failedStorageLeavesDurableReservationAndRetryUsesSameFile() throws Exception {
        when(minio.statObject(any())).thenThrow(new IllegalStateException("Synthetic offline storage"));
        assertThrows(ResponseStatusException.class, () -> service.store(id, owner, filename, checksum, bytes));
        assertEquals(FileStatus.PENDING, files.findById(id).orElseThrow().getStatus());
        reset(minio);
        var missing = missing(); var upload = uploaded();
        when(minio.statObject(any())).thenThrow(missing); when(minio.putObject(any())).thenReturn(upload);
        assertEquals(id, service.store(id, owner, filename, checksum, bytes).fileId());
    }

    @Test void differentOwnerOrFilenameCannotReuseReservedReport() throws Exception {
        var missing = missing(); var upload = uploaded();
        when(minio.statObject(any())).thenThrow(missing); when(minio.putObject(any())).thenReturn(upload);
        service.store(id, owner, filename, checksum, bytes);
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.store(id, UUID.randomUUID(), filename, checksum, bytes)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.store(id, owner, "AI_Report_HuongVanTra_20261002_20261012.xlsx", checksum, bytes)).getStatusCode().value());
        verify(minio, times(1)).putObject(any());
    }

    @Test void checksumMismatchAndInvalidWorkbookNeverReserveStorage() {
        assertThrows(ResponseStatusException.class, () -> service.store(id, owner, filename, "0".repeat(64), bytes));
        assertFalse(exports.existsById(id)); verifyNoInteractions(minio);
    }

    @Test void objectWithDifferentChecksumIsNotOverwritten() throws Exception {
        StatObjectResponse stat = existing(); Headers metadata = new Headers(Map.of("sha256", "0".repeat(64)));
        when(stat.userMetadata()).thenReturn(metadata);
        when(minio.statObject(any())).thenReturn(stat);
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.store(id, owner, filename, checksum, bytes)).getStatusCode().value());
        verify(minio, never()).putObject(any());
    }
}
