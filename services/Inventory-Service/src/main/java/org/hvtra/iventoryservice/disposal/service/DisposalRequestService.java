package org.hvtra.iventoryservice.disposal.service;

import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestResponse;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DisposalRequestService {

    DisposalRequestResponse getRequest(UUID requestId);

    Page<DisposalRequestResponse> listRequests(
            String requestCode,
            String returnCode,
            DisposalRequestStatus status,
            Pageable pageable
    );

    DisposalRequestResponse approveRequest(UUID requestId);
}
