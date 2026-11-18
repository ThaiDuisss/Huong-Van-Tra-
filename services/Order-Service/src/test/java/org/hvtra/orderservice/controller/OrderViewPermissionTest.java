package org.hvtra.orderservice.controller;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy cho lỗ hổng Broken Access Control phát hiện qua pentest ngày 2026-11-16: GET /api/orders và
 * GET /api/orders/{orderId} trước đây không kiểm tra permission, nên tài khoản không có order:view
 * (vd. nhân viên kho) vẫn đọc được toàn bộ đơn hàng. Test này khóa lại hành vi phải có order:view.
 */
@ExtendWith(MockitoExtension.class)
class OrderViewPermissionTest {

    @Mock private OrderService orderService;
    @Mock private OrderExportService orderExportService;
    @Mock private OrderReturnService orderReturnService;
    @Mock private OrderReturnReportService reportService;
    @Mock private PosOfflineCheckoutService posOfflineCheckoutService;

    private MockMvc mockMvc;
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
    void userWithOrderViewPermissionCanListOrders() throws Exception {
        authenticate(Set.of("SALE_STAFF"), Set.of("order:view"));
        when(orderService.getOrders(null, null, null, null, null, null, null, "businessStatus", 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutOrderViewPermissionCannotListOrders() throws Exception {
        // Nhân viên kho: có inventory:view nhưng KHÔNG có order:view.
        authenticate(Set.of("INVENTORY_STAFF"), Set.of("inventory:view", "product:view"));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(orderService);
    }

    @Test
    void userWithoutOrderViewPermissionCannotReadOrderDetail() throws Exception {
        authenticate(Set.of("INVENTORY_STAFF"), Set.of("inventory:view", "product:view"));

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void userWithOrderViewPermissionCanReadOrderDetail() throws Exception {
        authenticate(Set.of("SALE_STAFF"), Set.of("order:view"));
        when(orderService.getOrderById(orderId)).thenReturn(OrderResponse.builder().id(orderId).build());

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    private void authenticate(Set<String> roles, Set<String> permissions) {
        InternalUserPrincipal principal = new InternalUserPrincipal(
                UUID.randomUUID().toString(), "tester", "test", roles, "test-request", permissions);
        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
