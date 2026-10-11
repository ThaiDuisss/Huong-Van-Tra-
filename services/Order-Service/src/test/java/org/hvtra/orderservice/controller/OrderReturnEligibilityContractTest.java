package org.hvtra.orderservice.controller;

import org.hvtra.orderservice.dto.response.OrderReturnEligibilityCheckResponse;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityResponse;
import org.hvtra.orderservice.exception.OrderGlobalExceptionHandler;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderReturnEligibilityContractTest {
    @Mock private OrderService orders;
    @Mock private OrderExportService exports;
    @Mock private OrderReturnService returns;
    @Mock private OrderReturnReportService reports;
    private MockMvc mvc;
    private final UUID orderId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(orders, exports, returns, reports))
                .setValidator(validator).setControllerAdvice(new OrderGlobalExceptionHandler()).build();
    }

    private String invalidQuantityBody() {
        return "{\"items\":[{\"orderItemId\":\"" + itemId
                + "\",\"quantity\":0,\"reason\":\"DAMAGED\",\"condition\":\"DAMAGED\"}]}";
    }

    @Test
    void previewAcceptsInvalidQuantityToReturnSpecificChecks() throws Exception {
        when(returns.checkReturnEligibility(eq(orderId), argThat(request -> request.items().getFirst().getQuantity().signum() == 0)))
                .thenReturn(new OrderReturnEligibilityResponse(false,
                        List.of(new OrderReturnEligibilityCheckResponse("QUANTITY_POSITIVE", false, "Số lượng hoàn phải từ 0,001 trở lên")), List.of()));
        mvc.perform(post("/api/orders/{orderId}/returns/eligibility", orderId)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidQuantityBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.checks[0].code").value("QUANTITY_POSITIVE"))
                .andExpect(jsonPath("$.checks[0].passed").value(false));
        verify(returns).checkReturnEligibility(eq(orderId), argThat(request -> request.items().getFirst().getQuantity().signum() == 0));
    }

    @Test
    void createRejectsInvalidQuantityEvenWithForgedEligibilityFlag() throws Exception {
        String body = invalidQuantityBody().replace("{\"items\":", "{\"eligible\":true,\"items\":");
        mvc.perform(post("/api/orders/{orderId}/returns", orderId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returns);
    }

    @Test
    void previewRequiresItemsProperty() throws Exception {
        mvc.perform(post("/api/orders/{orderId}/returns/eligibility", orderId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returns);
    }
}
