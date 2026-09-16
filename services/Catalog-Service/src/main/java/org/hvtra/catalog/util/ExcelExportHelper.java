package org.hvtra.catalog.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ExcelExportHelper {

    private ExcelExportHelper() {}

    public static final byte[] BRAND_GREEN_RGB = new byte[]{(byte) 7, (byte) 136, (byte) 58}; // #07883A
    public static final String COMPANY_NAME = "CỬA HÀNG HƯƠNG VÂN TRÀ";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public record ExportStyles(
            CellStyle brandStyle,
            CellStyle titleStyle,
            CellStyle subtitleStyle,
            CellStyle headerStyle,
            CellStyle textStyle,
            CellStyle centerStyle,
            CellStyle moneyStyle,
            CellStyle numberStyle
    ) {}

    public static ExportStyles createExportStyles(Workbook workbook) {
        // Brand style (Row 0)
        Font brandFont = workbook.createFont();
        brandFont.setBold(true);
        brandFont.setFontHeightInPoints((short) 12);
        brandFont.setFontName("Calibri");
        if (brandFont instanceof XSSFFont xssfFont) {
            xssfFont.setColor(new XSSFColor(BRAND_GREEN_RGB, new DefaultIndexedColorMap()));
        }

        CellStyle brandStyle = workbook.createCellStyle();
        brandStyle.setFont(brandFont);
        brandStyle.setAlignment(HorizontalAlignment.LEFT);
        brandStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Title style (Row 1)
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setFontName("Calibri");

        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.LEFT);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Subtitle style (Row 2 - export timestamp)
        Font subtitleFont = workbook.createFont();
        subtitleFont.setItalic(true);
        subtitleFont.setFontHeightInPoints((short) 10);
        subtitleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        subtitleFont.setFontName("Calibri");

        CellStyle subtitleStyle = workbook.createCellStyle();
        subtitleStyle.setFont(subtitleFont);
        subtitleStyle.setAlignment(HorizontalAlignment.LEFT);
        subtitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Table Header style (Row 4)
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setFontName("Calibri");

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        if (headerStyle instanceof XSSFCellStyle xssfHeaderStyle) {
            xssfHeaderStyle.setFillForegroundColor(new XSSFColor(BRAND_GREEN_RGB, new DefaultIndexedColorMap()));
            xssfHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setWrapText(true);
        applyBorders(headerStyle);

        // Data text style
        Font dataFont = workbook.createFont();
        dataFont.setFontHeightInPoints((short) 11);
        dataFont.setFontName("Calibri");

        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setFont(dataFont);
        textStyle.setAlignment(HorizontalAlignment.LEFT);
        textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(textStyle);

        // Center style (STT, Status, etc.)
        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.setFont(dataFont);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);
        centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(centerStyle);

        // Money style
        DataFormat format = workbook.createDataFormat();
        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setFont(dataFont);
        moneyStyle.setDataFormat(format.getFormat("#,##0 \"đ\""));
        moneyStyle.setAlignment(HorizontalAlignment.RIGHT);
        moneyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(moneyStyle);

        // Number style
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setFont(dataFont);
        numberStyle.setDataFormat(format.getFormat("#,##0"));
        numberStyle.setAlignment(HorizontalAlignment.RIGHT);
        numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(numberStyle);

        return new ExportStyles(
                brandStyle,
                titleStyle,
                subtitleStyle,
                headerStyle,
                textStyle,
                centerStyle,
                moneyStyle,
                numberStyle
        );
    }

    private static void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    public static void createReportHeader(Sheet sheet, ExportStyles styles, String reportTitle, String[] headers) {
        // Row 0: Company Brand Name (Column B = 1)
        Row row0 = sheet.createRow(0);
        row0.setHeightInPoints(20);
        Cell cell0 = row0.createCell(1);
        cell0.setCellValue(COMPANY_NAME);
        cell0.setCellStyle(styles.brandStyle());

        // Row 1: Report Title (Column B = 1)
        Row row1 = sheet.createRow(1);
        row1.setHeightInPoints(26);
        Cell cell1 = row1.createCell(1);
        cell1.setCellValue(reportTitle);
        cell1.setCellStyle(styles.titleStyle());

        // Row 2: Export Time (Column B = 1)
        Row row2 = sheet.createRow(2);
        row2.setHeightInPoints(18);
        Cell cell2 = row2.createCell(1);
        cell2.setCellValue("Thời gian xuất: " + LocalDateTime.now().format(DATE_TIME_FORMATTER));
        cell2.setCellStyle(styles.subtitleStyle());

        // Row 3: Empty spacing row
        Row row3 = sheet.createRow(3);
        row3.setHeightInPoints(10);

        // Row 4: Table Header row starting at Column B (index 1)
        Row headerRow = sheet.createRow(4);
        headerRow.setHeightInPoints(28);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i + 1);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle());
        }
    }

    public static void finalizeSheet(Sheet sheet, int headersCount, int totalRows) {
        // Column A (index 0) is empty margin
        sheet.setColumnWidth(0, 1000);

        // Freeze header pane (freeze rows 0-4, starting data at row 5)
        sheet.createFreezePane(0, 5);

        // Enable auto-filter on table headers (Column 1 to headersCount)
        if (totalRows > 5) {
            sheet.setAutoFilter(new CellRangeAddress(4, totalRows - 1, 1, headersCount));
        }

        // Adjust column widths with comfortable padding for columns 1 to headersCount
        for (int i = 1; i <= headersCount; i++) {
            try {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int paddedWidth = currentWidth + 1400; // Extra breathing room
                if (i == 1) { // STT column
                    paddedWidth = Math.max(paddedWidth, 2400);
                } else {
                    paddedWidth = Math.max(paddedWidth, 3800);
                }
                sheet.setColumnWidth(i, Math.min(paddedWidth, 18000));
            } catch (Exception ignored) {}
        }
    }
}
