package org.hvtra.orderservice.controller;

import org.hvtra.orderservice.dto.request.AddDraftBundleItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.service.OrderCustomBundleService;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderQuantityValidationContractTest {
    @Mock private OrderService orders;
    @Mock private PosOfflineCheckoutService offline;
    @Mock private OrderExportService exports;
    @Mock private OrderReturnService returns;
    @Mock private OrderReturnReportService reports;
    @Mock private OrderCustomBundleService bundles;
    private MockMvc mvc;
    private final UUID orderId = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(orders, offline, exports, returns, reports),
                new OrderCustomBundleController(bundles)).setValidator(validator).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "0.0009", "null", "\"NaN\""})
    void rejectsInvalidReturnQuantityBeforeCallingBusinessService(String value) throws Exception {
        mvc.perform(post("/api/orders/{id}/returns", orderId).contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(value)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returns);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "3", "0.001"})
    void validReturnQuantityRetainsExactDomainValueAtControllerBoundary(String value) throws Exception {
        when(returns.createReturn(eq(orderId), any())).thenReturn(OrderReturnResponse.builder().build());
        mvc.perform(post("/api/orders/{id}/returns", orderId).contentType(MediaType.APPLICATION_JSON).content(returnBody(value)))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateOrderReturnRequest> request = ArgumentCaptor.forClass(CreateOrderReturnRequest.class);
        verify(returns).createReturn(eq(orderId), request.capture());
        assertEquals(0, new BigDecimal(value).compareTo(request.getValue().getItems().getFirst().getQuantity()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"items\":[]}", "{\"items\":[{}]}"})
    void missingReturnItemsOrNestedIdentityCannotReachService(String body) throws Exception {
        mvc.perform(post("/api/orders/{id}/returns", orderId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returns);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "null"})
    void nestedBundleQuantityIsValidatedBeforePricingOrPersistence(String value) throws Exception {
        mvc.perform(post("/api/orders/pos/draft/bundle-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"ingredientId\":\"" + lineId + "\",\"quantity\":" + value + "}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bundles);
    }

    @Test
    void validBundleRequestCarriesComponentsInsteadOfInventedVariantId() throws Exception {
        when(bundles.addDraftBundleItem(any())).thenReturn(OrderResponse.builder().build());
        mvc.perform(post("/api/orders/pos/draft/bundle-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"ingredientId\":\"" + lineId + "\",\"quantity\":50}]}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<AddDraftBundleItemRequest> request = ArgumentCaptor.forClass(AddDraftBundleItemRequest.class);
        verify(bundles).addDraftBundleItem(request.capture());
        assertEquals(lineId, request.getValue().getLines().getFirst().getIngredientId());
        assertEquals(0, new BigDecimal("50").compareTo(request.getValue().getLines().getFirst().getQuantity()));
    }

    private String returnBody(String quantity) {
        return "{\"items\":[{\"orderItemId\":\"" + lineId + "\",\"quantity\":" + quantity
                + ",\"reason\":\"DAMAGED\",\"condition\":\"DAMAGED\"}]}";
    }
}
