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
import org.hvtra.catalog.dto.request.BomItemRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.BomItemService;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.common_importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.common_importing.handler.ParsedImportRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Import Mặt hàng kèm BOM: sheet "Mặt hàng" (1 dòng = 1 mặt hàng, có cột Ref tự đặt để liên kết)
 * và sheet "BOM" (nhiều dòng nguyên liệu, mỗi dòng có cột "Ref mặt hàng" trỏ về Ref ở sheet Mặt hàng).
 * Mỗi ImportBatchItem tương ứng 1 mặt hàng, payload gồm field của ProductVariantRequest + productId
 * (resolve sẵn từ Mã sản phẩm) + mảng bomItems (resolve sẵn từ sheet BOM).
 */
@Component
public class ProductVariantImportHandler implements ImportTypeHandler {

    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductVariantService productVariantService;
    private final BomItemService bomItemService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public ProductVariantImportHandler(
            ProductRepository productRepository,
            UnitRepository unitRepository,
            IngredientRepository ingredientRepository,
            ProductVariantService productVariantService,
            BomItemService bomItemService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.ingredientRepository = ingredientRepository;
        this.productVariantService = productVariantService;
        this.bomItemService = bomItemService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportType type() {
        return ImportType.PRODUCT_VARIANT;
    }

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet variantSheet = findSheetByName(workbook, "mat hang");
            if (variantSheet == null) {
                variantSheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            Sheet bomSheet = findSheetByName(workbook, "bom");

            Map<String, Product> productsByCode = productRepository.findAll().stream()
                    .filter(p -> p.getProductCode() != null && !Boolean.TRUE.equals(p.getIsDeleted()))
                    .collect(Collectors.toMap(p -> p.getProductCode().toUpperCase().trim(), p -> p, (a, b) -> a));
            List<Unit> units = unitRepository.findAll().stream()
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .toList();
            Map<String, Ingredient> ingredientsByCode = ingredientRepository.findAll().stream()
                    .filter(i -> i.getIngredientCode() != null && !Boolean.TRUE.equals(i.getIsDeleted()))
                    .collect(Collectors.toMap(i -> i.getIngredientCode().toUpperCase().trim(), i -> i, (a, b) -> a));

            LinkedHashMap<String, Map<String, Object>> payloadsByRef = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> rowIndexByRef = new LinkedHashMap<>();

            if (variantSheet != null) {
                parseVariantSheet(variantSheet, productsByCode, units, payloadsByRef, rowIndexByRef);
            }
            if (bomSheet != null) {
                parseBomSheet(bomSheet, ingredientsByCode, units, payloadsByRef);
            }

            List<ParsedImportRow> rows = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : payloadsByRef.entrySet()) {
                rows.add(new ParsedImportRow(rowIndexByRef.get(entry.getKey()), entry.getValue()));
            }
            return rows;
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc file Excel mặt hàng: " + e.getMessage());
        }
    }

    private void parseVariantSheet(
            Sheet sheet,
            Map<String, Product> productsByCode,
            List<Unit> units,
            Map<String, Map<String, Object>> payloadsByRef,
            Map<String, Integer> rowIndexByRef) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        if (rowCount <= 1) return;

        int headerRowIndex = findHeaderRowIndex(sheet, "ma san pham", "dinh luong");
        Row headerRow = sheet.getRow(headerRowIndex);
        int colRef = -1, colProductCode = -1, colQuantity = -1, colUnit = -1,
                colSellingPrice = -1, colCostPrice = -1, colPolicy = -1, colStatus = -1;

        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                if (normalized.equals("ref") || normalized.contains("ref")) {
                    colRef = c;
                } else if (normalized.contains("ma san pham") || normalized.contains("ma sp")) {
                    colProductCode = c;
                } else if (normalized.contains("dinh luong")) {
                    colQuantity = c;
                } else if (normalized.contains("don vi")) {
                    colUnit = c;
                } else if (normalized.contains("gia ban")) {
                    colSellingPrice = c;
                } else if (normalized.contains("gia von")) {
                    colCostPrice = c;
                } else if (normalized.contains("chinh sach") || normalized.contains("ton kho")) {
                    colPolicy = c;
                } else if (normalized.contains("trang thai")) {
                    colStatus = c;
                }
            }
        }
        if (colRef == -1) colRef = 0;
        if (colProductCode == -1) colProductCode = 1;
        if (colQuantity == -1) colQuantity = 2;
        if (colUnit == -1) colUnit = 3;
        if (colSellingPrice == -1) colSellingPrice = 4;
        if (colCostPrice == -1) colCostPrice = 5;
        if (colPolicy == -1) colPolicy = 6;
        if (colStatus == -1) colStatus = 7;

        for (int r = headerRowIndex + 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String ref = getCellValueAsString(row.getCell(colRef)).trim();
            String productCode = getCellValueAsString(row.getCell(colProductCode)).trim();
            String quantityStr = getCellValueAsString(row.getCell(colQuantity)).trim();
            String unitName = getCellValueAsString(row.getCell(colUnit)).trim();
            String sellingPriceStr = getCellValueAsString(row.getCell(colSellingPrice)).trim();
            String costPriceStr = getCellValueAsString(row.getCell(colCostPrice)).trim();
            String policyStr = getCellValueAsString(row.getCell(colPolicy)).trim();
            String statusStr = getCellValueAsString(row.getCell(colStatus)).trim();

            if (ref.isBlank() && productCode.isBlank() && quantityStr.isBlank()) {
                continue;
            }
            if (ref.isBlank()) {
                ref = "__row_" + (r + 1);
            }
            if (payloadsByRef.containsKey(ref)) {
                continue;
            }

            Product product = productCode.isBlank() ? null : productsByCode.get(productCode.toUpperCase());
            Unit unit = resolveUnit(units, unitName);
            BigDecimal quantity = parseDecimal(quantityStr);
            BigDecimal sellingPrice = parseDecimal(sellingPriceStr);
            BigDecimal costPrice = parseDecimal(costPriceStr);
            InventoryPolicy policy = "Không theo dõi".equalsIgnoreCase(policyStr) || "DO_NOT_TRACK".equalsIgnoreCase(policyStr)
                    ? InventoryPolicy.DO_NOT_TRACK : InventoryPolicy.TRACK_STOCK;
            Status status = "Ngừng hoạt động".equalsIgnoreCase(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr)
                    ? Status.INACTIVE : Status.ACTIVE;

            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", product != null ? product.getId().toString() : null);
            payload.put("quantity", quantity);
            payload.put("sellingPrice", sellingPrice != null ? sellingPrice : BigDecimal.ZERO);
            payload.put("costPrice", costPrice != null ? costPrice : BigDecimal.ZERO);
            payload.put("unitId", unit != null ? unit.getId().toString() : null);
            payload.put("reorderPoint", null);
            payload.put("inventoryPolicy", policy.name());
            payload.put("status", status.name());
            payload.put("bomItems", new ArrayList<Map<String, Object>>());

            payloadsByRef.put(ref, payload);
            rowIndexByRef.put(ref, r + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseBomSheet(
            Sheet sheet,
            Map<String, Ingredient> ingredientsByCode,
            List<Unit> units,
            Map<String, Map<String, Object>> payloadsByRef) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        if (rowCount <= 1) return;

        int headerRowIndex = findHeaderRowIndex(sheet, "ma nguyen lieu", "ref");
        Row headerRow = sheet.getRow(headerRowIndex);
        int colVariantRef = -1, colIngredientCode = -1, colQuantity = -1, colUnit = -1, colWasteRate = -1;

        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                if (normalized.contains("ref")) {
                    colVariantRef = c;
                } else if (normalized.contains("ma nguyen lieu")) {
                    colIngredientCode = c;
                } else if (normalized.contains("dinh luong")) {
                    colQuantity = c;
                } else if (normalized.contains("don vi")) {
                    colUnit = c;
                } else if (normalized.contains("hao hut")) {
                    colWasteRate = c;
                }
            }
        }
        if (colVariantRef == -1) colVariantRef = 0;
        if (colIngredientCode == -1) colIngredientCode = 1;
        if (colQuantity == -1) colQuantity = 2;
        if (colUnit == -1) colUnit = 3;
        if (colWasteRate == -1) colWasteRate = 4;

        for (int r = headerRowIndex + 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String variantRef = getCellValueAsString(row.getCell(colVariantRef)).trim();
            String ingredientCode = getCellValueAsString(row.getCell(colIngredientCode)).trim();
            String quantityStr = getCellValueAsString(row.getCell(colQuantity)).trim();
            String unitName = getCellValueAsString(row.getCell(colUnit)).trim();
            String wasteRateStr = getCellValueAsString(row.getCell(colWasteRate)).trim();

            if (variantRef.isBlank() && ingredientCode.isBlank() && quantityStr.isBlank()) {
                continue;
            }
            Map<String, Object> variantPayload = payloadsByRef.get(variantRef);
            if (variantPayload == null) {
                continue; // Ref mặt hàng không khớp dòng nào ở sheet Mặt hàng — bỏ qua dòng BOM này.
            }

            Ingredient ingredient = ingredientCode.isBlank() ? null : ingredientsByCode.get(ingredientCode.toUpperCase());
            Unit unit = resolveUnit(units, unitName);
            BigDecimal quantity = parseDecimal(quantityStr);
            BigDecimal wasteRate = parseDecimal(wasteRateStr);

            Map<String, Object> bomLine = new HashMap<>();
            bomLine.put("ingredientId", ingredient != null ? ingredient.getId().toString() : null);
            bomLine.put("quantity", quantity);
            bomLine.put("unitId", unit != null ? unit.getId().toString() : null);
            bomLine.put("wasteRate", wasteRate != null ? wasteRate : BigDecimal.ZERO);

            ((List<Map<String, Object>>) variantPayload.get("bomItems")).add(bomLine);
        }
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        // 1 item lẻ (vd sau khi user sửa 1 dòng) — tra cứu tồn tại trực tiếp, không cần gộp id vì chỉ có 1 dòng.
        return validate(payload, null, null, null);
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        // Gộp toàn bộ id (product/unit/ingredient) của cả batch lại, chỉ tra cứu tồn tại bằng
        // 3 query findAllById() thay vì N*(1 product + 1 unit + 2*số dòng BOM) query existsById() riêng lẻ.
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> unitIds = new HashSet<>();
        Set<UUID> ingredientIds = new HashSet<>();
        for (Map<String, Object> payload : payloads) {
            addIfPresent(productIds, readUuid(payload.get("productId")));
            addIfPresent(unitIds, readUuid(payload.get("unitId")));
            for (Map<String, Object> bomLine : readBomItems(payload)) {
                addIfPresent(ingredientIds, readUuid(bomLine.get("ingredientId")));
                addIfPresent(unitIds, readUuid(bomLine.get("unitId")));
            }
        }

        Set<UUID> existingProductIds = findExistingIds(productRepository, productIds);
        Set<UUID> existingUnitIds = findExistingIds(unitRepository, unitIds);
        Set<UUID> existingIngredientIds = findExistingIds(ingredientRepository, ingredientIds);

        return payloads.stream()
                .map(payload -> validate(payload, existingProductIds, existingUnitIds, existingIngredientIds))
                .toList();
    }

    /**
     * @param existingProductIds/.../existingIngredientIds null nếu gọi từ {@link #validate(Map)} cho 1 item lẻ
     *        (sẽ tự tra cứu trực tiếp qua existsById); không null nếu gọi từ {@link #validateAll} (tra trong tập
     *        id đã gộp sẵn, không đụng DB nữa).
     */
    private List<ValidationErrorResponse> validate(
            Map<String, Object> payload,
            Set<UUID> existingProductIds,
            Set<UUID> existingUnitIds,
            Set<UUID> existingIngredientIds) {
        List<ValidationErrorResponse> errors = new ArrayList<>();

        UUID productId = readUuid(payload.get("productId"));
        if (productId == null) {
            errors.add(new ValidationErrorResponse("productId", "Mã sản phẩm không được để trống hoặc không tồn tại"));
        } else if (!existsProduct(existingProductIds, productId)) {
            errors.add(new ValidationErrorResponse("productId", "Sản phẩm không tồn tại"));
        }

        ProductVariantRequest request = toVariantRequest(payload);
        for (ConstraintViolation<ProductVariantRequest> violation : validator.validate(request)) {
            errors.add(new ValidationErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()));
        }
        if (request.unitId() != null && !existsUnit(existingUnitIds, request.unitId())) {
            errors.add(new ValidationErrorResponse("unitId", "Đơn vị không tồn tại"));
        }

        List<Map<String, Object>> bomItems = readBomItems(payload);
        Set<UUID> seenIngredientIds = new HashSet<>();
        for (int i = 0; i < bomItems.size(); i++) {
            String prefix = "bomItems[" + i + "].";
            BomItemRequest bomRequest = objectMapper.convertValue(bomItems.get(i), BomItemRequest.class);
            if (bomRequest.ingredientId() != null && !seenIngredientIds.add(bomRequest.ingredientId())) {
                errors.add(new ValidationErrorResponse(prefix + "ingredientId", "Nguyên liệu bị trùng trong định mức"));
            }
            for (ConstraintViolation<BomItemRequest> violation : validator.validate(bomRequest)) {
                errors.add(new ValidationErrorResponse(prefix + violation.getPropertyPath(), violation.getMessage()));
            }
            if (bomRequest.ingredientId() != null && !existsIngredient(existingIngredientIds, bomRequest.ingredientId())) {
                errors.add(new ValidationErrorResponse(prefix + "ingredientId", "Nguyên liệu không tồn tại"));
            }
            if (bomRequest.unitId() != null && !existsUnit(existingUnitIds, bomRequest.unitId())) {
                errors.add(new ValidationErrorResponse(prefix + "unitId", "Đơn vị không tồn tại"));
            }
        }

        return errors;
    }

    private void addIfPresent(Set<UUID> ids, UUID id) {
        if (id != null) ids.add(id);
    }

    private Set<UUID> findExistingIds(ProductRepository repository, Set<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return repository.findAllById(ids).stream().map(Product::getId).collect(Collectors.toSet());
    }

    // Đơn vị / nguyên liệu đã xoá coi như không tồn tại — BomItemService.replaceForVariant() từ chối chúng lúc duyệt
    private Set<UUID> findExistingIds(UnitRepository repository, Set<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return repository.findAllById(ids).stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .map(Unit::getId).collect(Collectors.toSet());
    }

    private Set<UUID> findExistingIds(IngredientRepository repository, Set<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return repository.findAllById(ids).stream()
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .map(Ingredient::getId).collect(Collectors.toSet());
    }

    private boolean existsProduct(Set<UUID> preloadedExistingIds, UUID id) {
        return preloadedExistingIds != null ? preloadedExistingIds.contains(id) : productRepository.existsById(id);
    }

    private boolean existsUnit(Set<UUID> preloadedExistingIds, UUID id) {
        return preloadedExistingIds != null
                ? preloadedExistingIds.contains(id)
                : unitRepository.findById(id).filter(u -> !Boolean.TRUE.equals(u.getIsDeleted())).isPresent();
    }

    private boolean existsIngredient(Set<UUID> preloadedExistingIds, UUID id) {
        return preloadedExistingIds != null
                ? preloadedExistingIds.contains(id)
                : ingredientRepository.findById(id).filter(i -> !Boolean.TRUE.equals(i.getIsDeleted())).isPresent();
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        UUID productId = readUuid(payload.get("productId"));
        ProductVariantRequest request = toVariantRequest(payload);
        ProductResponse.VariantResponse response = productVariantService.create(productId, request);

        List<Map<String, Object>> bomItemMaps = readBomItems(payload);
        if (!bomItemMaps.isEmpty()) {
            List<BomItemRequest> bomRequests = bomItemMaps.stream()
                    .map(m -> objectMapper.convertValue(m, BomItemRequest.class))
                    .toList();
            bomItemService.replaceForVariant(response.id(), bomRequests);
        }
        return response.id();
    }

    private ProductVariantRequest toVariantRequest(Map<String, Object> payload) {
        Map<String, Object> variantFields = new HashMap<>();
        variantFields.put("quantity", payload.get("quantity"));
        variantFields.put("sellingPrice", payload.get("sellingPrice"));
        variantFields.put("costPrice", payload.get("costPrice"));
        variantFields.put("unitId", payload.get("unitId"));
        variantFields.put("reorderPoint", payload.get("reorderPoint"));
        variantFields.put("inventoryPolicy", payload.get("inventoryPolicy"));
        variantFields.put("status", payload.get("status"));
        return objectMapper.convertValue(variantFields, ProductVariantRequest.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readBomItems(Map<String, Object> payload) {
        Object raw = payload.get("bomItems");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return List.of();
    }

    private UUID readUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Unit resolveUnit(List<Unit> units, String unitName) {
        if (unitName == null || unitName.isBlank()) return null;
        String clean = unitName.trim();
        return units.stream()
                .filter(u -> (u.getName() != null && u.getName().equalsIgnoreCase(clean))
                        || (u.getSymbol() != null && u.getSymbol().equalsIgnoreCase(clean)))
                .findFirst()
                .orElse(null);
    }

    private Sheet findSheetByName(Workbook workbook, String normalizedNameContains) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (normalize(workbook.getSheetName(i)).contains(normalizedNameContains)) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    private int findHeaderRowIndex(Sheet sheet, String... mustContainAny) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        for (int r = 0; r < Math.min(10, rowCount); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(row.getCell(c)));
                for (String needle : mustContainAny) {
                    if (normalized.contains(needle)) {
                        return r;
                    }
                }
            }
        }
        return 0;
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
