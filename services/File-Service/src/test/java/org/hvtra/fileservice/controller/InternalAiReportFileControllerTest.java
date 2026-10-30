package org.hvtra.fileservice.controller;

import jakarta.servlet.Filter;
import org.hvtra.commonservice.common_core.config.InternalHeaderAuthenticationFilter;
import org.hvtra.fileservice.exception.AiReportFileExceptionHandler;
import org.hvtra.fileservice.model.response.AiReportFileResponse;
import org.hvtra.fileservice.service.AiReportFileAccessService;
import org.hvtra.fileservice.service.GeneratedReportFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalAiReportFileControllerTest {
    private static final String SECRET = "test-internal-file-secret";
    private static final String CALLER = "service:recommendation-service";
    private final UUID file = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private AiReportFileAccessService service;
    private GeneratedReportFileService generated;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AiReportFileAccessService.class);
        generated = mock(GeneratedReportFileService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        Filter principalBridge = (request, response, chain) -> {
            ((MockHttpServletRequest) request).setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            chain.doFilter(request, response);
        };
        mvc = MockMvcBuilders.standaloneSetup(new InternalAiReportFileController(service, generated))
                .setControllerAdvice(new AiReportFileExceptionHandler()).setValidator(validator)
                .addFilters(new InternalHeaderAuthenticationFilter(SECRET, "file-service"), principalBridge).build();
    }

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    @Test void generatedUploadRequiresExactInternalCallerAndSystemRole() throws Exception {
        String path = "/internal/file/ai-reports/" + file + "/generated";
        for (String identity : new String[]{owner.toString(), "service:other"}) {
            mvc.perform(signed(put(path).contentType("application/octet-stream").content(new byte[]{1})
                    .param("ownerId", owner.toString()).param("fileName", "report.xlsx").param("sha256", "0".repeat(64)),
                    identity, "SYSTEM")).andExpect(status().isForbidden());
        }
        mvc.perform(signed(put(path).contentType("application/octet-stream").content(new byte[]{1})
                .param("ownerId", owner.toString()).param("fileName", "report.xlsx").param("sha256", "0".repeat(64)),
                CALLER, "SALE_STAFF")).andExpect(status().isForbidden());
        verifyNoInteractions(generated);
    }

    @Test void generatedUploadAndDownloadUseSeparateTrustedOperations() throws Exception {
        mvc.perform(signed(put("/internal/file/ai-reports/" + file + "/generated")
                .contentType("application/octet-stream").content(new byte[]{1, 2})
                .param("ownerId", owner.toString()).param("fileName", "report.xlsx").param("sha256", "0".repeat(64)),
                CALLER, "SYSTEM")).andExpect(status().isOk());
        verify(generated).store(eq(file), eq(owner), eq("report.xlsx"), eq("0".repeat(64)), aryEq(new byte[]{1, 2}));
        mvc.perform(signed(post("/internal/file/ai-reports/" + file + "/generated-download-url")
                .contentType("application/json").content("{\"fileId\":\"" + file + "\"}"), CALLER, "SYSTEM"))
                .andExpect(status().isOk());
        verify(service).downloadGenerated(file, file);
        verify(service, never()).download(any(), any());
    }

    MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String user, String roles) throws Exception {
        String requestId = UUID.randomUUID().toString();
        String authorization = "Bearer service:test";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(String.join("\n", user, roles, requestId, authorization).getBytes(StandardCharsets.UTF_8)));
        return request.header("X-User-ID", user).header("X-User_Roles", roles)
                .header("X-Request-ID", requestId).header("Authorization", authorization).header("X-Signature", signature);
    }

    MockHttpServletRequestBuilder metadata() {
        return post("/internal/file/ai-reports/metadata").contentType("application/json")
                .content("{\"fileId\":\"" + file + "\",\"ownerId\":\"" + owner + "\"}");
    }

    @Test void missingInternalContextRejected() throws Exception {
        mvc.perform(metadata()).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void badSignatureRejected() throws Exception {
        mvc.perform(metadata().header("X-User-ID", CALLER).header("X-User_Roles", "SYSTEM")
                .header("X-Request-ID", "request").header("Authorization", "Bearer test").header("X-Signature", "bad"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void forgedUserRejected() throws Exception {
        var request = signed(metadata(), CALLER, "SYSTEM");
        mvc.perform(request.with(r -> { r.removeHeader("X-User-ID"); r.addHeader("X-User-ID", "service:other"); return r; }))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void forgedRolesRejected() throws Exception {
        var request = signed(metadata(), owner.toString(), "SALE_STAFF");
        mvc.perform(request.with(r -> { r.removeHeader("X-User_Roles"); r.addHeader("X-User_Roles", "SYSTEM"); return r; }))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void wrongSignedServiceDenied() throws Exception {
        mvc.perform(signed(metadata(), "service:catalog-service", "SYSTEM")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void humanStaffDeniedInternalAccess() throws Exception {
        mvc.perform(signed(metadata(), owner.toString(), "SALE_STAFF")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void serviceWithoutSystemDenied() throws Exception {
        mvc.perform(signed(metadata(), CALLER, "ADMIN")).andExpect(status().isForbidden());
    }
    @Test void signedAllowlistedServiceReceivesTrustedMetadata() throws Exception {
        when(service.metadata(file, owner)).thenReturn(new AiReportFileResponse(file, owner, "r.pdf", "PDF",
                "application/pdf", 14, "ACTIVE", "PRIVATE", false));
        mvc.perform(signed(metadata(), CALLER, "SYSTEM")).andExpect(status().isOk())
                .andExpect(jsonPath("fileId").value(file.toString())).andExpect(jsonPath("fileType").value("PDF"));
    }
    @Test void bindingContractAndValidation() throws Exception {
        UUID report = UUID.randomUUID();
        mvc.perform(signed(put("/internal/file/ai-reports/" + report + "/binding")
                .contentType("application/json").content("{\"fileId\":\"" + file + "\",\"ownerId\":\"" + owner + "\"}"), CALLER, "SYSTEM"))
                .andExpect(status().isNoContent());
        verify(service).bind(report, file, owner);
        mvc.perform(signed(post("/internal/file/ai-reports/metadata").contentType("application/json").content("{}"), CALLER, "SYSTEM"))
                .andExpect(status().isBadRequest());
    }
    @Test void wrongBindingStatusIsPreserved() throws Exception {
        when(service.download(any(), any())).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Binding mismatch"));
        mvc.perform(signed(post("/internal/file/ai-reports/" + UUID.randomUUID() + "/download-url")
                .contentType("application/json").content("{\"fileId\":\"" + file + "\"}"), CALLER, "SYSTEM"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("detail").value("Binding mismatch"));
    }
}
