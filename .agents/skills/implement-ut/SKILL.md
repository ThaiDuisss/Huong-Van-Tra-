---
name: implement-ut
description: >-
  Use this skill ONLY when the user explicitly requests writing, expanding, or refactoring unit tests.
  Currently, unit testing is deferred by user preference. When activated, enforces Java style rules
  (no FQCNs), Mockito service tests, MockMvc controller contract tests, and Vitest frontend tests.
---

# Implement Unit Test (UT) Skill

> [!IMPORTANT]
> **Trạng thái hiện tại:** Theo yêu cầu của người dùng, **tạm thời chưa viết unit test** cho các tính năng mới để tập trung hoàn thiện UI, luồng nghiệp vụ và tích hợp hệ thống.
> Chỉ sử dụng skill này khi người dùng **yêu cầu rõ ràng** việc viết thêm test.

Hướng dẫn chi tiết quy chuẩn và mẫu code khi viết Unit Test cho hệ thống **Hương Vân Trà** (Java 21 / Spring Boot 3.5.x & TypeScript / Vitest).

---

## 1. Quy tắc bắt buộc về Java Code Style (Quy định dự án)

> [!CAUTION]
> **Tuân thủ tuyệt đối quy định trong [`.agents/AGENTS.md`](file:///Users/nguyenan/Documents/Spring%20boot/Huong-Van-Tra-/.agents/AGENTS.md):**
> - **KHÔNG ĐƯỢC DÙNG Fully Qualified Class Names (FQCN)** trực tiếp trong method signature, fields, generics hoặc biến cục bộ (ví dụ: cấm viết `PageResponse<org.hvtra.catalog.dto.response.LookupResponse>`).
> - **LUÔN DÙNG standard Java imports** ở đầu file và sử dụng tên class ngắn gọn (ví dụ: `import org.hvtra.catalog.dto.response.LookupResponse;` và dùng `PageResponse<LookupResponse>`).
> - Ngoại lệ duy nhất: Khi JPA JPQL query bắt buộc cú pháp `SELECT new org.hvtra...` trong annotation `@Query`.

---

## 2. Viết Unit Test cho Service Layer (Mockito + JUnit 5)

Sử dụng `@ExtendWith(MockitoExtension.class)` để test độc lập tầng Service mà không cần khởi động Spring context.

### Template mẫu chuẩn cho Service Test:

```java
package org.hvtra.orderservice.service.impl;

import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl Unit Tests")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID orderId;
    private Order mockOrder;
    private OrderResponse mockResponse;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        mockOrder = Order.builder()
                .id(orderId)
                .status(OrderStatus.PENDING_PAYMENT)
                .finalAmount(new BigDecimal("150000.00"))
                .build();

        mockResponse = OrderResponse.builder()
                .id(orderId)
                .status(OrderStatus.PENDING_PAYMENT)
                .finalAmount(new BigDecimal("150000.00"))
                .build();
    }

    @Nested
    @DisplayName("findById tests")
    class FindByIdTests {

        @Test
        @DisplayName("findById returns OrderResponse when found")
        void findById_success() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(mockOrder));
            when(orderMapper.toResponse(mockOrder)).thenReturn(mockResponse);

            OrderResponse actual = orderService.findById(orderId);

            assertNotNull(actual);
            assertEquals(orderId, actual.getId());
            verify(orderRepository).findById(orderId);
            verify(orderMapper).toResponse(mockOrder);
        }

        @Test
        @DisplayName("findById throws ResourceNotFoundException when not found")
        void findById_notFound_throwsException() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> orderService.findById(orderId));
            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    @DisplayName("createOrder tests")
    class CreateOrderTests {

        @Test
        @DisplayName("createOrder persists order with correct captured values")
        void createOrder_success() {
            CreateOrderRequest request = new CreateOrderRequest();
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(orderMapper.toResponse(any(Order.class))).thenReturn(mockResponse);

            OrderResponse response = orderService.createOrder(request);

            assertNotNull(response);
            verify(orderRepository).save(captor.capture());
            Order savedOrder = captor.getValue();
            assertNotNull(savedOrder);
        }
    }
}
```

---

## 3. Viết Unit Test cho Controller Layer (MockMvc Standalone)

Sử dụng `MockMvcBuilders.standaloneSetup()` kết hợp với `LocalValidatorFactoryBean` và cấu hình Jackson để kiểm tra hợp đồng API endpoint, validation và envelope response `ApiResponse<T>`.

### Template mẫu chuẩn cho Controller Test:

```java
package org.hvtra.promotionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionController Contract Tests")
class PromotionControllerTest {

    private static final String BASE_PATH = "/api/promotions";

    @Mock
    private PromotionService promotionService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PromotionController(promotionService))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("POST /api/promotions creates promotion with valid body")
    void createPromotion_success() throws Exception {
        UUID promotionId = UUID.randomUUID();
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Chương trình Tết 2026")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.AUTO)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .build();

        PromotionResponse response = PromotionResponse.builder()
                .id(promotionId)
                .name("Chương trình Tết 2026")
                .status(PromotionStatus.DRAFT)
                .build();

        when(promotionService.createPromotion(any(PromotionCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(promotionId.toString()));

        verify(promotionService).createPromotion(any(PromotionCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/promotions returns 400 Bad Request when missing required fields")
    void createPromotion_missingFields_returns400() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

---

## 4. Viết Unit Test cho Frontend (TypeScript / Vitest)

Viết test cho các hàm API, data transformations và filter builder functions:

```typescript
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/api/api-client";
import { exportInvoices } from "../services/order.api";
import { buildActiveOrderExportParams } from "../utils/order-export-filters";

vi.mock("@/api/api-client", () => ({
    apiClient: {
        download: vi.fn(),
        get: vi.fn(),
        post: vi.fn()
    }
}));

describe("Order API & Filter Builder Tests", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it("cleans empty and inactive filter values", () => {
        const params = buildActiveOrderExportParams({
            keyword: "   ",
            paymentStatus: "ALL",
            status: "ALL"
        });

        expect(params).toEqual({});
    });

    it("calls apiClient.download with proper normalized query params", () => {
        exportInvoices({
            fromDate: "2026-07-01",
            keyword: "Trà Sen",
            paymentStatus: "PAID"
        });

        expect(apiClient.download).toHaveBeenCalledWith(
            "/orders/invoices/export/excel",
            {
                query: {
                    fromDate: "2026-07-01",
                    keyword: "Trà Sen",
                    paymentStatus: "PAID"
                }
            }
        );
    });
});
```

---

## 5. Checklist khi hoàn thành viết UT

- [ ] Toàn bộ class Java được import chuẩn xác, không chứa FQCN trong method signature hoặc fields.
- [ ] Phủ đầy đủ Happy path (trường hợp thành công) và Sad paths (NotFound, Invalid Input, Forbidden, Conflict).
- [ ] Có assert rõ ràng trên các trường dữ liệu quan trọng và verify số lần gọi repository/client.
- [ ] Chạy lệnh `mvn -pl services/<ServiceName> test` và `pnpm test` đều PASS 100%.
