package org.hvtra.orderservice.service.impl;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchRequest;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchResponse;
import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSalesStaffResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductResponse;
import org.hvtra.orderservice.service.OrderStatisticReportService;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticReportExportServiceImplTest {

    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final int HEADER_ROW = OrderStatisticReportExportServiceImpl.HEADER_ROW_INDEX;
    private static final UUID SEN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID NHAI = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private OrderStatisticReportService orderStatisticReportService;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderDateTimeHelper orderDateTimeHelper;

    private OrderStatisticReportExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticReportExportServiceImpl(
                orderStatisticReportService,
                inventoryClient,
                orderDateTimeHelper
        );
        lenient().when(orderDateTimeHelper.today()).thenReturn(LocalDate.of(2026, 10, 2));
    }

    @Test
    void staffExportWritesRankedRowsAndPeriodTotals() throws Exception {
        when(orderStatisticReportService.getTopSalesStaff(FROM_DATE, TO_DATE, "orders", 5)).thenReturn(
                TopSalesStaffReportResponse.builder()
                        .totalStaff(4)
                        .totalOrders(20)
                        .totalRevenue(new BigDecimal("2000000"))
                        .totalItemsSold(new BigDecimal("55"))
                        .averageOrderValue(new BigDecimal("100000"))
                        .totalRefundedOrders(2)
                        .rows(List.of(
                                TopSalesStaffResponse.builder()
                                        .rank(1)
                                        .cashierName("Lan")
                                        .orderCount(12)
                                        .revenue(new BigDecimal("1200000"))
                                        .averageOrderValue(new BigDecimal("100000"))
                                        .itemsSold(new BigDecimal("30.5"))
                                        .refundedOrderCount(1)
                                        .build(),
                                TopSalesStaffResponse.builder()
                                        .rank(2)
                                        .cashierName(null)
                                        .orderCount(3)
                                        .revenue(new BigDecimal("300000"))
                                        .averageOrderValue(new BigDecimal("100000"))
                                        .itemsSold(new BigDecimal("6"))
                                        .build()
                        ))
                        .build()
        );

        byte[] bytes = service.exportTopSalesStaff(FROM_DATE, TO_DATE, "orders", 5);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheet(OrderStatisticReportExportServiceImpl.STAFF_SHEET_NAME);
            assertEquals("Kỳ báo cáo: 01/09/2026 - 30/09/2026", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Nhân viên", sheet.getRow(HEADER_ROW).getCell(1).getStringCellValue());
            assertEquals("Doanh thu", sheet.getRow(HEADER_ROW).getCell(3).getStringCellValue());

            Row first = sheet.getRow(HEADER_ROW + 1);
            assertEquals(1, first.getCell(0).getNumericCellValue());
            assertEquals("Lan", first.getCell(1).getStringCellValue());
            assertEquals(12, first.getCell(2).getNumericCellValue());
            assertEquals(1_200_000, first.getCell(3).getNumericCellValue());
            assertEquals(30.5, first.getCell(5).getNumericCellValue());
            assertEquals(1, first.getCell(6).getNumericCellValue());
            assertEquals(
                    OrderStatisticReportExportServiceImpl.UNKNOWN_CASHIER,
                    sheet.getRow(HEADER_ROW + 2).getCell(1).getStringCellValue()
            );

            Row total = sheet.getRow(HEADER_ROW + 3);
            assertTrue(total.getCell(1).getStringCellValue().contains("4 nhân viên"));
            assertEquals(20, total.getCell(2).getNumericCellValue());
            assertEquals(2_000_000, total.getCell(3).getNumericCellValue());
            assertEquals(55, total.getCell(5).getNumericCellValue());
        }
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void productExportIncludesCurrentStockFromInventory() throws Exception {
        when(orderStatisticReportService.getTopSellingProducts(FROM_DATE, TO_DATE, "quantity", 10))
                .thenReturn(productReport());
        when(inventoryClient.getStocksByVariantIds(any())).thenReturn(List.of(
                new StockVariantBatchResponse(
                        SEN,
                        null,
                        new BigDecimal("18"),
                        new BigDecimal("2"),
                        "IN_STOCK",
                        new BigDecimal("8"),
                        new BigDecimal("10"),
                        null,
                        null
                )
        ));

        byte[] bytes = service.exportTopSellingProducts(FROM_DATE, TO_DATE, "quantity", 10);

        ArgumentCaptor<StockVariantBatchRequest> request = ArgumentCaptor.forClass(StockVariantBatchRequest.class);
        verify(inventoryClient).getStocksByVariantIds(request.capture());
        assertEquals(List.of(SEN, NHAI), request.getValue().variantIds());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(OrderStatisticReportExportServiceImpl.PRODUCT_SHEET_NAME);
            assertEquals("Tồn khả dụng", sheet.getRow(HEADER_ROW).getCell(8).getStringCellValue());

            Row sen = sheet.getRow(HEADER_ROW + 1);
            assertEquals("SEN-100", sen.getCell(1).getStringCellValue());
            assertEquals(30, sen.getCell(4).getNumericCellValue());
            assertEquals(3_000_000, sen.getCell(5).getNumericCellValue());
            assertEquals(12, sen.getCell(6).getNumericCellValue());
            assertEquals(2, sen.getCell(7).getNumericCellValue());
            assertEquals(18, sen.getCell(8).getNumericCellValue());
            assertEquals(2, sen.getCell(9).getNumericCellValue());
            assertEquals(8, sen.getCell(10).getNumericCellValue());
            assertEquals(10, sen.getCell(11).getNumericCellValue());
            assertEquals("Còn hàng", sen.getCell(12).getStringCellValue());

            // Inventory không trả dòng cho biến thể này -> cột tồn kho để trống, không ghi 0.
            Row nhai = sheet.getRow(HEADER_ROW + 2);
            assertEquals(CellType.BLANK, nhai.getCell(8).getCellType());
            assertEquals("", nhai.getCell(12).getStringCellValue());

            Row total = sheet.getRow(HEADER_ROW + 3);
            assertTrue(total.getCell(1).getStringCellValue().contains("8 sản phẩm"));
            assertEquals(75, total.getCell(4).getNumericCellValue());
            assertEquals(9_000_000, total.getCell(5).getNumericCellValue());
            assertEquals(20, total.getCell(6).getNumericCellValue());
        }
    }

    @Test
    void productExportStillProducesFileWithBlankStockWhenInventoryFails() throws Exception {
        when(orderStatisticReportService.getTopSellingProducts(null, null, null, null))
                .thenReturn(productReport());
        when(inventoryClient.getStocksByVariantIds(any()))
                .thenThrow(new IllegalStateException("inventory down"));

        byte[] bytes = service.exportTopSellingProducts(null, null, null, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(OrderStatisticReportExportServiceImpl.PRODUCT_SHEET_NAME);
            assertEquals("Kỳ báo cáo: Toàn bộ thời gian", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(
                    OrderStatisticReportExportServiceImpl.STOCK_UNAVAILABLE_NOTE,
                    sheet.getRow(2).getCell(0).getStringCellValue()
            );
            for (int rowIndex = HEADER_ROW + 1; rowIndex <= HEADER_ROW + 2; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                for (int column = 8; column <= 11; column++) {
                    assertEquals(CellType.BLANK, row.getCell(column).getCellType());
                }
                assertEquals("", row.getCell(12).getStringCellValue());
            }
            assertEquals(30, sheet.getRow(HEADER_ROW + 1).getCell(4).getNumericCellValue());
            assertEquals("NHAI-50", sheet.getRow(HEADER_ROW + 2).getCell(1).getStringCellValue());
        }
    }

    @Test
    void productExportWithoutSalesSkipsInventoryAndWritesOnlyHeaderAndTotals() throws Exception {
        when(orderStatisticReportService.getTopSellingProducts(FROM_DATE, TO_DATE, null, null)).thenReturn(
                TopSellingProductReportResponse.builder()
                        .totalQuantitySold(BigDecimal.ZERO)
                        .totalRevenue(BigDecimal.ZERO)
                        .rows(List.of())
                        .build()
        );

        byte[] bytes = service.exportTopSellingProducts(FROM_DATE, TO_DATE, null, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(HEADER_ROW + 1, sheet.getLastRowNum());
            assertEquals(0, sheet.getRow(HEADER_ROW + 1).getCell(4).getNumericCellValue());
        }
        verifyNoInteractions(inventoryClient);
    }

    private TopSellingProductReportResponse productReport() {
        return TopSellingProductReportResponse.builder()
                .totalProducts(8)
                .totalQuantitySold(new BigDecimal("75"))
                .totalRevenue(new BigDecimal("9000000"))
                .totalOrders(20)
                .rows(List.of(
                        TopSellingProductResponse.builder()
                                .rank(1)
                                .productVariantId(SEN)
                                .sku("SEN-100")
                                .productName("Trà sen")
                                .variantName("Hộp 100g")
                                .quantitySold(new BigDecimal("30"))
                                .revenue(new BigDecimal("3000000"))
                                .orderCount(12)
                                .returnedQuantity(new BigDecimal("2"))
                                .build(),
                        TopSellingProductResponse.builder()
                                .rank(2)
                                .productVariantId(NHAI)
                                .sku("NHAI-50")
                                .productName("Trà nhài")
                                .quantitySold(new BigDecimal("10"))
                                .revenue(new BigDecimal("4000000"))
                                .orderCount(4)
                                .returnedQuantity(BigDecimal.ZERO)
                                .build()
                ))
                .build();
    }
}
