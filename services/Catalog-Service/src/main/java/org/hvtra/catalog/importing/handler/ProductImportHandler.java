package org.hvtra.catalog.importing.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.service.ProductService;
import org.hvtra.commonservice.importing.domain.enums.ImportType;
import org.hvtra.commonservice.importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.importing.handler.ParsedImportRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductImportHandler implements ImportTypeHandler {

    private final CategoryRepository categoryRepository;
    private final ProductService productService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public ProductImportHandler(
            CategoryRepository categoryRepository,
            ProductService productService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.productService = productService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportType type() {
        return ImportType.PRODUCT;
    }

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = findDataSheet(workbook);
            int rowCount = sheet.getPhysicalNumberOfRows();
            if (rowCount <= 1) {
                return List.of();
            }

            int headerRowIndex = findHeaderRowIndex(sheet);
            if (headerRowIndex < 0) {
                headerRowIndex = 0;
            }

            Row headerRow = sheet.getRow(headerRowIndex);
            int colCategoryCode = -1;
            int colName = -1;
            int colStatus = -1;
            int colDescription = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                    if (normalized.contains("ma danh muc") || normalized.contains("category code") || normalized.contains("danh muc")) {
                        colCategoryCode = c;
                    } else if (normalized.contains("ten san pham") || normalized.contains("product name") || normalized.contains("ten sp")) {
                        colName = c;
                    } else if (normalized.contains("trang thai") || normalized.contains("status")) {
                        colStatus = c;
                    } else if (normalized.contains("mo ta") || normalized.contains("description")) {
                        colDescription = c;
                    }
                }
            }

            if (colCategoryCode == -1) colCategoryCode = 0;
            if (colName == -1) colName = 1;
            if (colStatus == -1) colStatus = 2;
            if (colDescription == -1) colDescription = 3;

            Map<String, Category> categoriesByCode = categoryRepository.findAll().stream()
                    .filter(c -> c.getCategoryCode() != null)
                    .collect(Collectors.toMap(c -> c.getCategoryCode().toUpperCase().trim(), c -> c, (a, b) -> a));

            List<ParsedImportRow> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String categoryCode = colCategoryCode >= 0 ? getCellValueAsString(row.getCell(colCategoryCode)) : "";
                String name = colName >= 0 ? getCellValueAsString(row.getCell(colName)) : "";
                String statusStr = colStatus >= 0 ? getCellValueAsString(row.getCell(colStatus)) : "";
                String description = colDescription >= 0 ? getCellValueAsString(row.getCell(colDescription)) : "";

                if (name.isBlank() && categoryCode.isBlank()) {
                    continue;
                }

                Category category = categoryCode.isBlank() ? null : categoriesByCode.get(categoryCode.trim().toUpperCase());
                Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr)
                        ? Status.INACTIVE : Status.ACTIVE;

                Map<String, Object> payload = new HashMap<>();
                payload.put("categoryId", category != null ? category.getId().toString() : null);
                payload.put("name", name.trim());
                payload.put("description", description.isBlank() ? null : description);
                payload.put("status", status.name());

                rows.add(new ParsedImportRow(r + 1, payload));
            }

            return mergeDuplicateRows(rows);
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc file Excel sản phẩm: " + e.getMessage());
        }
    }

    /**
     * Các dòng Excel giống hệt nhau ở TẤT CẢ các trường (danh mục, tên, mô tả, trạng thái) chỉ giữ lại
     * dòng xuất hiện đầu tiên; occurrenceCount ghi lại số lần dòng đó lặp lại để trả về FE cảnh báo.
     */
    private List<ParsedImportRow> mergeDuplicateRows(List<ParsedImportRow> rows) {
        Map<String, ParsedImportRow> firstByKey = new LinkedHashMap<>();
        Map<String, Integer> countByKey = new LinkedHashMap<>();
        for (ParsedImportRow row : rows) {
            String key = duplicateKey(row.payload());
            countByKey.merge(key, 1, Integer::sum);
            firstByKey.putIfAbsent(key, row);
        }

        List<ParsedImportRow> merged = new ArrayList<>();
        for (Map.Entry<String, ParsedImportRow> entry : firstByKey.entrySet()) {
            ParsedImportRow first = entry.getValue();
            merged.add(new ParsedImportRow(first.rowIndex(), first.payload(), countByKey.get(entry.getKey())));
        }
        return merged;
    }

    private String duplicateKey(Map<String, Object> payload) {
        return String.join("",
                String.valueOf(payload.get("categoryId")),
                String.valueOf(payload.get("name")).trim().toLowerCase(),
                String.valueOf(payload.get("description")),
                String.valueOf(payload.get("status")));
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        ProductRequest request = toRequest(payload);
        List<ValidationErrorResponse> errors = new ArrayList<>();

        for (ConstraintViolation<ProductRequest> violation : validator.validate(request)) {
            errors.add(new ValidationErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()));
        }

        if (request.categoryId() != null && !categoryRepository.existsById(request.categoryId())) {
            errors.add(new ValidationErrorResponse("categoryId", "Danh mục không tồn tại"));
        }

        return errors;
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        ProductRequest request = toRequest(payload);
        return productService.create(request).id();
    }

    private ProductRequest toRequest(Map<String, Object> payload) {
        return objectMapper.convertValue(payload, ProductRequest.class);
    }

    private Sheet findDataSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (normalize(workbook.getSheetName(i)).contains("nhap thong tin san pham")) {
                return workbook.getSheetAt(i);
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet candidate = workbook.getSheetAt(i);
            if (findHeaderRowIndex(candidate) >= 0) {
                return candidate;
            }
        }
        return workbook.getSheetAt(0);
    }

    private int findHeaderRowIndex(Sheet sheet) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        for (int r = 0; r < Math.min(10, rowCount); r++) {
            Row rRow = sheet.getRow(r);
            if (rRow == null) continue;
            for (int c = 0; c < rRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(rRow.getCell(c)));
                if (normalized.contains("ma danh muc") || normalized.contains("ten san pham")
                        || normalized.contains("category code") || normalized.contains("product name")) {
                    return r;
                }
            }
        }
        return -1;
    }

    private String normalize(String value) {
        String lower = value.toLowerCase().trim();
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                return val == (long) val ? String.format("%d", (long) val) : String.format("%s", val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }
}
