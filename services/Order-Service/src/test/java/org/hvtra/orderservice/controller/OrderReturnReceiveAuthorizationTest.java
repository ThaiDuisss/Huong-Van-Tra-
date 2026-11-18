package org.hvtra.orderservice.controller;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.exception.OrderGlobalExceptionHandler;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderReturnReceiveAuthorizationTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PosOfflineCheckoutService posOfflineCheckoutService;

    @Mock
    private OrderExportService orderExportService;

    @Mock
    private OrderReturnService orderReturnService;

    @Mock
    private OrderReturnReportService orderReturnReportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(
                orderService,
                posOfflineCheckoutService,
                orderExportService,
                orderReturnService,
                orderReturnReportService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OrderGlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Set<String> roles, Set<String> permissions) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                UUID.randomUUID().toString(),
                "test-user",
                "https://auth.hvtra.id.vn/realms/hvt",
                roles,
                "corr-test",
                permissions
        );
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            String authority = Role.fromName(role)
                    .map(Role::authority)
                    .orElse(role.startsWith("ROLE_") ? role : "ROLE_" + role);
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        if (permissions != null) {
            for (String permission : permissions) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities)
        );
    }

    @Test
    void saleStaffIsRejectedWithHttp403EvenWhenInventoryReceivePermissionIsPresent() throws Exception {
        UUID returnId = UUID.randomUUID();
        authenticate(Set.of("SALE_STAFF"), Set.of("order:return", "order:view", "inventory:receive"));

        mockMvc.perform(put("/api/orders/returns/{returnId}/receive", returnId))
                .andExpect(status().isForbidden());

        verify(orderReturnService, never()).receiveReturn(returnId);
    }

    @Test
    void salePosAliasIsRejectedWithHttp403() throws Exception {
        UUID returnId = UUID.randomUUID();
        authenticate(Set.of("SALE_POS"), Set.of("order:return", "inventory:receive"));

        mockMvc.perform(put("/api/orders/returns/{returnId}/receive", returnId))
                .andExpect(status().isForbidden());

        verify(orderReturnService, never()).receiveReturn(returnId);
    }

    @Test
    void unauthenticatedCallerIsRejectedWithHttp403() throws Exception {
        UUID returnId = UUID.randomUUID();
        SecurityContextHolder.clearContext();

        mockMvc.perform(put("/api/orders/returns/{returnId}/receive", returnId))
                .andExpect(status().isForbidden());

        verify(orderReturnService, never()).receiveReturn(returnId);
    }

    @Test
    void qcStaffWithInventoryReceivePermissionSucceedsWithHttp200() throws Exception {
        UUID returnId = UUID.randomUUID();
        authenticate(Set.of("QC_STAFF"), Set.of("inventory:view", "inventory:receive", "inventory:qc"));
        OrderReturnResponse expected = OrderReturnResponse.builder()
                .id(returnId)
                .returnCode("RET-QC-001")
                .status(ReturnStatus.RECEIVED)
                .build();
        when(orderReturnService.receiveReturn(returnId)).thenReturn(expected);

        mockMvc.perform(put("/api/orders/returns/{returnId}/receive", returnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(orderReturnService).receiveReturn(returnId);
    }

    @Test
    void qcStaffWithoutInventoryReceivePermissionIsRejectedWithHttp403() throws Exception {
        UUID returnId = UUID.randomUUID();
        authenticate(Set.of("QC"), Set.of("inventory:view", "inventory:qc"));

        mockMvc.perform(put("/api/orders/returns/{returnId}/receive", returnId))
                .andExpect(status().isForbidden());

        verify(orderReturnService, never()).receiveReturn(returnId);
    }

    @Test
    void superAdminSucceedsWithHttp200() throws Exception {
        UUID returnId = UUID.randomUUID();
        authenticate(Set.of("SUPER_ADMIN"), Set.of());
        OrderReturnResponse expected = OrderReturnResponse.builder()
                .id(returnId)
                .returnCode("RET-ADMIN-001")
                .status(ReturnStatus.RECEIVED)
                .build();
        when(orderReturnService.receiveReturn(returnId)).thenReturn(expected);

        mockMvc.perform(put("/api/orders/returns/{returnId}/receive", returnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(orderReturnService).receiveReturn(returnId);
    }
}
