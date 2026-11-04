package org.hvtra.fileservice.controller;

import io.minio.GetObjectResponse;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.StatObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.ObjectWriteResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import io.minio.Http.Headers;
import org.hvtra.fileservice.FileServiceApplication;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.repository.FileReferenceRepository;
import org.hvtra.fileservice.service.AiReportFileAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real Spring controllers/security/JPA over HTTP; MinIO is a synthetic capability fixture.
 * H2 is not evidence of MySQL Flyway compatibility or a live MinIO E2E.
 */
@EnabledIfSystemProperty(named = "recommendation.smoke.python", matches = ".+")
@SpringBootTest(classes = FileServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ai-report-test")
@Import(AiReportHttpIntegrationTest.FixtureConfig.class)
class AiReportHttpIntegrationTest {
    @LocalServerPort int port;
    @Autowired FileMetadataRepository files;
    @Autowired FileReferenceRepository references;
    @Autowired AiReportFileAccessService access;
    @Autowired StorageFixture storage;
    @MockitoBean MinioClient minio;

    @Test
    void pythonImportUploadBindingDownloadAndRecoveryOverHttp() throws Exception {
        when(minio.getPresignedObjectUrl(any())).thenAnswer(invocation -> {
            GetPresignedObjectUrlArgs args = invocation.getArgument(0);
            return storage.capability(args.object(), args.method().name(), args.expiry());
        });
        when(minio.statObject(any())).thenAnswer(invocation -> {
            StatObjectArgs args = invocation.getArgument(0);
            if (!storage.objects.containsKey(args.object())) {
                ErrorResponse error = mock(ErrorResponse.class); when(error.code()).thenReturn("NoSuchKey");
                ErrorResponseException missing = mock(ErrorResponseException.class); when(missing.errorResponse()).thenReturn(error);
                throw missing;
            }
            byte[] bytes = storage.require(args.object());
            StatObjectResponse stat = mock(StatObjectResponse.class);
            when(stat.size()).thenReturn((long) bytes.length);
            when(stat.etag()).thenReturn(storage.etag(bytes));
            Headers metadata = new Headers(Map.of("sha256", storage.etag(bytes)));
            when(stat.userMetadata()).thenReturn(metadata);
            return stat;
        });
        when(minio.putObject(any())).thenAnswer(invocation -> {
            PutObjectArgs args = invocation.getArgument(0);
            byte[] bytes = args.stream().readAllBytes();
            storage.objects.put(args.object(), bytes);
            ObjectWriteResponse uploaded = mock(ObjectWriteResponse.class);
            when(uploaded.etag()).thenReturn(storage.etag(bytes));
            return uploaded;
        });
        when(minio.getObject(any())).thenAnswer(invocation -> {
            GetObjectArgs args = invocation.getArgument(0);
            GetObjectResponse stream = mock(GetObjectResponse.class);
            when(stream.readNBytes(anyInt())).thenReturn(storage.require(args.object()));
            return stream;
        });
        Path service = Path.of(System.getProperty("recommendation.smoke.service"));
        ProcessBuilder builder = new ProcessBuilder(System.getProperty("recommendation.smoke.python"), "-B",
                "tests/file_service_http_smoke.py", Integer.toString(port));
        builder.directory(service.toFile());
        builder.environment().putAll(Map.of("MYSQL_HOST", "127.0.0.1", "MYSQL_PORT", "9", "MYSQL_USER", "fixture",
                "MYSQL_PASSWORD", "fixture", "MYSQL_DATABASE", "fixture", "INTERNAL_HEADER_SECRET",
                "isolated-ai-report-context-secret-32", "APP_ENVIRONMENT", "test", "INTERNAL_REPLAY_BACKEND", "memory"));
        builder.environment().put("FILE_SERVICE_URL", "http://127.0.0.1:" + port);
        builder.environment().put("AI_REPORT_STORAGE_ORIGINS", "http://127.0.0.1:" + port);
        builder.environment().put("CATALOG_SERVICE_URL", "http://127.0.0.1:9");
        builder.environment().put("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9");
        builder.redirectErrorStream(true);
        builder.redirectOutput(Path.of(System.getProperty("ai.report.smoke.log")).toFile());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Python HTTP fixture timeout");
            assertEquals(0, process.exitValue(), "See isolated Python AI Report smoke log");
        } finally {
            if (process.isAlive()) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
        }
        assertEquals(3, references.count());
        assertEquals(3, files.count());
        assertEquals(3, storage.objects.size());
        var original = references.findAll().stream().filter(ref -> "ORIGINAL".equals(ref.getUsageType())).findFirst().orElseThrow();
        UUID fileId = original.getFile().getId();
        UUID reportId = original.getRefId();
        UUID ownerId = files.findById(fileId).orElseThrow().getOwnerId();
        references.deleteById(original.getId());
        assertEquals(reportId, files.findById(fileId).orElseThrow().getRefId());
        access.bind(reportId, fileId, ownerId);
        access.bind(reportId, fileId, ownerId);
        assertEquals(3, references.count(), "Missing typed binding restored once with original identity");
        files.findAll().forEach(file -> {
            assertNull(file.getPublicUrl());
            assertNotNull(file.getRefId());
            assertEquals("PRIVATE", file.getVisibility().name());
        });
        verify(minio, never()).setBucketPolicy(any());
        verify(minio, never()).makeBucket(any());
    }

    @TestConfiguration
    static class FixtureConfig {
        @Bean StorageFixture storageFixture(Environment environment) { return new StorageFixture(environment); }
    }

    @RestController
    static class StorageFixture {
        final Environment environment;
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        final Map<String, Capability> capabilities = new ConcurrentHashMap<>();
        record Capability(String key, String method, Instant expiry) { }
        StorageFixture(Environment environment) { this.environment = environment; }
        String capability(String key, String method, int ttl) {
            String token = UUID.randomUUID().toString();
            capabilities.put(token, new Capability(key, method, Instant.now().plusSeconds(ttl)));
            return "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/actuator/fixture-object?cap=" + token;
        }
        Capability authorized(String token, String method) {
            Capability cap = capabilities.get(token);
            if (cap == null || !method.equals(cap.method()) || !cap.expiry().isAfter(Instant.now()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return cap;
        }
        byte[] require(String key) {
            byte[] bytes = objects.get(key);
            if (bytes == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return bytes;
        }
        String etag(byte[] bytes) throws Exception {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        @PutMapping("/actuator/fixture-object")
        ResponseEntity<Void> put(@RequestParam("cap") String token, @RequestBody byte[] bytes) {
            objects.put(authorized(token, "PUT").key(), bytes);
            return ResponseEntity.ok().build();
        }
        @GetMapping("/actuator/fixture-object")
        ResponseEntity<byte[]> get(@RequestParam("cap") String token,
                @RequestHeader(value = "If-Match", required = false) String match) throws Exception {
            byte[] bytes = require(authorized(token, "GET").key());
            String etag = "\"" + etag(bytes) + "\"";
            if (match != null && !etag.equals(match)) return ResponseEntity.status(412).build();
            return ResponseEntity.ok().eTag(etag).contentLength(bytes.length).body(bytes);
        }
    }
}
