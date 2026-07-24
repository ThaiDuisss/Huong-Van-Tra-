package org.hvtra.orderservice.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExportServiceImplTest {

    @Mock
    private OrderQueryService orderQueryService;

    @Test
    void exportsExistingFiltersSortColumnsAndDataWithoutExtraFields() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID orderId = UUID.randomUUID();
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 24);
        OrderSummaryResponse summary = OrderSummaryResponse.builder()
                .id(orderId)
                .orderCode("ORD-001")
                .statusLabel("Đã hoàn thành")
                .finalAmount(new BigDecimal("123000"))
                .createdAt(LocalDateTime.of(2026, 7, 24, 9, 30))
                .build();
        OrderItemResponse item = OrderItemResponse.builder()
                .productName("Trà sen")
                .quantity(new BigDecimal("2"))
                .actualUnitPrice(new BigDecimal("60000"))
                .lineTotal(new BigDecimal("120000"))
                .build();
        OrderResponse detail = OrderResponse.builder()
                .id(orderId)
                .orderCode("ORD-001")
                .statusLabel("Đã hoàn thành")
                .finalAmount(new BigDecimal("123000"))
                .createdAt(LocalDateTime.of(2026, 7, 24, 9, 30))
                .items(List.of(item))
                .build();

        when(orderQueryService.getOrders(
                "sen",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "POS",
                fromDate,
                toDate,
                "invoicePayment",
                0,
                200
        )).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 200), 1));
        when(orderQueryService.getOrderById(orderId)).thenReturn(detail);

        byte[] bytes = service.exportOrders(
                "sen",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "POS",
                fromDate,
                toDate,
                "invoicePayment"
        );

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            Sheet summarySheet = workbook.getSheet("Danh sách đơn hàng");
            Sheet detailSheet = workbook.getSheet("Chi tiết đơn hàng");

            assertHeader(
                    summarySheet.getRow(0),
                    "Mã đơn hàng",
                    "Trạng thái",
                    "Giá trị đơn hàng",
                    "Ngày đặt"
            );
            assertHeader(
                    detailSheet.getRow(0),
                    "Mã đơn hàng",
                    "Ngày đặt",
                    "Trạng thái",
                    "Tên sản phẩm",
                    "Số lượng",
                    "Đơn giá",
                    "Thành tiền",
                    "Tổng đơn"
            );

            assertEquals(4, summarySheet.getRow(0).getLastCellNum());
            assertEquals(8, detailSheet.getRow(0).getLastCellNum());
            assertArrayEquals(
                    new String[]{"ORD-001", "Đã hoàn thành", "123.000đ", "24/07/2026"},
                    rowValues(summarySheet.getRow(1))
            );
            assertArrayEquals(
                    new String[]{
                            "ORD-001",
                            "24/07/2026",
                            "Đã hoàn thành",
                            "Trà sen",
                            "2",
                            "60.000đ",
                            "120.000đ",
                            "123.000đ"
                    },
                    rowValues(detailSheet.getRow(1))
            );
        }

        verify(orderQueryService).getOrders(
                "sen",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                "POS",
                fromDate,
                toDate,
                "invoicePayment",
                0,
                200
        );
        verify(orderQueryService).getOrderById(orderId);
    }

    @Test
    void blankSortKeepsExistingBusinessStatusDefault() {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        when(orderQueryService.getOrders(
                null,
                null,
                null,
                null,
                null,
                null,
                "businessStatus",
                0,
                200
        )).thenReturn(new PageImpl<>(List.of()));

        service.exportOrders(null, null, null, null, null, null, " ");

        verify(orderQueryService).getOrders(
                null,
                null,
                null,
                null,
                null,
                null,
                "businessStatus",
                0,
                200
        );
    }

    private void assertHeader(Row row, String... expected) {
        assertArrayEquals(expected, rowValues(row));
    }

    private String[] rowValues(Row row) {
        String[] values = new String[row.getLastCellNum()];
        for (int index = 0; index < row.getLastCellNum(); index++) {
            values[index] = row.getCell(index).getStringCellValue();
        }
        return values;
    }
}
