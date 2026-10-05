package org.hvtra.iventoryservice.disposal;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.iventoryservice.customerreturn.repository.CustomerReturnInspectionRepository;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.disposal.repository.DisposalRequestRepository;
import org.hvtra.iventoryservice.disposal.service.impl.DisposalRequestServiceImpl;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisposalApprovalCompatibilityTest {
    @Mock private DisposalRequestRepository requestRepository;
    @Mock private CustomerReturnInspectionRepository inspectionRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private CustomerReturnInspectionService inspectionService;

    private DisposalRequestServiceImpl service;
    private CustomerReturnInspection inspection;
    private DisposalRequest request;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        authenticate("ADMIN");
        service = new DisposalRequestServiceImpl(
                requestRepository, inspectionRepository, transactionRepository, inspectionService);
        inspection = new CustomerReturnInspection();
        inspection.setId(UUID.randomUUID());
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        CustomerReturnInspectionLine line = new CustomerReturnInspectionLine();
        line.setId(UUID.randomUUID());
        line.setInventoryItem(item);
        line.setResult(CustomerReturnQcResult.WRITE_OFF);
        line.setQuantity(new BigDecimal("2.00"));
        line.setPosted(false);
        inspection.addLine(line);
        request = new DisposalRequest();
        request.setId(UUID.randomUUID());
        request.setSourceInspection(inspection);
        request.setSourceType("CUSTOMER_RETURN_INSPECTION");
        request.setStatus(DisposalRequestStatus.PENDING_APPROVAL);
        request.addLine(line);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"})
    void existingApprovalRolesAndMovementRemainCompatible(String role) {
        authenticate(role);
        stubLocks();
        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(requestRepository.saveAndFlush(request)).thenReturn(request);

        service.approveRequest(request.getId());

        assertThat(request.getStatus()).isEqualTo(DisposalRequestStatus.APPROVED);
        assertThat(request.getApprovedBy()).isEqualTo(actorId);
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(InventoryTransactionType.WRITE_OFF);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(request.getId().toString());
        assertThat(captor.getValue().getLines().getFirst().getQuantity()).isEqualByComparingTo("-2.00");
        assertThat(captor.getValue().getLines().getFirst().getLocation()).isNull();
        assertThat(request.getLines().getFirst().getPosted()).isTrue();
        verify(inspectionService).completeAfterDisposal(inspection.getId());
    }

    @Test
    void alreadyCompletedRecordCannotCreateAnotherMovement() {
        request.setStatus(DisposalRequestStatus.APPROVED);
        stubLocks();
        service.approveRequest(request.getId());
        verifyNoInteractions(transactionRepository);
        verify(inspectionService).completeAfterDisposal(inspection.getId());
    }

    @Test
    void qcUsesCustomerReturnPostingInsteadOfLegacyApprovalEndpoint() {
        authenticate("QC");
        assertThatThrownBy(() -> service.approveRequest(request.getId())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(requestRepository, inspectionRepository, transactionRepository, inspectionService);
    }

    private void stubLocks() {
        when(requestRepository.findDetailById(request.getId())).thenReturn(Optional.of(request));
        when(inspectionRepository.findByIdAndIsDeletedFalseForUpdate(inspection.getId())).thenReturn(Optional.of(inspection));
        when(requestRepository.findDetailByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
    }

    private void authenticate(String role) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                actorId.toString(), "approval-test", "test", Set.of(role), "test-request");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
