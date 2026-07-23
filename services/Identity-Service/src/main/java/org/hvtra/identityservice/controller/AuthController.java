package org.hvtra.identityservice.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.identityservice.model.response.IdentityMeResponse;
import org.hvtra.identityservice.service.IdentityMeService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class AuthController {
    private final IdentityMeService identityMeService;

    @GetMapping("/me")
    public ApiResponse<IdentityMeResponse> me(
            @AuthenticationPrincipal InternalUserPrincipal currentUser,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        return identityMeService.getMe(currentUser, authorization);
    }
}
