package org.hvtra.orderservice.service.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.commonservice.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExportServiceImplTest {

    private static final String MONEY_FORMAT = "#,##0 \"đ\"";
    private static final String DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm";

    @Mock
    private OrderQueryService orderQueryService;

    @Test
    void exportsExactlyTwoSheetsWithTypedSummaryAndOneDetailRowPerItem() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID orderId = UUID.randomUUID();
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 24);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 24, 9, 30);
        OrderSummaryResponse summary = summary(
                orderId,
                "ORD202607240930001",
                "Đã hoàn thành",
                new BigDecimal("123000"),
                createdAt
        );
        OrderItemResponse firstItem = OrderItemResponse.builder()
                .skuSnapshot("00123-SEN")
                .productNameSnapshot("Trà sen")
                .variantNameSnapshot("Hộp 100g")
                .quantity(new BigDecimal("2"))
                .actualUnitPrice(new BigDecimal("60000"))
                .originalUnitPrice(new BigDecimal("65000"))
                .lineTotal(new BigDecimal("120000"))
                .build();
        OrderItemResponse secondItem = OrderItemResponse.builder()
                .skuSnapshot("00045-NHAI")
                .productNameSnapshot("Trà nhài")
                .variantNameSnapshot(null)
                .quantity(BigDecimal.ONE)
                .actualUnitPrice(null)
                .originalUnitPrice(new BigDecimal("50000"))
                .lineTotal(null)
                .build();
        OrderResponse detail = detail(
                orderId,
                summary.getOrderCode(),
                "Hoàng Văn Phong",
                "0888888888",
                createdAt,
                new BigDecimal("123000"),
                List.of(firstItem, secondItem)
        );

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
        when(orderQueryService.getOrdersByIdsForExport(List.of(orderId))).thenReturn(List.of(detail));

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
            assertEquals("Danh sách đơn hàng", workbook.getSheetName(0));
            assertEquals("Chi tiết sản phẩm", workbook.getSheetName(1));

            Sheet summarySheet = workbook.getSheetAt(0);
            Sheet detailSheet = workbook.getSheetAt(1);
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
                    "Khách hàng",
                    "SĐT",
                    "Trạng thái đơn",
                    "Thanh toán",
                    "Ngày đặt",
                    "Nghiệp vụ",
                    "SKU",
                    "Tên sản phẩm",
                    "Biến thể",
                    "Số lượng",
                    "Đơn giá",
                    "Thành tiền"
            );
            assertEquals(4, summarySheet.getRow(0).getLastCellNum());
            assertEquals(13, detailSheet.getRow(0).getLastCellNum());
            assertEquals(1, summarySheet.getLastRowNum());
            assertEquals(2, detailSheet.getLastRowNum());
            assertColumnWidths(summarySheet, 30, 22, 20, 22);
            assertColumnWidths(
                    detailSheet,
                    30, 24, 16, 20, 20, 22, 18, 26, 30, 30, 10, 18, 18
            );
            assertHeaderStyle(workbook, summarySheet.getRow(0));
            assertHeaderStyle(workbook, detailSheet.getRow(0));
            assertDetailHeaderGroups(detailSheet.getRow(0));

            Row summaryRow = summarySheet.getRow(1);
            assertText(summaryRow.getCell(0), summary.getOrderCode());
            assertText(summaryRow.getCell(1), "Đã hoàn thành");
            assertMoney(summaryRow.getCell(2), 123000);
            assertDateTime(summaryRow.getCell(3), createdAt);
            assertAlignment(summaryRow.getCell(0), HorizontalAlignment.LEFT, false);
            assertAlignment(summaryRow.getCell(1), HorizontalAlignment.CENTER, false);
            assertAlignment(summaryRow.getCell(2), HorizontalAlignment.RIGHT, false);
            assertAlignment(summaryRow.getCell(3), HorizontalAlignment.CENTER, false);

            Row firstDetailRow = detailSheet.getRow(1);
            assertText(firstDetailRow.getCell(1), "Hoàng Văn Phong");
            assertText(firstDetailRow.getCell(2), "0888888888");
            assertText(firstDetailRow.getCell(4), "Đã thanh toán");
            assertText(firstDetailRow.getCell(6), "Bán tại quầy");
            assertText(firstDetailRow.getCell(7), "00123-SEN");
            assertText(firstDetailRow.getCell(8), "Trà sen");
            assertText(firstDetailRow.getCell(9), "Hộp 100g");
            assertNumeric(firstDetailRow.getCell(10), 2);
            assertMoney(firstDetailRow.getCell(11), 60000);
            assertMoney(firstDetailRow.getCell(12), 120000);
            assertAlignment(firstDetailRow.getCell(1), HorizontalAlignment.LEFT, true);
            assertAlignment(firstDetailRow.getCell(3), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(4), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(5), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(6), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(7), HorizontalAlignment.LEFT, false);
            assertEquals(BorderStyle.MEDIUM, firstDetailRow.getCell(7).getCellStyle().getBorderLeft());
            assertAlignment(firstDetailRow.getCell(8), HorizontalAlignment.LEFT, true);
            assertAlignment(firstDetailRow.getCell(9), HorizontalAlignment.LEFT, true);
            assertAlignment(firstDetailRow.getCell(10), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(11), HorizontalAlignment.RIGHT, false);
            assertAlignment(firstDetailRow.getCell(12), HorizontalAlignment.RIGHT, false);
            assertEquals(21, firstDetailRow.getHeightInPoints(), 0.01);
            assertDetailDoesNotContainFinalAmount(firstDetailRow, 123000);

            Row secondDetailRow = detailSheet.getRow(2);
            assertText(secondDetailRow.getCell(7), "00045-NHAI");
            assertText(secondDetailRow.getCell(9), "");
            assertMoney(secondDetailRow.getCell(11), 50000);
            assertMoney(secondDetailRow.getCell(12), 50000);
            assertDetailDoesNotContainFinalAmount(secondDetailRow, 123000);

            assertTrue(summarySheet.getPaneInformation().isFreezePane());
            assertTrue(detailSheet.getPaneInformation().isFreezePane());
            assertTrue(((XSSFSheet) summarySheet).getCTWorksheet().isSetAutoFilter());
            assertTrue(((XSSFSheet) detailSheet).getCTWorksheet().isSetAutoFilter());
            assertEquals(
                    "A1:M3",
                    ((XSSFSheet) detailSheet).getCTWorksheet().getAutoFilter().getRef()
            );
        }

        verify(orderQueryService).getOrdersByIdsForExport(List.of(orderId));
        verify(orderQueryService, never()).getOrderById(any());
    }

    @Test
    void keepsItemsGroupedByOrderAndWritesOrderWithoutItemsSafely() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 7, 24, 8, 5);
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 7, 24, 9, 10);
        OrderSummaryResponse firstSummary = summary(
                firstOrderId, "ORD-001", "Chờ thanh toán", new BigDecimal("2000000"), firstCreatedAt
        );
        OrderSummaryResponse secondSummary = summary(
                secondOrderId, "ORD-002", "Đã hoàn thành", new BigDecimal("300000"), secondCreatedAt
        );
        OrderItemResponse firstItem = item("SKU-A", "Sản phẩm A", new BigDecimal("1000000"));
        OrderItemResponse secondItem = item("SKU-B", "Sản phẩm B", new BigDecimal("1000000"));
        OrderResponse firstDetail = detail(
                firstOrderId,
                "ORD-001",
                "Khách A",
                "0123456789",
                firstCreatedAt,
                new BigDecimal("2000000"),
                List.of(firstItem, secondItem)
        );
        OrderResponse secondDetail = detail(
                secondOrderId,
                "ORD-002",
                null,
                null,
                secondCreatedAt,
                new BigDecimal("300000"),
                List.of()
        );

        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "businessStatus", 0, 200
        )).thenReturn(new PageImpl<>(List.of(firstSummary, secondSummary)));
        when(orderQueryService.getOrdersByIdsForExport(List.of(firstOrderId, secondOrderId)))
                .thenReturn(List.of(secondDetail, firstDetail));

        byte[] bytes = service.exportOrders(null, null, null, null, null, null, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summarySheet = workbook.getSheet("Danh sách đơn hàng");
            Sheet detailSheet = workbook.getSheet("Chi tiết sản phẩm");

            assertEquals(2, summarySheet.getLastRowNum());
            assertEquals(3, detailSheet.getLastRowNum());
            assertEquals("ORD-001", detailSheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("SKU-A", detailSheet.getRow(1).getCell(7).getStringCellValue());
            assertEquals("ORD-001", detailSheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("SKU-B", detailSheet.getRow(2).getCell(7).getStringCellValue());
            assertEquals("ORD-002", detailSheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("", detailSheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("", detailSheet.getRow(3).getCell(7).getStringCellValue());
            assertEquals(CellType.BLANK, detailSheet.getRow(3).getCell(10).getCellType());
            assertEquals(CellType.BLANK, detailSheet.getRow(3).getCell(11).getCellType());
            assertEquals(CellType.BLANK, detailSheet.getRow(3).getCell(12).getCellType());
        }
    }

    @Test
    void loadsEveryPageAndFetchesDetailsOncePerBatchWithoutChangingSort() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        OrderSummaryResponse firstSummary = summary(
                firstOrderId,
                "ORD-001",
                "Chờ thanh toán",
                new BigDecimal("100000"),
                LocalDateTime.of(2026, 7, 25, 8, 0)
        );
        OrderSummaryResponse secondSummary = summary(
                secondOrderId,
                "ORD-201",
                "Chờ thanh toán",
                new BigDecimal("200000"),
                LocalDateTime.of(2026, 7, 25, 9, 0)
        );

        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "businessStatus", 0, 200
        )).thenReturn(new PageImpl<>(List.of(firstSummary), PageRequest.of(0, 200), 201));
        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "businessStatus", 1, 200
        )).thenReturn(new PageImpl<>(List.of(secondSummary), PageRequest.of(1, 200), 201));
        when(orderQueryService.getOrdersByIdsForExport(List.of(firstOrderId))).thenReturn(List.of(
                detail(firstOrderId, "ORD-001", null, null, firstSummary.getCreatedAt(),
                        firstSummary.getFinalAmount(), List.of())
        ));
        when(orderQueryService.getOrdersByIdsForExport(List.of(secondOrderId))).thenReturn(List.of(
                detail(secondOrderId, "ORD-201", null, null, secondSummary.getCreatedAt(),
                        secondSummary.getFinalAmount(), List.of())
        ));

        byte[] bytes = service.exportOrders(null, null, null, null, null, null, " ");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summarySheet = workbook.getSheet("Danh sách đơn hàng");
            assertEquals("ORD-001", summarySheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("ORD-201", summarySheet.getRow(2).getCell(0).getStringCellValue());
        }

        verify(orderQueryService).getOrders(
                null, null, null, null, null, null, "businessStatus", 0, 200
        );
        verify(orderQueryService).getOrders(
                null, null, null, null, null, null, "businessStatus", 1, 200
        );
        verify(orderQueryService).getOrdersByIdsForExport(List.of(firstOrderId));
        verify(orderQueryService).getOrdersByIdsForExport(List.of(secondOrderId));
        verify(orderQueryService, never()).getOrderById(any());
    }

    @Test
    void keepsZebraRowsContinuousAcrossOrdersAndBatchesWithFixedStyles() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        UUID thirdOrderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 25, 10, 0);
        OrderSummaryResponse firstSummary = summary(
                firstOrderId, "ORD-ZEBRA-001", "Đã hoàn thành", new BigDecimal("100000"), createdAt
        );
        OrderSummaryResponse secondSummary = summary(
                secondOrderId, "ORD-ZEBRA-002", "Đã hoàn thành", new BigDecimal("200000"), createdAt
        );
        OrderSummaryResponse thirdSummary = summary(
                thirdOrderId, "ORD-ZEBRA-201", "Đã hoàn thành", new BigDecimal("300000"), createdAt
        );

        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "businessStatus", 0, 200
        )).thenReturn(new PageImpl<>(
                List.of(firstSummary, secondSummary), PageRequest.of(0, 200), 201
        ));
        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "businessStatus", 1, 200
        )).thenReturn(new PageImpl<>(List.of(thirdSummary), PageRequest.of(1, 200), 201));
        when(orderQueryService.getOrdersByIdsForExport(List.of(firstOrderId, secondOrderId)))
                .thenReturn(List.of(
                        detail(firstOrderId, firstSummary.getOrderCode(), "Khách 1", "0900000001",
                                createdAt, firstSummary.getFinalAmount(),
                                List.of(item("SKU-001", "Sản phẩm thứ nhất", new BigDecimal("100000")))),
                        detail(secondOrderId, secondSummary.getOrderCode(), "Khách 2", "0900000002",
                                createdAt, secondSummary.getFinalAmount(),
                                List.of(item("SKU-002", "Sản phẩm thứ hai", new BigDecimal("200000"))))
                ));
        when(orderQueryService.getOrdersByIdsForExport(List.of(thirdOrderId))).thenReturn(List.of(
                detail(thirdOrderId, thirdSummary.getOrderCode(), "Khách 3", "0900000003",
                        createdAt, thirdSummary.getFinalAmount(),
                        List.of(item("SKU-201", "Sản phẩm qua batch mới", new BigDecimal("300000"))))
        ));

        byte[] bytes = service.exportOrders(null, null, null, null, null, null, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summarySheet = workbook.getSheet("Danh sách đơn hàng");
            Sheet detailSheet = workbook.getSheet("Chi tiết sản phẩm");

            assertZebraRow(summarySheet.getRow(1), "FFFFFFFF");
            assertZebraRow(summarySheet.getRow(2), "FFF3F7F4");
            assertZebraRow(summarySheet.getRow(3), "FFFFFFFF");
            assertZebraRow(detailSheet.getRow(1), "FFFFFFFF");
            assertZebraRow(detailSheet.getRow(2), "FFF3F7F4");
            assertZebraRow(detailSheet.getRow(3), "FFFFFFFF");

            assertTrue(!"FFFFFFFF".equals(fillArgb(summarySheet.getRow(0).getCell(0))));
            assertTrue(!"FFF3F7F4".equals(fillArgb(summarySheet.getRow(0).getCell(0))));
            assertTrue(!"FFFFFFFF".equals(fillArgb(detailSheet.getRow(0).getCell(7))));
            assertTrue(!"FFF3F7F4".equals(fillArgb(detailSheet.getRow(0).getCell(7))));

            assertEquals(
                    summarySheet.getRow(1).getCell(0).getCellStyle().getIndex(),
                    summarySheet.getRow(3).getCell(0).getCellStyle().getIndex()
            );
            assertTrue(
                    summarySheet.getRow(1).getCell(0).getCellStyle().getIndex()
                            != summarySheet.getRow(2).getCell(0).getCellStyle().getIndex()
            );
            assertEquals(
                    detailSheet.getRow(1).getCell(7).getCellStyle().getIndex(),
                    detailSheet.getRow(3).getCell(7).getCellStyle().getIndex()
            );
            assertEquals(18, workbook.getNumCellStyles());

            Row evenDetailRow = detailSheet.getRow(2);
            assertAlignment(evenDetailRow.getCell(5), HorizontalAlignment.CENTER, false);
            assertAlignment(evenDetailRow.getCell(8), HorizontalAlignment.LEFT, true);
            assertAlignment(evenDetailRow.getCell(10), HorizontalAlignment.CENTER, false);
            assertAlignment(evenDetailRow.getCell(11), HorizontalAlignment.RIGHT, false);
            assertEquals(MONEY_FORMAT, evenDetailRow.getCell(11).getCellStyle().getDataFormatString());
            assertEquals(BorderStyle.MEDIUM, evenDetailRow.getCell(7).getCellStyle().getBorderLeft());
        }
    }

    @Test
    void exportsInvoiceSheetsWithUiColumnsTypedCellsAndTwoProductRows() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID orderId = UUID.randomUUID();
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 30);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 30, 14, 25);
        OrderSummaryResponse summary = summary(
                orderId,
                "ORD-INV-001",
                "Đã hoàn thành",
                new BigDecimal("275000"),
                createdAt
        );
        OrderItemResponse firstItem = OrderItemResponse.builder()
                .skuSnapshot("00001-SEN")
                .productNameSnapshot("Trà sen")
                .variantNameSnapshot("Hộp 100g")
                .quantity(new BigDecimal("2"))
                .actualUnitPrice(new BigDecimal("100000"))
                .lineTotal(new BigDecimal("200000"))
                .build();
        OrderItemResponse secondItem = OrderItemResponse.builder()
                .sku("00002-NHAI")
                .productName("Trà nhài")
                .quantity(BigDecimal.ONE)
                .originalUnitPrice(new BigDecimal("75000"))
                .build();
        OrderResponse detail = detail(
                orderId,
                summary.getOrderCode(),
                "Nguyễn Minh An",
                "0012345678",
                createdAt,
                summary.getFinalAmount(),
                List.of(firstItem, secondItem)
        );

        when(orderQueryService.getOrders(
                "sen",
                null,
                PaymentStatus.PAID,
                null,
                fromDate,
                toDate,
                "invoicePayment",
                0,
                200
        )).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 200), 1));
        when(orderQueryService.getOrdersByIdsForExport(List.of(orderId))).thenReturn(List.of(detail));

        byte[] bytes = service.exportInvoices(
                "sen",
                PaymentStatus.PAID,
                fromDate,
                toDate,
                null
        );

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Danh sách hóa đơn", workbook.getSheetName(0));
            assertEquals("Chi tiết sản phẩm", workbook.getSheetName(1));

            Sheet summarySheet = workbook.getSheetAt(0);
            Sheet detailSheet = workbook.getSheetAt(1);
            assertHeader(
                    summarySheet.getRow(0),
                    "Mã hóa đơn",
                    "Thanh toán",
                    "Tổng tiền",
                    "Khách hàng",
                    "Ngày"
            );
            assertHeader(
                    detailSheet.getRow(0),
                    "Mã hóa đơn",
                    "Thanh toán",
                    "Khách hàng",
                    "SĐT",
                    "Trạng thái hóa đơn",
                    "Thời gian",
                    "Nghiệp vụ",
                    "SKU",
                    "Tên sản phẩm",
                    "Biến thể",
                    "Số lượng",
                    "Đơn giá",
                    "Thành tiền"
            );
            assertEquals(5, summarySheet.getRow(0).getLastCellNum());
            assertEquals(13, detailSheet.getRow(0).getLastCellNum());
            assertEquals(1, summarySheet.getLastRowNum());
            assertEquals(2, detailSheet.getLastRowNum());
            assertColumnWidths(summarySheet, 32, 20, 20, 24, 22);
            assertColumnWidths(
                    detailSheet,
                    32, 20, 24, 16, 20, 22, 18, 26, 30, 30, 10, 18, 18
            );
            assertHeaderStyle(workbook, summarySheet.getRow(0));
            assertHeaderStyle(workbook, detailSheet.getRow(0));
            assertDetailHeaderGroups(detailSheet.getRow(0));

            Row summaryRow = summarySheet.getRow(1);
            assertText(summaryRow.getCell(0), "HD-ORD-INV-001");
            assertText(summaryRow.getCell(1), "Đã thanh toán");
            assertMoney(summaryRow.getCell(2), 275000);
            assertText(summaryRow.getCell(3), "Khách hàng");
            assertDateTime(summaryRow.getCell(4), createdAt);
            assertAlignment(summaryRow.getCell(0), HorizontalAlignment.LEFT, false);
            assertAlignment(summaryRow.getCell(1), HorizontalAlignment.CENTER, false);
            assertAlignment(summaryRow.getCell(2), HorizontalAlignment.RIGHT, false);
            assertAlignment(summaryRow.getCell(3), HorizontalAlignment.LEFT, true);
            assertAlignment(summaryRow.getCell(4), HorizontalAlignment.CENTER, false);

            Row firstDetailRow = detailSheet.getRow(1);
            assertText(firstDetailRow.getCell(0), "HD-ORD-INV-001");
            assertText(firstDetailRow.getCell(1), "Đã thanh toán");
            assertText(firstDetailRow.getCell(2), "Nguyễn Minh An");
            assertText(firstDetailRow.getCell(3), "0012345678");
            assertText(firstDetailRow.getCell(4), "Đã hoàn thành");
            assertDateTime(firstDetailRow.getCell(5), createdAt);
            assertText(firstDetailRow.getCell(6), "Bán tại quầy");
            assertText(firstDetailRow.getCell(7), "00001-SEN");
            assertText(firstDetailRow.getCell(8), "Trà sen");
            assertText(firstDetailRow.getCell(9), "Hộp 100g");
            assertNumeric(firstDetailRow.getCell(10), 2);
            assertMoney(firstDetailRow.getCell(11), 100000);
            assertMoney(firstDetailRow.getCell(12), 200000);
            assertAlignment(firstDetailRow.getCell(0), HorizontalAlignment.LEFT, false);
            assertAlignment(firstDetailRow.getCell(1), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(2), HorizontalAlignment.LEFT, true);
            assertAlignment(firstDetailRow.getCell(3), HorizontalAlignment.LEFT, false);
            assertAlignment(firstDetailRow.getCell(4), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(5), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(6), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(7), HorizontalAlignment.LEFT, false);
            assertEquals(BorderStyle.MEDIUM, firstDetailRow.getCell(7).getCellStyle().getBorderLeft());
            assertAlignment(firstDetailRow.getCell(8), HorizontalAlignment.LEFT, true);
            assertAlignment(firstDetailRow.getCell(9), HorizontalAlignment.LEFT, true);
            assertAlignment(firstDetailRow.getCell(10), HorizontalAlignment.CENTER, false);
            assertAlignment(firstDetailRow.getCell(11), HorizontalAlignment.RIGHT, false);
            assertAlignment(firstDetailRow.getCell(12), HorizontalAlignment.RIGHT, false);
            assertDetailDoesNotContainFinalAmount(firstDetailRow, 275000);

            Row secondDetailRow = detailSheet.getRow(2);
            assertText(secondDetailRow.getCell(7), "00002-NHAI");
            assertText(secondDetailRow.getCell(8), "Trà nhài");
            assertMoney(secondDetailRow.getCell(11), 75000);
            assertMoney(secondDetailRow.getCell(12), 75000);
            assertDetailDoesNotContainFinalAmount(secondDetailRow, 275000);

            assertTrue(summarySheet.getPaneInformation().isFreezePane());
            assertTrue(detailSheet.getPaneInformation().isFreezePane());
            assertEquals(
                    "A1:E2",
                    ((XSSFSheet) summarySheet).getCTWorksheet().getAutoFilter().getRef()
            );
            assertEquals(
                    "A1:M3",
                    ((XSSFSheet) detailSheet).getCTWorksheet().getAutoFilter().getRef()
            );
        }

        verify(orderQueryService).getOrdersByIdsForExport(List.of(orderId));
        verify(orderQueryService, never()).getOrderById(any());
    }

    @Test
    void invoiceExportKeepsGroupingAndZebraAcrossBatchesAndHandlesNoItems() throws Exception {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        UUID thirdOrderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 30, 15, 0);
        OrderSummaryResponse firstSummary = summary(
                firstOrderId, "ORD-I-001", "Đã hoàn thành", new BigDecimal("200000"), createdAt
        );
        OrderSummaryResponse secondSummary = summary(
                secondOrderId, "ORD-I-002", "Đã hoàn thành", new BigDecimal("300000"), createdAt
        );
        OrderSummaryResponse thirdSummary = summary(
                thirdOrderId, "ORD-I-201", "Đã hoàn thành", new BigDecimal("400000"), createdAt
        );

        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "invoicePayment", 0, 200
        )).thenReturn(new PageImpl<>(
                List.of(firstSummary, secondSummary), PageRequest.of(0, 200), 201
        ));
        when(orderQueryService.getOrders(
                null, null, null, null, null, null, "invoicePayment", 1, 200
        )).thenReturn(new PageImpl<>(List.of(thirdSummary), PageRequest.of(1, 200), 201));
        when(orderQueryService.getOrdersByIdsForExport(List.of(firstOrderId, secondOrderId)))
                .thenReturn(List.of(
                        detail(
                                secondOrderId,
                                secondSummary.getOrderCode(),
                                null,
                                null,
                                createdAt,
                                secondSummary.getFinalAmount(),
                                List.of()
                        ),
                        detail(
                                firstOrderId,
                                firstSummary.getOrderCode(),
                                "Khách 1",
                                "0900000001",
                                createdAt,
                                firstSummary.getFinalAmount(),
                                List.of(
                                        item("SKU-A", "Sản phẩm A", new BigDecimal("100000")),
                                        item("SKU-B", "Sản phẩm B", new BigDecimal("100000"))
                                )
                        )
                ));
        when(orderQueryService.getOrdersByIdsForExport(List.of(thirdOrderId))).thenReturn(List.of(
                detail(
                        thirdOrderId,
                        thirdSummary.getOrderCode(),
                        "Khách 3",
                        "0900000003",
                        createdAt,
                        thirdSummary.getFinalAmount(),
                        List.of(item("SKU-201", "Sản phẩm batch 2", new BigDecimal("400000")))
                )
        ));

        byte[] bytes = service.exportInvoices(null, null, null, null, " ");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summarySheet = workbook.getSheet("Danh sách hóa đơn");
            Sheet detailSheet = workbook.getSheet("Chi tiết sản phẩm");

            assertEquals(3, summarySheet.getLastRowNum());
            assertEquals(4, detailSheet.getLastRowNum());
            assertText(detailSheet.getRow(1).getCell(0), "HD-ORD-I-001");
            assertText(detailSheet.getRow(1).getCell(7), "SKU-A");
            assertText(detailSheet.getRow(2).getCell(0), "HD-ORD-I-001");
            assertText(detailSheet.getRow(2).getCell(7), "SKU-B");
            assertText(detailSheet.getRow(3).getCell(0), "HD-ORD-I-002");
            assertText(detailSheet.getRow(3).getCell(7), "");
            assertEquals(CellType.BLANK, detailSheet.getRow(3).getCell(10).getCellType());
            assertText(detailSheet.getRow(4).getCell(0), "HD-ORD-I-201");
            assertText(detailSheet.getRow(4).getCell(7), "SKU-201");

            assertZebraRow(summarySheet.getRow(1), "FFFFFFFF");
            assertZebraRow(summarySheet.getRow(2), "FFF3F7F4");
            assertZebraRow(summarySheet.getRow(3), "FFFFFFFF");
            assertZebraRow(detailSheet.getRow(1), "FFFFFFFF");
            assertZebraRow(detailSheet.getRow(2), "FFF3F7F4");
            assertZebraRow(detailSheet.getRow(3), "FFFFFFFF");
            assertZebraRow(detailSheet.getRow(4), "FFF3F7F4");
        }

        verify(orderQueryService).getOrders(
                null, null, null, null, null, null, "invoicePayment", 0, 200
        );
        verify(orderQueryService).getOrders(
                null, null, null, null, null, null, "invoicePayment", 1, 200
        );
        verify(orderQueryService).getOrdersByIdsForExport(List.of(firstOrderId, secondOrderId));
        verify(orderQueryService).getOrdersByIdsForExport(List.of(thirdOrderId));
        verify(orderQueryService, never()).getOrderById(any());
    }

    @Test
    void emptyInvoiceResultRaisesInvoiceSpecificBusinessException() {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        when(orderQueryService.getOrders(
                null, null, PaymentStatus.UNPAID, null, null, null, "invoicePayment", 0, 200
        )).thenReturn(new PageImpl<>(List.of()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.exportInvoices(null, PaymentStatus.UNPAID, null, null, null)
        );

        assertEquals("INVOICE_EXPORT_EMPTY", exception.getCode());
        assertEquals("Không có hóa đơn phù hợp với bộ lọc để xuất file.", exception.getMessage());
        verify(orderQueryService, never()).getOrdersByIdsForExport(any());
    }

    @Test
    void emptyResultRaisesClearBusinessExceptionInsteadOfBlankWorkbook() {
        OrderExportServiceImpl service = new OrderExportServiceImpl(orderQueryService);
        LocalDate fromDate = LocalDate.of(2026, 7, 25);
        LocalDate toDate = LocalDate.of(2026, 7, 25);
        when(orderQueryService.getOrders(
                "Hoang",
                OrderStatus.PENDING_PAYMENT,
                null,
                null,
                fromDate,
                toDate,
                "businessStatus",
                0,
                200
        )).thenReturn(new PageImpl<>(List.of()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.exportOrders(
                        "Hoang",
                        OrderStatus.PENDING_PAYMENT,
                        null,
                        null,
                        fromDate,
                        toDate,
                        null
                )
        );

        assertEquals("ORDER_EXPORT_EMPTY", exception.getCode());
        assertEquals("Không có đơn hàng phù hợp với bộ lọc để xuất file.", exception.getMessage());
        verify(orderQueryService, never()).getOrdersByIdsForExport(any());
    }

    private OrderSummaryResponse summary(
            UUID orderId,
            String orderCode,
            String statusLabel,
            BigDecimal finalAmount,
            LocalDateTime createdAt
    ) {
        return OrderSummaryResponse.builder()
                .id(orderId)
                .orderCode(orderCode)
                .customerName("Khách hàng")
                .customerPhone("0123456789")
                .status(OrderStatus.COMPLETED)
                .statusLabel(statusLabel)
                .paymentStatus(PaymentStatus.PAID)
                .paymentStatusLabel("Đã thanh toán")
                .orderType("POS")
                .orderTypeLabel("Bán tại quầy")
                .finalAmount(finalAmount)
                .createdAt(createdAt)
                .build();
    }

    private OrderResponse detail(
            UUID orderId,
            String orderCode,
            String customerName,
            String customerPhone,
            LocalDateTime createdAt,
            BigDecimal finalAmount,
            List<OrderItemResponse> items
    ) {
        return OrderResponse.builder()
                .id(orderId)
                .orderCode(orderCode)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .status(OrderStatus.COMPLETED)
                .statusLabel("Đã hoàn thành")
                .paymentStatus(PaymentStatus.PAID)
                .paymentStatusLabel("Đã thanh toán")
                .orderType("POS")
                .orderTypeLabel("Bán tại quầy")
                .finalAmount(finalAmount)
                .createdAt(createdAt)
                .items(items)
                .build();
    }

    private OrderItemResponse item(String sku, String name, BigDecimal lineTotal) {
        return OrderItemResponse.builder()
                .skuSnapshot(sku)
                .productNameSnapshot(name)
                .variantNameSnapshot("Biến thể")
                .quantity(BigDecimal.ONE)
                .actualUnitPrice(lineTotal)
                .lineTotal(lineTotal)
                .build();
    }

    private void assertHeader(Row row, String... expected) {
        String[] actual = new String[row.getLastCellNum()];
        for (int index = 0; index < row.getLastCellNum(); index++) {
            actual[index] = row.getCell(index).getStringCellValue();
        }
        assertArrayEquals(expected, actual);
    }

    private void assertColumnWidths(Sheet sheet, int... expectedWidthsInCharacters) {
        for (int index = 0; index < expectedWidthsInCharacters.length; index++) {
            assertTrue(
                    sheet.getColumnWidth(index) >= expectedWidthsInCharacters[index] * 256,
                    "Column " + index + " is narrower than expected"
            );
        }
    }

    private void assertHeaderStyle(Workbook workbook, Row headerRow) {
        assertTrue(headerRow.getHeightInPoints() >= 26);
        for (Cell cell : headerRow) {
            assertTrue(workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold());
            assertTrue(cell.getCellStyle().getWrapText());
            assertEquals(VerticalAlignment.CENTER, cell.getCellStyle().getVerticalAlignment());
            assertEquals(HorizontalAlignment.CENTER, cell.getCellStyle().getAlignment());
            assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
            assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderRight());
            assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
            assertThinOrMediumLeftBorder(cell);
        }
    }

    private void assertDetailHeaderGroups(Row headerRow) {
        short orderGroupColor = headerRow.getCell(0).getCellStyle().getFillForegroundColor();
        short productGroupColor = headerRow.getCell(7).getCellStyle().getFillForegroundColor();
        assertTrue(orderGroupColor != productGroupColor);

        for (int index = 0; index <= 6; index++) {
            assertEquals(orderGroupColor, headerRow.getCell(index).getCellStyle().getFillForegroundColor());
        }
        for (int index = 7; index <= 12; index++) {
            assertEquals(productGroupColor, headerRow.getCell(index).getCellStyle().getFillForegroundColor());
        }
        assertEquals(BorderStyle.MEDIUM, headerRow.getCell(7).getCellStyle().getBorderLeft());
    }

    private void assertDetailDoesNotContainFinalAmount(Row row, double finalAmount) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                assertTrue(
                        Math.abs(cell.getNumericCellValue() - finalAmount) > 0.001,
                        "finalAmount must not be written to the detail sheet"
                );
            }
        }
    }

    private void assertAlignment(Cell cell, HorizontalAlignment horizontal, boolean wrapText) {
        assertEquals(horizontal, cell.getCellStyle().getAlignment());
        assertEquals(VerticalAlignment.CENTER, cell.getCellStyle().getVerticalAlignment());
        assertEquals(wrapText, cell.getCellStyle().getWrapText());
        assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
        assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderRight());
        assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
        assertThinOrMediumLeftBorder(cell);
    }

    private void assertThinOrMediumLeftBorder(Cell cell) {
        BorderStyle borderLeft = cell.getCellStyle().getBorderLeft();
        assertTrue(borderLeft == BorderStyle.THIN || borderLeft == BorderStyle.MEDIUM);
    }

    private void assertZebraRow(Row row, String expectedArgb) {
        for (Cell cell : row) {
            assertEquals(expectedArgb, fillArgb(cell));
        }
    }

    private String fillArgb(Cell cell) {
        return ((XSSFCellStyle) cell.getCellStyle())
                .getFillForegroundXSSFColor()
                .getARGBHex();
    }

    private void assertText(Cell cell, String expected) {
        assertEquals(CellType.STRING, cell.getCellType());
        assertEquals(expected, cell.getStringCellValue());
        assertEquals("@", cell.getCellStyle().getDataFormatString());
    }

    private void assertMoney(Cell cell, double expected) {
        assertNumeric(cell, expected);
        assertEquals(MONEY_FORMAT, cell.getCellStyle().getDataFormatString());
    }

    private void assertNumeric(Cell cell, double expected) {
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertEquals(expected, cell.getNumericCellValue(), 0.001);
    }

    private void assertDateTime(Cell cell, LocalDateTime expected) {
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertTrue(DateUtil.isCellDateFormatted(cell));
        assertEquals(DATE_TIME_FORMAT, cell.getCellStyle().getDataFormatString());
        assertEquals(expected, cell.getLocalDateTimeCellValue());
    }
}
