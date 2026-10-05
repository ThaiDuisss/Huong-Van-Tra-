package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchRequest;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchResponse;
import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSalesStaffResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductResponse;
import org.hvtra.orderservice.service.OrderStatisticReportExportService;
import org.hvtra.orderservice.service.OrderStatisticReportService;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatisticReportExportServiceImpl implements OrderStatisticReportExportService {

    static final String STAFF_SHEET_NAME = "Nhân viên bán hàng";
    static final String PRODUCT_SHEET_NAME = "Sản phẩm bán chạy";
    static final String UNKNOWN_CASHIER = "(Không xác định)";
    static final String STOCK_UNAVAILABLE_NOTE =
            "Không lấy được tồn kho từ Inventory-Service tại thời điểm xuất; các cột tồn kho để trống.";
    /** Dòng tiêu đề cột (0-based); phía trên là tiêu đề báo cáo, kỳ báo cáo và ghi chú. */
    static final int HEADER_ROW_INDEX = 4;

    private static final String MONEY_FORMAT = "#,##0 \"đ\"";
    private static final String QUANTITY_FORMAT = "#,##0.###";
    private static final String COUNT_FORMAT = "#,##0";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int STOCK_BATCH_SIZE = 100;

    private static final String[] STAFF_HEADERS = {
            "Hạng",
            "Nhân viên",
            "Số đơn đã thanh toán",
            "Doanh thu",
            "Giá trị đơn trung bình",
            "Số lượng sản phẩm bán",
            "Số đơn có hoàn tiền"
    };
    private static final int[] STAFF_COLUMN_WIDTHS = {8, 32, 22, 20, 24, 24, 22};
    private static final String[] PRODUCT_HEADERS = {
            "Hạng",
            "SKU",
            "Tên sản phẩm",
            "Biến thể",
            "Số lượng bán",
            "Doanh thu",
            "Số đơn",
            "Số lượng trả",
            "Tồn khả dụng",
            "Đang giữ chỗ",
            "Tồn trên kệ",
            "Tồn trong kho",
            "Trạng thái tồn"
    };
    private static final int[] PRODUCT_COLUMN_WIDTHS = {8, 22, 32, 26, 16, 20, 12, 16, 16, 16, 16, 16, 18};

    private final OrderStatisticReportService orderStatisticReportService;
    private final InventoryClient inventoryClient;
    private final OrderDateTimeHelper orderDateTimeHelper;

    @Override
    public byte[] exportTopSalesStaff(LocalDate fromDate, LocalDate toDate, String sortBy, Integer limit) {
        TopSalesStaffReportResponse report =
                orderStatisticReportService.getTopSalesStaff(fromDate, toDate, sortBy, limit);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(STAFF_SHEET_NAME);
            writeTitle(
                    sheet,
                    styles,
                    "BÁO CÁO HIỆU SUẤT NHÂN VIÊN BÁN HÀNG",
                    periodLabel(fromDate, toDate),
                    "Chỉ tính đơn đã thanh toán. Doanh thu là số gộp, chưa trừ tiền hoàn."
            );
            writeHeader(sheet, styles, STAFF_HEADERS);

            int rowIndex = HEADER_ROW_INDEX + 1;
            for (TopSalesStaffResponse staff : safe(report.getRows())) {
                Row row = sheet.createRow(rowIndex++);
                number(row, 0, staff.getRank(), styles.count);
                text(row, 1, staff.getCashierName() == null ? UNKNOWN_CASHIER : staff.getCashierName(), styles.text);
                number(row, 2, staff.getOrderCount(), styles.count);
                number(row, 3, staff.getRevenue(), styles.money);
                number(row, 4, staff.getAverageOrderValue(), styles.money);
                number(row, 5, staff.getItemsSold(), styles.quantity);
                number(row, 6, staff.getRefundedOrderCount(), styles.count);
            }

            Row total = sheet.createRow(rowIndex);
            text(total, 0, "", styles.totalText);
            text(total, 1, "Tổng toàn kỳ (" + report.getTotalStaff() + " nhân viên)", styles.totalText);
            number(total, 2, report.getTotalOrders(), styles.totalCount);
            number(total, 3, report.getTotalRevenue(), styles.totalMoney);
            number(total, 4, report.getAverageOrderValue(), styles.totalMoney);
            number(total, 5, report.getTotalItemsSold(), styles.totalQuantity);
            number(total, 6, report.getTotalRefundedOrders(), styles.totalCount);

            finishSheet(sheet, STAFF_COLUMN_WIDTHS);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Lỗi xuất file Excel báo cáo nhân viên bán hàng: " + exception.getMessage(),
                    exception
            );
        }
    }

    @Override
    public byte[] exportTopSellingProducts(LocalDate fromDate, LocalDate toDate, String sortBy, Integer limit) {
        TopSellingProductReportResponse report =
                orderStatisticReportService.getTopSellingProducts(fromDate, toDate, sortBy, limit);
        List<TopSellingProductResponse> products = safe(report.getRows());
        // null = Inventory-Service lỗi: vẫn xuất file, để trống các cột tồn kho.
        Map<UUID, StockVariantBatchResponse> stockByVariant = loadStock(products);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(PRODUCT_SHEET_NAME);
            writeTitle(
                    sheet,
                    styles,
                    "BÁO CÁO SẢN PHẨM BÁN CHẠY VÀ TỒN KHO",
                    periodLabel(fromDate, toDate),
                    stockByVariant == null
                            ? STOCK_UNAVAILABLE_NOTE
                            : "Chỉ tính đơn đã thanh toán; số lượng và doanh thu là số gộp. Tồn kho lấy tại thời điểm xuất ("
                                    + orderDateTimeHelper.today().format(DATE_FORMAT) + ")."
            );
            writeHeader(sheet, styles, PRODUCT_HEADERS);

            int rowIndex = HEADER_ROW_INDEX + 1;
            for (TopSellingProductResponse product : products) {
                StockVariantBatchResponse stock = stockByVariant == null
                        ? null
                        : stockByVariant.get(product.getProductVariantId());
                Row row = sheet.createRow(rowIndex++);
                number(row, 0, product.getRank(), styles.count);
                text(row, 1, product.getSku(), styles.text);
                text(row, 2, product.getProductName(), styles.text);
                text(row, 3, product.getVariantName(), styles.text);
                number(row, 4, product.getQuantitySold(), styles.quantity);
                number(row, 5, product.getRevenue(), styles.money);
                number(row, 6, product.getOrderCount(), styles.count);
                number(row, 7, product.getReturnedQuantity(), styles.quantity);
                number(row, 8, stock == null ? null : stock.availableQuantity(), styles.quantity);
                number(row, 9, stock == null ? null : stock.reservedQuantity(), styles.quantity);
                number(row, 10, stock == null ? null : stock.shelfQuantity(), styles.quantity);
                number(row, 11, stock == null ? null : stock.warehouseQuantity(), styles.quantity);
                text(row, 12, stock == null ? null : stockStatusLabel(stock.stockStatus()), styles.text);
            }

            Row total = sheet.createRow(rowIndex);
            text(total, 0, "", styles.totalText);
            text(total, 1, "Tổng toàn kỳ (" + report.getTotalProducts() + " sản phẩm)", styles.totalText);
            text(total, 2, "", styles.totalText);
            text(total, 3, "", styles.totalText);
            number(total, 4, report.getTotalQuantitySold(), styles.totalQuantity);
            number(total, 5, report.getTotalRevenue(), styles.totalMoney);
            number(total, 6, report.getTotalOrders(), styles.totalCount);
            for (int column = 7; column < PRODUCT_HEADERS.length; column++) {
                text(total, column, "", styles.totalText);
            }

            finishSheet(sheet, PRODUCT_COLUMN_WIDTHS);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Lỗi xuất file Excel báo cáo sản phẩm bán chạy: " + exception.getMessage(),
                    exception
            );
        }
    }

    private Map<UUID, StockVariantBatchResponse> loadStock(List<TopSellingProductResponse> products) {
        List<UUID> variantIds = products.stream()
                .map(TopSellingProductResponse::getProductVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, StockVariantBatchResponse> result = new HashMap<>();
        if (variantIds.isEmpty()) {
            return result;
        }
        try {
            for (int index = 0; index < variantIds.size(); index += STOCK_BATCH_SIZE) {
                List<UUID> chunk = variantIds.subList(index, Math.min(index + STOCK_BATCH_SIZE, variantIds.size()));
                List<StockVariantBatchResponse> responses =
                        inventoryClient.getStocksByVariantIds(new StockVariantBatchRequest(chunk));
                if (responses == null) {
                    continue;
                }
                responses.stream()
                        .filter(stock -> stock != null && stock.variantId() != null)
                        .forEach(stock -> result.putIfAbsent(stock.variantId(), stock));
            }
            return result;
        } catch (Exception exception) {
            log.warn(
                    "Khong lay duoc ton kho cho bao cao san pham ban chay ({} bien the): {}",
                    variantIds.size(),
                    exception.getMessage()
            );
            return null;
        }
    }

    private String stockStatusLabel(String stockStatus) {
        if ("IN_STOCK".equals(stockStatus)) {
            return "Còn hàng";
        }
        if ("OUT_OF_STOCK".equals(stockStatus)) {
            return "Hết hàng";
        }
        return stockStatus;
    }

    private String periodLabel(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return "Kỳ báo cáo: Toàn bộ thời gian";
        }
        if (fromDate == null) {
            return "Kỳ báo cáo: đến " + toDate.format(DATE_FORMAT);
        }
        if (toDate == null) {
            return "Kỳ báo cáo: từ " + fromDate.format(DATE_FORMAT);
        }
        return "Kỳ báo cáo: " + fromDate.format(DATE_FORMAT) + " - " + toDate.format(DATE_FORMAT);
    }

    private void writeTitle(Sheet sheet, Styles styles, String title, String period, String note) {
        text(sheet.createRow(0), 0, title, styles.title);
        text(sheet.createRow(1), 0, period, styles.note);
        text(sheet.createRow(2), 0, note, styles.note);
    }

    private void writeHeader(Sheet sheet, Styles styles, String[] headers) {
        Row row = sheet.createRow(HEADER_ROW_INDEX);
        row.setHeightInPoints(27);
        for (int index = 0; index < headers.length; index++) {
            text(row, index, headers[index], styles.header);
        }
    }

    private void finishSheet(Sheet sheet, int[] columnWidths) {
        sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
        for (int index = 0; index < columnWidths.length; index++) {
            sheet.setColumnWidth(index, columnWidths[index] * 256);
        }
    }

    private void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        cell.setCellValue(value == null ? "" : value);
    }

    private void number(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void number(Row row, int column, long value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle note;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle count;
        private final CellStyle money;
        private final CellStyle quantity;
        private final CellStyle totalText;
        private final CellStyle totalCount;
        private final CellStyle totalMoney;
        private final CellStyle totalQuantity;

        private Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            note = workbook.createCellStyle();

            header = bordered(workbook, null, HorizontalAlignment.CENTER);
            header.setFont(bold);
            header.setWrapText(true);
            header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            text = bordered(workbook, "@", HorizontalAlignment.LEFT);
            count = bordered(workbook, COUNT_FORMAT, HorizontalAlignment.RIGHT);
            money = bordered(workbook, MONEY_FORMAT, HorizontalAlignment.RIGHT);
            quantity = bordered(workbook, QUANTITY_FORMAT, HorizontalAlignment.RIGHT);

            totalText = total(bordered(workbook, "@", HorizontalAlignment.LEFT), bold);
            totalCount = total(bordered(workbook, COUNT_FORMAT, HorizontalAlignment.RIGHT), bold);
            totalMoney = total(bordered(workbook, MONEY_FORMAT, HorizontalAlignment.RIGHT), bold);
            totalQuantity = total(bordered(workbook, QUANTITY_FORMAT, HorizontalAlignment.RIGHT), bold);
        }

        private static CellStyle bordered(Workbook workbook, String format, HorizontalAlignment alignment) {
            CellStyle style = workbook.createCellStyle();
            if (format != null) {
                style.setDataFormat(workbook.createDataFormat().getFormat(format));
            }
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            return style;
        }

        private static CellStyle total(CellStyle style, Font bold) {
            style.setFont(bold);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }
    }
}
