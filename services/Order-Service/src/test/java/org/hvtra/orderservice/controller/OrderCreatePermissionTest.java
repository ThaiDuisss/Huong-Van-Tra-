package org.hvtra.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.orderservice.dto.response.OrderResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** API tạo đơn POS yêu cầu permission order:create do gateway relay; không tin dữ liệu từ client. */
@ExtendWith(MockitoExtension.class)
class OrderCreatePermissionTest {

    @Mock private OrderService orderService;
    @Mock private OrderExportService orderExportService;
    @Mock private OrderReturnService orderReturnService;
    @Mock private OrderReturnReportService reportService;
    @Mock private PosOfflineCheckoutService posOfflineCheckoutService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(
                        orderService, posOfflineCheckoutService, orderExportService, orderReturnService, reportService))
                .setControllerAdvice(new OrderGlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userWithOrderCreatePermissionCanCheckout() throws Exception {
        authenticate(Set.of("SALE_STAFF"), Set.of("order:create"));
        when(orderService.checkoutDraftOrder(eq(orderId), any()))
                .thenReturn(OrderResponse.builder().id(orderId).build());

        mockMvc.perform(post("/api/orders/pos/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CASH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
        verify(orderService).checkoutDraftOrder(eq(orderId), any());
    }

    @Test
    void userWithoutOrderCreatePermissionGets403EvenWithSaleStaffRole() throws Exception {
        // Gateway mới relay permission (không có order:create): role SALE_STAFF không còn đủ.
        authenticate(Set.of("SALE_STAFF"), Set.of("order:view"));

        mockMvc.perform(post("/api/orders/pos/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CASH"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/orders/pos/offline-checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                                Map.of("productVariantId", UUID.randomUUID().toString(), "quantity", 1))))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService, posOfflineCheckoutService);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        // Không có principal (filter nội bộ đã trả 401 trước đó; ở đây mô phỏng context rỗng).
        mockMvc.perform(post("/api/orders/pos/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CASH"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void legacyGatewayWithoutPermissionHeaderKeepsRoleBasedAccess() throws Exception {
        authenticate(Set.of("SALE_STAFF"), null);
        when(orderService.checkoutDraftOrder(eq(orderId), any()))
                .thenReturn(OrderResponse.builder().id(orderId).build());

        mockMvc.perform(post("/api/orders/pos/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CASH"))))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        authenticate(Set.of("ACCOUNTANT"), null);
        mockMvc.perform(post("/api/orders/pos/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CASH"))))
                .andExpect(status().isForbidden());
    }

    private void authenticate(Set<String> roles, Set<String> permissions) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                UUID.randomUUID().toString(), "cashier", "test", roles, "test-request", permissions);
        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        if (permissions != null) {
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
