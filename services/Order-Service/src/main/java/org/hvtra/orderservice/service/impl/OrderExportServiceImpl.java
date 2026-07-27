package org.hvtra.orderservice.service.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderQueryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderExportServiceImpl implements OrderExportService {

    private static final int EXPORT_BATCH_SIZE = 200;
    private static final int ROW_ACCESS_WINDOW_SIZE = 200;
    private static final String DEFAULT_SORT = "businessStatus";
    private static final String NO_EXPORT_DATA_MESSAGE = "Không có đơn hàng phù hợp với bộ lọc để xuất file.";
    private static final String MONEY_FORMAT = "#,##0 \"đ\"";
    private static final String DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm";
    private static final String QUANTITY_FORMAT = "#,##0.###";
    private static final float HEADER_ROW_HEIGHT = 27;
    private static final float DATA_ROW_HEIGHT = 21;
    private static final byte[] ODD_ROW_RGB = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };
    private static final byte[] EVEN_ROW_RGB = {
            (byte) 0xF3, (byte) 0xF7, (byte) 0xF4
    };
    private static final String[] SUMMARY_HEADERS = {
            "Mã đơn hàng",
            "Trạng thái",
            "Giá trị đơn hàng",
            "Ngày đặt"
    };
    private static final int[] SUMMARY_COLUMN_WIDTHS = {30, 22, 20, 22};
    private static final String[] DETAIL_HEADERS = {
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
    };
    private static final int[] DETAIL_COLUMN_WIDTHS = {
            30, 24, 16, 20, 20, 22, 18, 26, 30, 30, 10, 18, 18
    };

    private final OrderQueryService orderQueryService;

    public OrderExportServiceImpl(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    ) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExportStyles styles = createStyles(workbook);
            Sheet summarySheet = workbook.createSheet("Danh sách đơn hàng");
            Sheet detailSheet = workbook.createSheet("Chi tiết sản phẩm");
            writeHeader(summarySheet, styles.header(), SUMMARY_HEADERS);
            writeDetailHeader(detailSheet, styles);

            ExportRows rows = writeOrdersForExport(
                    summarySheet,
                    detailSheet,
                    styles,
                    keyword,
                    status,
                    paymentStatus,
                    orderType,
                    fromDate,
                    toDate,
                    sort
            );

            if (!rows.hasOrders()) {
                throw new BusinessException("ORDER_EXPORT_EMPTY", NO_EXPORT_DATA_MESSAGE);
            }

            finishSheet(summarySheet, SUMMARY_HEADERS.length, rows.summaryRowIndex(), SUMMARY_COLUMN_WIDTHS);
            finishSheet(detailSheet, DETAIL_HEADERS.length, rows.detailRowIndex(), DETAIL_COLUMN_WIDTHS);

            workbook.write(out);
            return out.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi xuất file Excel đơn hàng: " + exception.getMessage(), exception);
        } finally {
            closeWorkbook(workbook);
        }
    }

    private ExportRows writeOrdersForExport(
            Sheet summarySheet,
            Sheet detailSheet,
            ExportStyles styles,
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    ) {
        int summaryRowIndex = 1;
        int detailRowIndex = 1;
        int page = 0;
        boolean hasNext;
        boolean hasOrders = false;

        do {
            Page<OrderSummaryResponse> result = orderQueryService.getOrders(
                    keyword,
                    status,
                    paymentStatus,
                    orderType,
                    fromDate,
                    toDate,
                    sort == null || sort.isBlank() ? DEFAULT_SORT : sort,
                    page,
                    EXPORT_BATCH_SIZE
            );
            List<OrderSummaryResponse> batch = result.getContent();
            Map<UUID, OrderResponse> detailsByOrderId = loadDetailsByOrderId(batch);

            for (OrderSummaryResponse summary : batch) {
                hasOrders = true;
                Row summaryRow = summarySheet.createRow(summaryRowIndex++);
                summaryRow.setHeightInPoints(DATA_ROW_HEIGHT);
                writeOrderSummaryCells(summaryRow, summary, styles);

                OrderResponse detail = detailsByOrderId.getOrDefault(
                        summary.getId(),
                        detailFromSummary(summary)
                );
                List<OrderItemResponse> items = detail.getItems();

                if (items == null || items.isEmpty()) {
                    Row detailRow = detailSheet.createRow(detailRowIndex++);
                    adjustDetailRowHeight(detailRow, detail, null);
                    writeOrderDetailCells(detailRow, detail, null, styles);
                    continue;
                }

                for (OrderItemResponse item : items) {
                    Row detailRow = detailSheet.createRow(detailRowIndex++);
                    adjustDetailRowHeight(detailRow, detail, item);
                    writeOrderDetailCells(detailRow, detail, item, styles);
                }
            }

            hasNext = result.hasNext() && !batch.isEmpty();
            page++;
        } while (hasNext);

        return new ExportRows(summaryRowIndex, detailRowIndex, hasOrders);
    }

    private Map<UUID, OrderResponse> loadDetailsByOrderId(List<OrderSummaryResponse> batch) {
        if (batch == null || batch.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> orderIds = batch.stream()
                .map(OrderSummaryResponse::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return orderQueryService.getOrdersByIdsForExport(orderIds).stream()
                .collect(Collectors.toMap(
                        OrderResponse::getId,
                        Function.identity(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
    }

    private OrderResponse detailFromSummary(OrderSummaryResponse summary) {
        return OrderResponse.builder()
                .id(summary.getId())
                .orderCode(summary.getOrderCode())
                .customerName(summary.getCustomerName())
                .customerPhone(summary.getCustomerPhone())
                .status(summary.getStatus())
                .statusLabel(summary.getStatusLabel())
                .paymentStatus(summary.getPaymentStatus())
                .paymentStatusLabel(summary.getPaymentStatusLabel())
                .orderType(summary.getOrderType())
                .orderTypeLabel(summary.getOrderTypeLabel())
                .finalAmount(summary.getFinalAmount())
                .createdAt(summary.getCreatedAt())
                .items(Collections.emptyList())
                .build();
    }

    private void writeOrderSummaryCells(Row row, OrderSummaryResponse order, ExportStyles styles) {
        DataStyles dataStyles = styles.forDataRow(row);
        writeTextCell(row, 0, order.getOrderCode(), dataStyles.leftText());
        writeTextCell(row, 1, order.getStatusLabel(), dataStyles.centerText());
        writeNumericCell(row, 2, order.getFinalAmount(), dataStyles.money());
        writeDateTimeCell(row, 3, order.getCreatedAt(), dataStyles.dateTime());
    }

    private void writeOrderDetailCells(
            Row row,
            OrderResponse order,
            OrderItemResponse item,
            ExportStyles styles
    ) {
        DataStyles dataStyles = styles.forDataRow(row);
        writeTextCell(row, 0, order.getOrderCode(), dataStyles.leftText());
        writeTextCell(row, 1, order.getCustomerName(), dataStyles.wrappedText());
        writeTextCell(row, 2, order.getCustomerPhone(), dataStyles.leftText());
        writeTextCell(row, 3, order.getStatusLabel(), dataStyles.centerText());
        writeTextCell(row, 4, order.getPaymentStatusLabel(), dataStyles.centerText());
        writeDateTimeCell(row, 5, order.getCreatedAt(), dataStyles.dateTime());
        writeTextCell(row, 6, order.getOrderTypeLabel(), dataStyles.centerText());
        writeTextCell(row, 7, item == null ? null : itemSku(item), dataStyles.skuText());
        writeTextCell(row, 8, item == null ? null : productName(item), dataStyles.wrappedText());
        writeTextCell(row, 9, item == null ? null : item.getVariantNameSnapshot(), dataStyles.wrappedText());
        writeNumericCell(row, 10, item == null ? null : item.getQuantity(), dataStyles.quantity());
        writeNumericCell(row, 11, item == null ? null : itemUnitPrice(item), dataStyles.money());
        writeNumericCell(row, 12, item == null ? null : itemLineTotal(item), dataStyles.money());
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(HEADER_ROW_HEIGHT);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeDetailHeader(Sheet sheet, ExportStyles styles) {
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(HEADER_ROW_HEIGHT);
        for (int index = 0; index < DETAIL_HEADERS.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(DETAIL_HEADERS[index]);
            if (index == 7) {
                cell.setCellStyle(styles.skuHeader());
            } else if (index > 7) {
                cell.setCellStyle(styles.productHeader());
            } else {
                cell.setCellStyle(styles.header());
            }
        }
    }

    private void writeTextCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellStyle(style);
        cell.setCellValue(value == null ? "" : value.trim());
    }

    private void writeNumericCell(Row row, int columnIndex, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void writeDateTimeCell(Row row, int columnIndex, LocalDateTime value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private void finishSheet(Sheet sheet, int columnCount, int rowCount, int[] columnWidths) {
        sheet.createFreezePane(0, 1);
        if (rowCount > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, columnCount - 1));
        }
        for (int index = 0; index < columnCount; index++) {
            int widthInCharacters = columnWidths[index];
            sheet.setColumnWidth(index, widthInCharacters * 256);
        }
    }

    private ExportStyles createStyles(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setWrapText(true);
        applyThinBorder(headerStyle);

        CellStyle productHeaderStyle = workbook.createCellStyle();
        productHeaderStyle.cloneStyleFrom(headerStyle);
        productHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());

        CellStyle skuHeaderStyle = workbook.createCellStyle();
        skuHeaderStyle.cloneStyleFrom(productHeaderStyle);
        applyGroupDivider(skuHeaderStyle);

        DataStyles oddDataStyles = createDataStyles(workbook, ODD_ROW_RGB);
        DataStyles evenDataStyles = createDataStyles(workbook, EVEN_ROW_RGB);

        return new ExportStyles(
                headerStyle,
                productHeaderStyle,
                skuHeaderStyle,
                oddDataStyles,
                evenDataStyles
        );
    }

    private DataStyles createDataStyles(Workbook workbook, byte[] fillRgb) {
        CellStyle leftTextStyle = createDataStyle(
                workbook, "@", HorizontalAlignment.LEFT, false, false, fillRgb
        );
        CellStyle skuTextStyle = createDataStyle(
                workbook, "@", HorizontalAlignment.LEFT, false, true, fillRgb
        );
        CellStyle wrappedTextStyle = createDataStyle(
                workbook, "@", HorizontalAlignment.LEFT, true, false, fillRgb
        );
        CellStyle centerTextStyle = createDataStyle(
                workbook, "@", HorizontalAlignment.CENTER, false, false, fillRgb
        );
        CellStyle moneyStyle = createDataStyle(
                workbook, MONEY_FORMAT, HorizontalAlignment.RIGHT, false, false, fillRgb
        );
        CellStyle dateTimeStyle = createDataStyle(
                workbook, DATE_TIME_FORMAT, HorizontalAlignment.CENTER, false, false, fillRgb
        );
        CellStyle quantityStyle = createDataStyle(
                workbook, QUANTITY_FORMAT, HorizontalAlignment.CENTER, false, false, fillRgb
        );
        return new DataStyles(
                leftTextStyle,
                skuTextStyle,
                wrappedTextStyle,
                centerTextStyle,
                moneyStyle,
                dateTimeStyle,
                quantityStyle
        );
    }

    private CellStyle createDataStyle(
            Workbook workbook,
            String dataFormat,
            CellStyle style,
            HorizontalAlignment horizontalAlignment,
            boolean wrapText,
            boolean groupDivider,
            byte[] fillRgb
    ) {
        style.setDataFormat(workbook.createDataFormat().getFormat(dataFormat));
        style.setAlignment(horizontalAlignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(wrapText);
        applyThinBorder(style);
        if (groupDivider) {
            applyGroupDivider(style);
        }
        applyFillColor(style, fillRgb);
        return style;
    }

    private CellStyle createDataStyle(
            Workbook workbook,
            String dataFormat,
            HorizontalAlignment horizontalAlignment,
            boolean wrapText,
            boolean groupDivider,
            byte[] fillRgb
    ) {
        return createDataStyle(
                workbook,
                dataFormat,
                workbook.createCellStyle(),
                horizontalAlignment,
                wrapText,
                groupDivider,
                fillRgb
        );
    }

    private void applyFillColor(CellStyle style, byte[] fillRgb) {
        XSSFColor color = new XSSFColor(fillRgb, new DefaultIndexedColorMap());
        ((XSSFCellStyle) style).setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
    }

    private void applyGroupDivider(CellStyle style) {
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
    }

    private void adjustDetailRowHeight(Row row, OrderResponse order, OrderItemResponse item) {
        int requiredLines = wrappedLineCount(order.getCustomerName(), DETAIL_COLUMN_WIDTHS[1]);
        if (item != null) {
            requiredLines = Math.max(
                    requiredLines,
                    wrappedLineCount(productName(item), DETAIL_COLUMN_WIDTHS[8])
            );
            requiredLines = Math.max(
                    requiredLines,
                    wrappedLineCount(item.getVariantNameSnapshot(), DETAIL_COLUMN_WIDTHS[9])
            );
        }
        row.setHeightInPoints(DATA_ROW_HEIGHT * Math.max(requiredLines, 1));
    }

    private int wrappedLineCount(String value, int columnWidthInCharacters) {
        if (value == null || value.isBlank()) {
            return 1;
        }

        int lines = 0;
        for (String explicitLine : value.split("\\R", -1)) {
            lines += Math.max(
                    1,
                    (int) Math.ceil((double) explicitLine.length() / columnWidthInCharacters)
            );
        }
        return lines;
    }

    private void closeWorkbook(SXSSFWorkbook workbook) {
        try {
            workbook.dispose();
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // Export already completed or failed; cleanup should not mask the original outcome.
            }
        }
    }

    private String itemSku(OrderItemResponse item) {
        return firstNonBlank(item.getSkuSnapshot(), item.getSku());
    }

    private String productName(OrderItemResponse item) {
        return firstNonBlank(item.getProductNameSnapshot(), item.getProductName());
    }

    private BigDecimal itemUnitPrice(OrderItemResponse item) {
        return item.getActualUnitPrice() != null ? item.getActualUnitPrice() : item.getOriginalUnitPrice();
    }

    private BigDecimal itemLineTotal(OrderItemResponse item) {
        if (item.getLineTotal() != null) {
            return item.getLineTotal();
        }

        BigDecimal quantity = item.getQuantity();
        BigDecimal unitPrice = itemUnitPrice(item);
        if (quantity == null || unitPrice == null) {
            return null;
        }

        return quantity.multiply(unitPrice);
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private record ExportStyles(
            CellStyle header,
            CellStyle productHeader,
            CellStyle skuHeader,
            DataStyles oddData,
            DataStyles evenData
    ) {
        private DataStyles forDataRow(Row row) {
            boolean evenDataRow = ((row.getRowNum() - 1) % 2 == 1);
            return evenDataRow ? evenData : oddData;
        }
    }

    private record DataStyles(
            CellStyle leftText,
            CellStyle skuText,
            CellStyle wrappedText,
            CellStyle centerText,
            CellStyle money,
            CellStyle dateTime,
            CellStyle quantity
    ) {
    }

    private record ExportRows(
            int summaryRowIndex,
            int detailRowIndex,
            boolean hasOrders
    ) {
    }
}
