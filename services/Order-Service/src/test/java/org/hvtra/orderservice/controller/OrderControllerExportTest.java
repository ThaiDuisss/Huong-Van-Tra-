package org.hvtra.orderservice.controller;

import org.hvtra.orderservice.domain.enums.OrderReturnEligibility;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityStatsResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerExportTest {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    @Mock
    private OrderService orderService;

    @Mock
    private OrderExportService orderExportService;

    @Mock
    private OrderReturnService orderReturnService;

    @Mock
    private OrderReturnReportService orderReturnReportService;

    @Mock
    private PosOfflineCheckoutService posOfflineCheckoutService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(
                orderService,
                posOfflineCheckoutService,
                orderExportService,
                orderReturnService,
                orderReturnReportService
        );
    }

    @Test
    void filteredInvoiceExportUsesOnlySupportedFiltersAndFilteredFilename() {
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 30);
        byte[] content = {1, 2, 3};
        when(orderExportService.exportInvoices(
                "sen",
                PaymentStatus.PAID,
                fromDate,
                toDate,
                "invoicePayment"
        )).thenReturn(content);

        ResponseEntity<byte[]> response = controller.exportInvoices(
                "sen",
                PaymentStatus.PAID,
                fromDate,
                toDate,
                "invoicePayment"
        );

        assertArrayEquals(content, response.getBody());
        assertEquals(XLSX_MEDIA_TYPE, response.getHeaders().getContentType());
        assertTrue(response.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .matches("attachment; filename=\"hoa-don-theo-bo-loc-\\d{8}-\\d{6}\\.xlsx\""));
        verify(orderExportService).exportInvoices(
                "sen",
                PaymentStatus.PAID,
                fromDate,
                toDate,
                "invoicePayment"
        );
    }

    @Test
    void allInvoiceExportKeepsFiltersEmptyAndUsesAllFilename() {
        byte[] content = {4, 5, 6};
        when(orderExportService.exportInvoices(
                null,
                null,
                null,
                null,
                "invoicePayment"
        )).thenReturn(content);

        ResponseEntity<byte[]> response = controller.exportInvoices(
                null,
                null,
                null,
                null,
                "invoicePayment"
        );

        assertArrayEquals(content, response.getBody());
        assertTrue(response.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .matches("attachment; filename=\"tat-ca-hoa-don-\\d{8}-\\d{6}\\.xlsx\""));
    }

    @Test
    void invoiceExportEndpointDoesNotAcceptPageOrSize() throws Exception {
        Method method = OrderController.class.getMethod(
                "exportInvoices",
                String.class,
                PaymentStatus.class,
                LocalDate.class,
                LocalDate.class,
                String.class
        );

        assertEquals(5, method.getParameterCount());
        for (Class<?> parameterType : method.getParameterTypes()) {
            assertFalse(int.class.equals(parameterType) || Integer.class.equals(parameterType));
        }
    }

    @Test
    void getOrdersDelegatesReturnEligibilityFilter() {
        Page<OrderSummaryResponse> expected = Page.empty(PageRequest.of(0, 10));
        when(orderService.getOrders(
                "sen",
                null,
                null,
                null,
                null,
                null,
                OrderReturnEligibility.INELIGIBLE,
                "businessStatus",
                0,
                10
        )).thenReturn(expected);

        Page<OrderSummaryResponse> actual = controller.getOrders(
                "sen",
                null,
                null,
                null,
                null,
                null,
                OrderReturnEligibility.INELIGIBLE,
                "businessStatus",
                0,
                10
        );

        assertEquals(expected, actual);
        verify(orderService).getOrders(
                "sen",
                null,
                null,
                null,
                null,
                null,
                OrderReturnEligibility.INELIGIBLE,
                "businessStatus",
                0,
                10
        );
    }

    @Test
    void getOrderReturnEligibilityStatsDelegatesKeyword() {
        OrderReturnEligibilityStatsResponse expected =
                OrderReturnEligibilityStatsResponse.builder()
                        .total(3)
                        .eligible(1)
                        .ineligible(2)
                        .build();
        when(orderService.getOrderReturnEligibilityStats("sen"))
                .thenReturn(expected);

        OrderReturnEligibilityStatsResponse actual =
                controller.getOrderReturnEligibilityStats("sen");

        assertEquals(expected, actual);
        verify(orderService).getOrderReturnEligibilityStats("sen");
    }

    @Test
    void getReturnsDelegatesGlobalReturnListQueryParams() {
        UUID returnId = UUID.randomUUID();
        OrderReturnSummaryResponse summary = OrderReturnSummaryResponse.builder()
                .id(returnId)
                .returnCode("RET00001")
                .status(ReturnStatus.CREATED)
                .itemCount(2L)
                .build();
        Page<OrderReturnSummaryResponse> expected = new PageImpl<>(
                List.of(summary),
                PageRequest.of(1, 20),
                21
        );
        when(orderReturnService.getReturns(
                "RET00001",
                ReturnStatus.CREATED,
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                "createdAt,desc",
                1,
                20
        )).thenReturn(expected);

        Page<OrderReturnSummaryResponse> actual = controller.getReturns(
                "RET00001",
                ReturnStatus.CREATED,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                "createdAt,desc",
                1,
                20
        );

        assertEquals(expected, actual);
        assertEquals(21, actual.getTotalElements());
        verify(orderReturnService).getReturns(
                "RET00001",
                ReturnStatus.CREATED,
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                "createdAt,desc",
                1,
                20
        );
    }

    @Test
    void receiveReturnDelegatesToOrderReturnService() {
        UUID returnId = UUID.randomUUID();
        OrderReturnResponse expected = OrderReturnResponse.builder()
                .id(returnId)
                .returnCode("RET00001")
                .status(ReturnStatus.RECEIVED)
                .build();
        when(orderReturnService.receiveReturn(returnId)).thenReturn(expected);

        OrderReturnResponse actual = controller.receiveReturn(returnId);

        assertEquals(expected, actual);
        assertEquals(ReturnStatus.RECEIVED, actual.getStatus());
        verify(orderReturnService).receiveReturn(returnId);
    }
}
