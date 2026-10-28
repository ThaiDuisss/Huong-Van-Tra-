package org.hvtra.fileservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import org.hvtra.fileservice.exception.UploadCompletionException;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.service.FileService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("ai-report-test")
class FileServiceApplicationTests {
    @Autowired FileService service;
    @Autowired FileMetadataRepository files;
    @MockitoBean MinioClient minio;

    @Test
    void contextLoads() {
    }

    @Test
    void rejectedCompletionCommitsFailedStatusAndKeepsSizeErrorCode() throws Exception {
        UUID owner = UUID.randomUUID();
        when(minio.getPresignedObjectUrl(any())).thenReturn("http://127.0.0.1:9/fixture");
        var upload = service.initUpload(new InitUploadRequest("original.pdf", "application/pdf", 20L,
                FilePurpose.USER_DOCUMENT, "AI_REPORT", null, null), owner);
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(19L);
        when(minio.statObject(any())).thenReturn(stat);
        var failure = assertThrows(UploadCompletionException.class, () -> service.completeUpload(upload.fileId(), owner));
        assertEquals(7010, failure.getCode());
        assertEquals(FileStatus.FAILED, files.findById(upload.fileId()).orElseThrow().getStatus());
    }

    @Test
    void storageFailureAlsoCommitsFailedStatus() throws Exception {
        UUID owner = UUID.randomUUID();
        when(minio.getPresignedObjectUrl(any())).thenReturn("http://127.0.0.1:9/fixture");
        var upload = service.initUpload(new InitUploadRequest("original.pdf", "application/pdf", 20L,
                FilePurpose.USER_DOCUMENT, "AI_REPORT", null, null), owner);
        when(minio.statObject(any())).thenThrow(new IllegalStateException("Synthetic storage outage"));
        assertEquals(8002, assertThrows(UploadCompletionException.class,
                () -> service.completeUpload(upload.fileId(), owner)).getCode());
        assertEquals(FileStatus.FAILED, files.findById(upload.fileId()).orElseThrow().getStatus());
    }

}
