package org.hvtra.orderservice.service.impl;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OrderExportServiceImpl implements OrderExportService {

    private static final int EXPORT_BATCH_SIZE = 200;
    // Safety limit to avoid generating very large workbooks in one request.
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final String DEFAULT_SORT = "businessStatus";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrderService orderService;

    public OrderExportServiceImpl(OrderService orderService) {
        this.orderService = orderService;
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
        List<OrderSummaryResponse> orders = loadOrdersForExport(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate,
                sort
        );

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            createSummarySheet(workbook, headerStyle, orders);
            createDetailSheet(workbook, headerStyle, orders);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi xuất file Excel đơn hàng: " + exception.getMessage(), exception);
        }
    }

    private void createSummarySheet(
            Workbook workbook,
            CellStyle headerStyle,
            List<OrderSummaryResponse> orders
    ) {
        Sheet sheet = workbook.createSheet("Danh sách đơn hàng");
        String[] headers = {
                "Mã đơn hàng",
                "Trạng thái",
                "Giá trị đơn hàng",
                "Ngày đặt"
        };
        writeHeader(sheet, headerStyle, headers);

        int rowIndex = 1;
        for (OrderSummaryResponse order : orders) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(valueOrDash(order.getOrderCode()));
            row.createCell(1).setCellValue(valueOrDash(order.getStatusLabel()));
            row.createCell(2).setCellValue(formatMoney(order.getFinalAmount()));
            row.createCell(3).setCellValue(formatDate(order));
        }

        finishSheet(sheet, headers.length, rowIndex);
    }

    private void createDetailSheet(
            Workbook workbook,
            CellStyle headerStyle,
            List<OrderSummaryResponse> orders
    ) {
        Sheet sheet = workbook.createSheet("Chi tiết đơn hàng");
        String[] headers = {
                "Mã đơn hàng",
                "Ngày đặt",
                "Trạng thái",
                "Tên sản phẩm",
                "Số lượng",
                "Đơn giá",
                "Thành tiền",
                "Tổng đơn"
        };
        writeHeader(sheet, headerStyle, headers);

        int rowIndex = 1;
        for (OrderSummaryResponse summary : orders) {
            OrderResponse detail = orderService.getOrderById(summary.getId());
            List<OrderItemResponse> items = detail.getItems();

            if (items == null || items.isEmpty()) {
                Row row = sheet.createRow(rowIndex++);
                writeOrderDetailCells(row, detail, null);
                continue;
            }

            for (OrderItemResponse item : items) {
                Row row = sheet.createRow(rowIndex++);
                writeOrderDetailCells(row, detail, item);
            }
        }

        finishSheet(sheet, headers.length, rowIndex);
    }

    private void writeOrderDetailCells(Row row, OrderResponse order, OrderItemResponse item) {
        row.createCell(0).setCellValue(valueOrDash(order.getOrderCode()));
        row.createCell(1).setCellValue(formatDate(order));
        row.createCell(2).setCellValue(valueOrDash(order.getStatusLabel()));
        row.createCell(3).setCellValue(item == null ? "--" : productName(item));
        row.createCell(4).setCellValue(item == null ? "--" : formatQuantity(item.getQuantity()));
        row.createCell(5).setCellValue(item == null ? "--" : formatMoney(itemUnitPrice(item)));
        row.createCell(6).setCellValue(item == null ? "--" : formatMoney(itemLineTotal(item)));
        row.createCell(7).setCellValue(formatMoney(order.getFinalAmount()));
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void finishSheet(Sheet sheet, int columnCount, int rowCount) {
        sheet.createFreezePane(0, 1);
        if (rowCount > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, columnCount - 1));
        }
        for (int index = 0; index < columnCount; index++) {
            try {
                sheet.autoSizeColumn(index);
            } catch (RuntimeException ignored) {
                // Column auto-size may fail in headless environments; export should still succeed.
            }
        }
    }

    private List<OrderSummaryResponse> loadOrdersForExport(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    ) {
        List<OrderSummaryResponse> orders = new ArrayList<>();
        int page = 0;
        boolean hasNext;

        do {
            Page<OrderSummaryResponse> result = orderService.getOrders(
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

            for (OrderSummaryResponse order : result.getContent()) {
                if (orders.size() >= MAX_EXPORT_ROWS) {
                    return orders;
                }
                orders.add(order);
            }

            hasNext = result.hasNext() && !result.getContent().isEmpty();
            page++;
        } while (hasNext && orders.size() < MAX_EXPORT_ROWS);

        return orders;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        return headerStyle;
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "--";
        }
        DecimalFormat formatter = new DecimalFormat(
                "#,##0",
                DecimalFormatSymbols.getInstance(Locale.GERMANY)
        );
        return formatter.format(value) + "đ";
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "--";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatDate(OrderSummaryResponse order) {
        if (order.getCreatedAt() == null) {
            return "--";
        }
        return order.getCreatedAt().format(DATE_FORMATTER);
    }

    private String formatDate(OrderResponse order) {
        if (order.getCreatedAt() == null) {
            return "--";
        }
        return order.getCreatedAt().format(DATE_FORMATTER);
    }

    private String productName(OrderItemResponse item) {
        return valueOrDash(firstNonBlank(item.getProductName(), item.getProductNameSnapshot()));
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

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "--" : value.trim();
    }
}
