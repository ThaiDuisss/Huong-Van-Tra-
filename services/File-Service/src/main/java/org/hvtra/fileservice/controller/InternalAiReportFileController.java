package org.hvtra.fileservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.fileservice.model.request.AiReportDownloadRequest;
import org.hvtra.fileservice.model.request.AiReportFileRequest;
import org.hvtra.fileservice.model.response.AiReportDownloadResponse;
import org.hvtra.fileservice.model.response.AiReportFileResponse;
import org.hvtra.fileservice.service.AiReportFileAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/file/ai-reports")
@RequiredArgsConstructor
public class InternalAiReportFileController {
    private final AiReportFileAccessService service;

    @PostMapping("/metadata")
    public AiReportFileResponse metadata(@Valid @RequestBody AiReportFileRequest request, Authentication authentication) {
        requireCaller(authentication);
        return service.metadata(request.fileId(), request.ownerId());
    }

    @PutMapping("/{reportId}/binding")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bind(@PathVariable("reportId") UUID reportId, @Valid @RequestBody AiReportFileRequest request, Authentication authentication) {
        requireCaller(authentication);
        service.bind(reportId, request.fileId(), request.ownerId());
    }

    @PostMapping("/{reportId}/download-url")
    public AiReportDownloadResponse download(@PathVariable("reportId") UUID reportId,
            @Valid @RequestBody AiReportDownloadRequest request, Authentication authentication) {
        requireCaller(authentication);
        return service.download(reportId, request.fileId());
    }

    @GetMapping("/orphans")
    public List<UUID> orphanCandidates(Authentication authentication) {
        requireCaller(authentication);
        return service.orphanCandidates();
    }

    private void requireCaller(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof InternalUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated internal caller required");
        }
        if (!"service:recommendation-service".equals(principal.userId())
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_SYSTEM".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal caller denied");
        }
    }
}
