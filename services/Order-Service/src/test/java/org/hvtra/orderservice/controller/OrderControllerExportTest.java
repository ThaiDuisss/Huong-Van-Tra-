package org.hvtra.orderservice.controller;

import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.LocalDate;

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

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, orderExportService, orderReturnService);
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
}
