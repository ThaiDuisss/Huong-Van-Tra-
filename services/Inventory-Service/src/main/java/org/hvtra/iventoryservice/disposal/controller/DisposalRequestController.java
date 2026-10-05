package org.hvtra.iventoryservice.disposal.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestResponse;
import org.hvtra.iventoryservice.disposal.service.DisposalRequestService;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/disposal-requests")
@RequiredArgsConstructor
public class DisposalRequestController {

    private final DisposalRequestService service;

    @GetMapping
    public ResponseEntity<Page<DisposalRequestResponse>> listRequests(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String requestCode,
            @RequestParam(required = false) String returnCode,
            @RequestParam(required = false) DisposalRequestStatus status,
            @PageableDefault(size = 10)
            Pageable pageable
    ) {
        return ResponseEntity.ok(service.listRequests(keyword, requestCode, returnCode, status, pageable));
    }

    @GetMapping("/status-counts")
    public ResponseEntity<Map<String, Long>> getStatusCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String requestCode,
            @RequestParam(required = false) String returnCode
    ) {
        return ResponseEntity.ok(service.getStatusCounts(keyword, requestCode, returnCode));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<DisposalRequestResponse> getRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(service.getRequest(requestId));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<DisposalRequestResponse> approveRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(service.approveRequest(requestId));
    }
}
