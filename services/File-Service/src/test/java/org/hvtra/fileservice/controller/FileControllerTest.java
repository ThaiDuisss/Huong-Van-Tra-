package org.hvtra.fileservice.controller;

import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.request.FileAttach;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.model.response.DownloadUrlResponse;
import org.hvtra.fileservice.model.response.InitUploadResponse;
import org.hvtra.fileservice.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MVC: no Spring context, no security filter chain, FileService is a mock. */
class FileControllerTest {
    private static final String USER_HEADER = "X-User-ID";
    private final UUID user = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID fileId = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private final UUID ref = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private FileService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(FileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FileController(service)).build();
    }

    private MockHttpServletRequestBuilder initUpload(String json) {
        return post("/api/file/init-upload").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String uploadJson(String filename, String contentType, String size, String purpose) {
        return "{\"originalFilename\":%s,\"contentType\":%s,\"sizeBytes\":%s,\"purpose\":%s,\"refId\":\"%s\"}"
                .formatted(filename, contentType, size, purpose, ref);
    }

    @Test
    void initUploadPassesBodyAndHeaderIdentityToServiceAndWrapsResponse() throws Exception {
        when(service.initUpload(any(), eq(user))).thenReturn(new InitUploadResponse(fileId, FileVisibility.PUBLIC,
                "fixture-public", "items/k.png", "http://upload.fixture/put", "http://cdn.fixture/k.png", 300));

        mvc.perform(initUpload(uploadJson("\"a.png\"", "\"image/png\"", "42", "\"ITEM_IMAGE\""))
                        .header(USER_HEADER, user.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.data.uploadUrl").value("http://upload.fixture/put"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));

        ArgumentCaptor<InitUploadRequest> captor = ArgumentCaptor.forClass(InitUploadRequest.class);
        verify(service).initUpload(captor.capture(), eq(user));
        assertEquals(new InitUploadRequest("a.png", "image/png", 42L, FilePurpose.ITEM_IMAGE, null, null, ref),
                captor.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"originalFilename\":\" \",\"contentType\":\"image/png\",\"sizeBytes\":1,\"purpose\":\"ITEM_IMAGE\"}",
            "{\"contentType\":\"image/png\",\"sizeBytes\":1,\"purpose\":\"ITEM_IMAGE\"}",
            "{\"originalFilename\":\"a.png\",\"contentType\":\"\",\"sizeBytes\":1,\"purpose\":\"ITEM_IMAGE\"}",
            "{\"originalFilename\":\"a.png\",\"contentType\":\"image/png\",\"purpose\":\"ITEM_IMAGE\"}",
            "{\"originalFilename\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":0,\"purpose\":\"ITEM_IMAGE\"}",
            "{\"originalFilename\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":-5,\"purpose\":\"ITEM_IMAGE\"}",
            "{\"originalFilename\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":1}",
            "{\"originalFilename\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":1,\"purpose\":\"NOT_A_PURPOSE\"}",
            "not json"
    })
    void invalidInitUploadBodyIsRejectedBeforeReachingService(String body) throws Exception {
        mvc.perform(initUpload(body).header(USER_HEADER, user.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void completeUploadUsesPathFileIdAndHeaderIdentity() throws Exception {
        mvc.perform(post("/api/file/{id}/complete", fileId).header(USER_HEADER, user.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Success"));
        verify(service).completeUpload(fileId, user);
    }

    @Test
    void downloadUrlReturnsServiceResultForHeaderIdentity() throws Exception {
        when(service.createDownloadUrl(fileId, user))
                .thenReturn(new DownloadUrlResponse("http://download.fixture/get", 60, FileVisibility.PRIVATE));

        mvc.perform(get("/api/file/{id}/download-url", fileId).header(USER_HEADER, user.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("http://download.fixture/get"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(60))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
    }

    @Test
    void attachPassesBodyAndHeaderIdentityAndReturnsAffectedCount() throws Exception {
        when(service.attachFile(new FileAttach(fileId, ref), user)).thenReturn(1);

        mvc.perform(put("/api/file/attach").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":\"%s\",\"refId\":\"%s\"}".formatted(fileId, ref))
                        .header(USER_HEADER, user.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-uuid", "service:recommendation-service", "null"})
    void everyEndpointRequiresHumanUuidIdentity(String identity) throws Exception {
        String validBody = uploadJson("\"a.png\"", "\"image/png\"", "42", "\"ITEM_IMAGE\"");
        mvc.perform(initUpload(validBody).header(USER_HEADER, identity)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/file/{id}/complete", fileId).header(USER_HEADER, identity))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/file/{id}/download-url", fileId).header(USER_HEADER, identity))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/file/attach").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":\"%s\",\"refId\":\"%s\"}".formatted(fileId, ref))
                        .header(USER_HEADER, identity))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void missingIdentityHeaderIsUnauthorizedOnReadAndWriteEndpoints() throws Exception {
        mvc.perform(get("/api/file/{id}/download-url", fileId)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/file/attach").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":\"%s\",\"refId\":\"%s\"}".formatted(fileId, ref)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void malformedFileIdInPathIsBadRequest() throws Exception {
        mvc.perform(get("/api/file/not-a-uuid/download-url").header(USER_HEADER, user.toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void serviceRefusalIsNotSwallowedByController() {
        when(service.createDownloadUrl(fileId, user)).thenThrow(new AppException(7002, "Forbidden"));

        Exception failure = assertThrows(Exception.class, () -> mvc.perform(
                get("/api/file/{id}/download-url", fileId).header(USER_HEADER, user.toString())));

        AppException cause = assertInstanceOf(AppException.class, failure.getCause());
        assertEquals(7002, cause.getCode());
        assertNull(cause.getFieldValidateExceptions());
    }
}
