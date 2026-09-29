package org.hvtra.iventoryservice.disposal;

import jakarta.persistence.EntityManager;
import org.hvtra.iventoryservice.disposal.dto.response.DisposalRequestResponse;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
import org.hvtra.iventoryservice.disposal.service.impl.DisposalRequestServiceImpl;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:disposal_contract;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DisposalRequestContractJpaTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    DisposalRequestRepository repository;

    @Test
    void filtersCountsAndOrdersAcrossPageBoundary() {
        Instant base = Instant.parse("2026-09-29T12:00:00Z");
        for (int index = 1; index <= 11; index++) {
            String suffix = String.format("%02d", index);
            createRequest("DSP-P" + suffix,
                    index == 1 ? "RET-SPECIAL-P" : "RET-P" + suffix,
                    DisposalRequestStatus.PENDING_APPROVAL,
                    base.plusSeconds(Math.min(index, 10) * 60L));
        }
        for (int index = 1; index <= 3; index++) {
            String suffix = String.format("%02d", index);
            createRequest("DSP-A" + suffix,
                    index == 1 ? "RET-SPECIAL-A" : "RET-A" + suffix,
                    DisposalRequestStatus.APPROVED,
                    base.plusSeconds((60L + index) * 60L));
        }
        entityManager.clear();

        DisposalRequestServiceImpl service = new DisposalRequestServiceImpl(repository, null, null, null);
        assertEquals(List.of("DSP-P01"), codes(service.listRequests(
                "DSP-P01", null, null, null, PageRequest.of(0, 10))));
        assertEquals(List.of("DSP-P01"), codes(service.listRequests(
                "RET-SPECIAL-P", null, null, null, PageRequest.of(0, 10))));
        assertEquals(0, service.listRequests("NOT-FOUND", null, null, null,
                PageRequest.of(0, 10)).getTotalElements());
        assertEquals(11, service.listRequests(null, null, null, DisposalRequestStatus.PENDING_APPROVAL,
                PageRequest.of(0, 10)).getTotalElements());

        assertEquals(Map.of("ALL", 14L, "PENDING_APPROVAL", 11L, "APPROVED", 3L),
                service.getStatusCounts(null, null, null));
        assertEquals(Map.of("ALL", 2L, "PENDING_APPROVAL", 1L, "APPROVED", 1L),
                service.getStatusCounts("RET-SPECIAL", null, null));
        assertEquals(2, service.listRequests("RET-SPECIAL", null, null, null,
                PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, service.listRequests("RET-SPECIAL", "DSP-A", null, null,
                PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, service.listRequests(null, "DSP-P01", "RET-SPECIAL-P", null,
                PageRequest.of(0, 10)).getTotalElements());

        var first = service.listRequests(null, null, null, null, PageRequest.of(0, 10));
        var second = service.listRequests(null, null, null, null, PageRequest.of(1, 10));
        assertEquals(14, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(0, first.getNumber());
        assertEquals(10, first.getSize());
        assertEquals(List.of("DSP-P10", "DSP-P11", "DSP-P09", "DSP-P08", "DSP-P07",
                "DSP-P06", "DSP-P05", "DSP-P04", "DSP-P03", "DSP-P02"), codes(first));
        assertEquals(List.of("DSP-P01", "DSP-A03", "DSP-A02", "DSP-A01"), codes(second));
        assertTrue(first.getContent().stream().allMatch(
                request -> request.status() == DisposalRequestStatus.PENDING_APPROVAL));
    }

    private List<String> codes(Page<DisposalRequestResponse> page) {
        return page.getContent().stream().map(request -> request.requestCode()).toList();
    }

    private void createRequest(String code, String returnCode, DisposalRequestStatus status, Instant createdAt) {
        CustomerReturnInspection inspection = new CustomerReturnInspection();
        inspection.setInspectionCode("QC-" + code);
        inspection.setReturnId(UUID.randomUUID());
        inspection.setReturnCode(returnCode);
        entityManager.persist(inspection);

        DisposalRequest request = new DisposalRequest();
        request.setRequestCode(code);
        request.setSourceType("CUSTOMER_RETURN_INSPECTION");
        request.setSourceInspection(inspection);
        request.setReturnId(inspection.getReturnId());
        request.setReturnCode(returnCode);
        request.setStatus(status);
        entityManager.persist(request);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE disposal_requests SET created_at = :createdAt WHERE request_code = :code")
                .setParameter("createdAt", Timestamp.from(createdAt))
                .setParameter("code", code)
                .executeUpdate();
    }
}
