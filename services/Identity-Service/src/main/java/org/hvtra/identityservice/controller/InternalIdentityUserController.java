package org.hvtra.identityservice.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.identityservice.model.response.IdentityUserBriefResponse;
import org.hvtra.identityservice.service.IdentityUserLookupService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/identity/users")
@RequiredArgsConstructor
public class InternalIdentityUserController {

    private final IdentityUserLookupService identityUserLookupService;

    /** Lấy tên/username/email của nhiều user theo Keycloak subject — dùng để hiển thị người tạo/người duyệt ở service khác. */
    @PostMapping("/batch")
    public List<IdentityUserBriefResponse> getUsersBatch(@RequestBody List<String> keycloakSubjects) {
        return identityUserLookupService.findBySubjects(keycloakSubjects);
    }
}
