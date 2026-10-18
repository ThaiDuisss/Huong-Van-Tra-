package org.hvtra.orderservice.controller;

import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;
import org.hvtra.orderservice.service.OrderStatisticReportExportService;
import org.hvtra.orderservice.service.OrderStatisticReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticReportControllerTest {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);

    @Mock
    private OrderStatisticReportService orderStatisticReportService;

    @Mock
    private OrderStatisticReportExportService orderStatisticReportExportService;

    private OrderStatisticReportController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderStatisticReportController(
                orderStatisticReportService,
                orderStatisticReportExportService
        );
    }

    @Test
    void getTopSalesStaffDelegatesFilters() {
        TopSalesStaffReportResponse expected = TopSalesStaffReportResponse.builder().totalOrders(3).build();
        when(orderStatisticReportService.getTopSalesStaff(FROM_DATE, TO_DATE, "orders", 5)).thenReturn(expected);

        assertSame(expected, controller.getTopSalesStaff(FROM_DATE, TO_DATE, "orders", 5));
    }

    @Test
    void getTopSellingProductsDelegatesFilters() {
        TopSellingProductReportResponse expected = TopSellingProductReportResponse.builder().totalOrders(7).build();
        when(orderStatisticReportService.getTopSellingProducts(FROM_DATE, TO_DATE, "revenue", 20))
                .thenReturn(expected);

        assertSame(expected, controller.getTopSellingProducts(FROM_DATE, TO_DATE, "revenue", 20));
    }

    @Test
    void staffExportReturnsXlsxAttachmentWithSameFilters() {
        byte[] content = {1, 2, 3};
        when(orderStatisticReportExportService.exportTopSalesStaff(FROM_DATE, TO_DATE, "revenue", 10))
                .thenReturn(content);

        ResponseEntity<byte[]> response = controller.exportTopSalesStaff(FROM_DATE, TO_DATE, "revenue", 10);

        assertArrayEquals(content, response.getBody());
        assertEquals(XLSX_MEDIA_TYPE, response.getHeaders().getContentType());
        assertTrue(response.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .matches("attachment; filename=\"bao-cao-nhan-vien-ban-hang-\\d{8}-\\d{6}\\.xlsx\""));
    }

    @Test
    void productExportReturnsXlsxAttachmentWithSameFilters() {
        byte[] content = {4, 5, 6};
        when(orderStatisticReportExportService.exportTopSellingProducts(null, null, "quantity", 10))
                .thenReturn(content);

        ResponseEntity<byte[]> response = controller.exportTopSellingProducts(null, null, "quantity", 10);

        assertArrayEquals(content, response.getBody());
        assertEquals(XLSX_MEDIA_TYPE, response.getHeaders().getContentType());
        assertTrue(response.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .matches("attachment; filename=\"bao-cao-san-pham-ban-chay-ton-kho-\\d{8}-\\d{6}\\.xlsx\""));
    }

    @Test
    void endpointsLiveUnderOrderReportsBasePath() {
        assertArrayEquals(
                new String[]{"/api/orders/reports"},
                OrderStatisticReportController.class.getAnnotation(RequestMapping.class).value()
        );
        List<String> paths = Arrays.stream(OrderStatisticReportController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .map(method -> method.getAnnotation(GetMapping.class).value()[0])
                .sorted()
                .toList();

        assertEquals(
                List.of(
                        "/sales-dashboard",
                        "/top-sales-staff",
                        "/top-sales-staff/export",
                        "/top-selling-products",
                        "/top-selling-products/export"
                ),
                paths
        );
    }
}
