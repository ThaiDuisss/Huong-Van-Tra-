package org.hvtra.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionLineRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.response.OrderReturnInventoryDispositionResponse;
import org.hvtra.orderservice.exception.OrderGlobalExceptionHandler;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderReturnDispositionAuthorizationTest {
    @Mock private OrderService orderService;
    @Mock private OrderExportService orderExportService;
    @Mock private OrderReturnService orderReturnService;
    @Mock private OrderReturnReportService reportService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID returnId;
    private SubmitReturnInventoryDispositionRequest request;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrderController(orderService, orderExportService, orderReturnService, reportService))
                .setControllerAdvice(new OrderGlobalExceptionHandler())
                .build();
        returnId = UUID.randomUUID();
        request = SubmitReturnInventoryDispositionRequest.builder()
                .commandId(UUID.randomUUID())
                .lines(List.of(SubmitReturnInventoryDispositionLineRequest.builder()
                        .returnItemId(UUID.randomUUID())
                        .quantity(BigDecimal.ONE)
                        .result(ReturnInventoryDispositionResult.WRITE_OFF)
                        .build()))
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QC", "QC_STAFF", "MANAGER", "BRANCH_MANAGER", "INVENTORY_MANAGER", "ROLE_QC", "qc"})
    void qcAndManagersCanSubmitWriteOff(String role) throws Exception {
        authenticate(role);
        when(orderReturnService.submitInventoryDisposition(eq(returnId), any()))
                .thenReturn(OrderReturnInventoryDispositionResponse.builder()
                        .commandId(request.getCommandId())
                        .status(OrderReturnInventoryCommandStatus.PENDING)
                        .build());

        mockMvc.perform(post("/api/orders/returns/{returnId}/inventory-disposition", returnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commandId").value(request.getCommandId().toString()));
        verify(orderReturnService).submitInventoryDisposition(eq(returnId), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SALE_STAFF", "INVENTORY_STAFF", "ACCOUNTANT", "CUSTOMER"})
    void otherRolesCannotSubmitQcDisposition(String role) throws Exception {
        authenticate(role);
        mockMvc.perform(post("/api/orders/returns/{returnId}/inventory-disposition", returnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderReturnService);
    }

    private void authenticate(String role) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                UUID.randomUUID().toString(), "qc-test", "test", Set.of(role), "test-request");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
