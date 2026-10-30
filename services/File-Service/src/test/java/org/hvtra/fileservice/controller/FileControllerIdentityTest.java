package org.hvtra.fileservice.controller;

import org.hvtra.fileservice.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerIdentityTest {
    @Test
    void noFallbackOwnerForMissingOrServiceIdentity() throws Exception {
        FileService service = mock(FileService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new FileController(service)).build();
        String path = "/api/file/11111111-1111-4111-8111-111111111111/complete";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("X-User-ID", "service:recommendation-service"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
