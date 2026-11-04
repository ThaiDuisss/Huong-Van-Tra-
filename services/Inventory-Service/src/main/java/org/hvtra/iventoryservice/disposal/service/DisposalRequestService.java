package org.hvtra.iventoryservice.disposal.service;

import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestResponse;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface DisposalRequestService {

    DisposalRequestResponse getRequest(UUID requestId);

    default Page<DisposalRequestResponse> listRequests(
            String keyword,
            String requestCode,
            String returnCode,
            DisposalRequestStatus status,
            Pageable pageable
    ) {
        return listRequests(keyword, requestCode, returnCode, status, null, null, pageable);
    }

    Page<DisposalRequestResponse> listRequests(
            String keyword,
            String requestCode,
            String returnCode,
            DisposalRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    default Map<String, Long> getStatusCounts(String keyword, String requestCode, String returnCode) {
        return getStatusCounts(keyword, requestCode, returnCode, null, null);
    }

    Map<String, Long> getStatusCounts(
            String keyword, String requestCode, String returnCode, LocalDate fromDate, LocalDate toDate);

    DisposalRequestResponse approveRequest(UUID requestId);
}
