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
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.service.ProductService;
import org.hvtra.commonservice.common_importing.enums.ImportMode;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.common_importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.common_importing.handler.ParsedImportRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductImportHandler implements ImportTypeHandler {

    private static final String CUSTOM_BUNDLE_PRODUCT_CODE = "CUSTOM-BUNDLE";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public ProductImportHandler(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductService productService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
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
        return parseExcel(file, ImportMode.CREATE);
    }

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file, ImportMode mode) {
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
            int colProductCode = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                    if (normalized.contains("ma san pham") || normalized.contains("product code")) {
                        colProductCode = c;
                    } else if (normalized.contains("ma danh muc") || normalized.contains("category code") || normalized.contains("danh muc")) {
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

            List<String[]> rawRows = new ArrayList<>();
            List<Integer> rawRowIndexes = new ArrayList<>();
            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String categoryCode = colCategoryCode >= 0 ? getCellValueAsString(row.getCell(colCategoryCode)) : "";
                String name = colName >= 0 ? getCellValueAsString(row.getCell(colName)) : "";
                String statusStr = colStatus >= 0 ? getCellValueAsString(row.getCell(colStatus)) : "";
                String description = colDescription >= 0 ? getCellValueAsString(row.getCell(colDescription)) : "";
                String productCode = colProductCode >= 0 ? getCellValueAsString(row.getCell(colProductCode)).trim() : "";

                if (name.isBlank() && categoryCode.isBlank() && productCode.isBlank()) {
                    continue;
                }
                rawRows.add(new String[]{categoryCode, name, statusStr, description, productCode});
                rawRowIndexes.add(r + 1);
            }

            Map<String, Product> productsByCode = mode == ImportMode.UPDATE
                    ? loadProductsByCode(rawRows.stream().map(raw -> raw[4]).filter(code -> !code.isBlank()).toList())
                    : Map.of();

            List<ParsedImportRow> rows = new ArrayList<>();
            for (int i = 0; i < rawRows.size(); i++) {
                String[] raw = rawRows.get(i);
                String categoryCode = raw[0];
                String name = raw[1];
                String statusStr = raw[2];
                String description = raw[3];
                String productCode = raw[4];

                Category category = categoryCode.isBlank() ? null : categoriesByCode.get(categoryCode.trim().toUpperCase());
                Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr)
                        ? Status.INACTIVE : Status.ACTIVE;

                Map<String, Object> payload = new HashMap<>();
                payload.put("categoryId", category != null ? category.getId().toString() : null);
                payload.put("name", name.trim());
                payload.put("description", description.isBlank() ? null : description);
                payload.put("status", status.name());
                payload.put("productCode", productCode.isBlank() ? null : productCode);
                payload.put("importMode", mode.name());

                if (mode == ImportMode.UPDATE) {
                    Product existing = productCode.isBlank() ? null : productsByCode.get(productCode.toUpperCase());
                    payload.put("productId", existing != null ? existing.getId().toString() : null);
                    if (existing != null) {
                        payload.put("imageUrl", existing.getImageUrl());
                        if (categoryCode.isBlank() && existing.getCategory() != null) {
                            payload.put("categoryId", existing.getCategory().getId().toString());
                        }
                        if (name.isBlank()) {
                            payload.put("name", existing.getName());
                        }
                        if (description.isBlank()) {
                            payload.put("description", existing.getDescription());
                        }
                        if (statusStr.isBlank() && existing.getStatus() != null) {
                            payload.put("status", existing.getStatus().name());
                        }
                    }
                }

                rows.add(new ParsedImportRow(rawRowIndexes.get(i), payload));
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
                String.valueOf(payload.get("status")),
                String.valueOf(payload.get("productCode")).trim().toLowerCase());
    }

    private Map<String, Product> loadProductsByCode(Collection<String> codes) {
        if (codes.isEmpty()) return Map.of();
        return productRepository.findAllByProductCodeIn(codes).stream()
                .filter(p -> p.getProductCode() != null)
                .collect(Collectors.toMap(p -> p.getProductCode().toUpperCase(), p -> p, (a, b) -> a));
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        List<ValidationErrorResponse> errors = validate(payload, null);
        validateMode(payload, null, errors);
        return errors;
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        // Gộp toàn bộ categoryId của cả batch lại, chỉ tra cứu tồn tại bằng 1 query findAllById()
        // thay vì N query existsById() riêng lẻ (mỗi dòng Excel 1 query).
        Set<UUID> categoryIds = new HashSet<>();
        for (Map<String, Object> payload : payloads) {
            Object categoryIdObj = payload.get("categoryId");
            if (categoryIdObj != null) {
                try {
                    categoryIds.add(UUID.fromString(String.valueOf(categoryIdObj)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Set<UUID> existingCategoryIds = categoryIds.isEmpty()
                ? Set.of()
                : categoryRepository.findAllById(categoryIds).stream().map(Category::getId).collect(Collectors.toSet());

        Map<String, Integer> codeCounts = new HashMap<>();
        for (Map<String, Object> payload : payloads) {
            String code = readProductCode(payload);
            if (code != null) {
                codeCounts.merge(code.toUpperCase(), 1, Integer::sum);
            }
        }
        Map<String, Product> productsByCode = loadProductsByCode(codeCounts.keySet());

        return payloads.stream().map(payload -> {
            List<ValidationErrorResponse> errors = validate(payload, existingCategoryIds);
            validateMode(payload, productsByCode, errors);
            String code = readProductCode(payload);
            if (code != null && codeCounts.getOrDefault(code.toUpperCase(), 0) > 1) {
                errors.add(new ValidationErrorResponse("productCode", "Mã sản phẩm " + code + " bị lặp trong file import"));
            }
            return errors;
        }).toList();
    }

    private void validateMode(
            Map<String, Object> payload,
            Map<String, Product> preloadedByCode,
            List<ValidationErrorResponse> errors) {
        String code = readProductCode(payload);
        Map<String, Product> byCode = preloadedByCode != null ? preloadedByCode
                : code == null ? Map.of() : loadProductsByCode(List.of(code));
        Product existing = code == null ? null : byCode.get(code.toUpperCase());

        if (readMode(payload) == ImportMode.CREATE) {
            if (code == null) return;
            if (code.length() > 50) {
                errors.add(new ValidationErrorResponse("productCode", "Mã sản phẩm tối đa 50 ký tự"));
            }
            if (existing != null) {
                errors.add(new ValidationErrorResponse("productCode",
                        "Mã sản phẩm " + code + " đã tồn tại — để trống để hệ thống tự sinh, hoặc chọn kiểu Cập nhật"));
            }
            return;
        }

        if (code == null) {
            errors.add(new ValidationErrorResponse("productCode", "Kiểu import Cập nhật bắt buộc nhập Mã sản phẩm"));
            return;
        }
        UUID productId = readUuid(payload.get("productId"));
        if (existing == null || Boolean.TRUE.equals(existing.getIsDeleted()) || !existing.getId().equals(productId)) {
            errors.add(new ValidationErrorResponse("productCode", "Mã sản phẩm " + code + " không tồn tại hoặc đã bị xoá"));
            return;
        }
        if (CUSTOM_BUNDLE_PRODUCT_CODE.equalsIgnoreCase(code)) {
            errors.add(new ValidationErrorResponse("productCode", "Không thể cập nhật sản phẩm hệ thống qua import"));
        }
    }

    private List<ValidationErrorResponse> validate(Map<String, Object> payload, Set<UUID> preloadedExistingCategoryIds) {
        ProductRequest request = toRequest(payload);
        List<ValidationErrorResponse> errors = new ArrayList<>();

        for (ConstraintViolation<ProductRequest> violation : validator.validate(request)) {
            errors.add(new ValidationErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()));
        }

        if (request.categoryId() != null) {
            boolean exists = preloadedExistingCategoryIds != null
                    ? preloadedExistingCategoryIds.contains(request.categoryId())
                    : categoryRepository.existsById(request.categoryId());
            if (!exists) {
                errors.add(new ValidationErrorResponse("categoryId", "Danh mục không tồn tại"));
            }
        }

        return errors;
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        ProductRequest request = toRequest(payload);
        if (readMode(payload) == ImportMode.UPDATE) {
            UUID productId = readUuid(payload.get("productId"));
            if (productId == null) {
                throw new BadRequestException("Mã sản phẩm " + readProductCode(payload) + " không tồn tại hoặc đã bị xoá");
            }
            return productService.update(productId, request).id();
        }
        return productService.create(request).id();
    }

    private ProductRequest toRequest(Map<String, Object> payload) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("categoryId", payload.get("categoryId"));
        fields.put("productCode", readProductCode(payload));
        fields.put("name", payload.get("name"));
        fields.put("description", payload.get("description"));
        fields.put("imageUrl", payload.get("imageUrl"));
        fields.put("status", payload.get("status"));
        return objectMapper.convertValue(fields, ProductRequest.class);
    }

    private ImportMode readMode(Map<String, Object> payload) {
        return ImportMode.UPDATE.name().equals(payload.get("importMode")) ? ImportMode.UPDATE : ImportMode.CREATE;
    }

    private String readProductCode(Map<String, Object> payload) {
        Object value = payload.get("productCode");
        if (value == null) return null;
        String code = String.valueOf(value).trim();
        return code.isEmpty() ? null : code;
    }

    private UUID readUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
