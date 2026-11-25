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
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.BomItemService;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.commonservice.common_importing.enums.ImportMode;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.common_importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.common_importing.handler.ParsedImportRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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

/**
 * Import Mặt hàng kèm BOM: sheet "Mặt hàng" (1 dòng = 1 mặt hàng, có cột Ref tự đặt để liên kết)
 * và sheet "BOM" (nhiều dòng nguyên liệu, mỗi dòng có cột "Ref mặt hàng" trỏ về Ref ở sheet Mặt hàng).
 * Mỗi ImportBatchItem tương ứng 1 mặt hàng, payload gồm field của ProductVariantRequest + productId
 * (resolve sẵn từ Mã sản phẩm) + mảng bomItems (resolve sẵn từ sheet BOM).
 */
@Component
public class ProductVariantImportHandler implements ImportTypeHandler {

    private static final String CUSTOM_BUNDLE_SKU = "CUSTOM-BUNDLE";

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UnitRepository unitRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductVariantService productVariantService;
    private final BomItemService bomItemService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public ProductVariantImportHandler(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            UnitRepository unitRepository,
            IngredientRepository ingredientRepository,
            ProductVariantService productVariantService,
            BomItemService bomItemService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
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
        return parseExcel(file, ImportMode.CREATE);
    }

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file, ImportMode mode) {
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
                parseVariantSheet(variantSheet, mode, productsByCode, units, payloadsByRef, rowIndexByRef);
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
            ImportMode mode,
            Map<String, Product> productsByCode,
            List<Unit> units,
            Map<String, Map<String, Object>> payloadsByRef,
            Map<String, Integer> rowIndexByRef) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        if (rowCount <= 1) return;

        int headerRowIndex = findHeaderRowIndex(sheet, "ma san pham", "dinh luong");
        Row headerRow = sheet.getRow(headerRowIndex);
        int colRef = -1, colProductCode = -1, colQuantity = -1, colUnit = -1,
                colSellingPrice = -1, colCostPrice = -1, colPolicy = -1, colStatus = -1, colSku = -1, colBarcode = -1;

        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                if (normalized.startsWith("sku")) {
                    colSku = c;
                } else if (normalized.startsWith("barcode") || normalized.contains("ma vach")) {
                    colBarcode = c;
                } else if (normalized.equals("ref") || normalized.contains("ref")) {
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

        List<RawVariantRow> rawRows = new ArrayList<>();
        Set<String> seenRefs = new HashSet<>();
        for (int r = headerRowIndex + 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String ref = getCellValueAsString(row.getCell(colRef)).trim();
            String productCode = getCellValueAsString(row.getCell(colProductCode)).trim();
            String quantityStr = getCellValueAsString(row.getCell(colQuantity)).trim();
            String sku = colSku == -1 ? "" : getCellValueAsString(row.getCell(colSku)).trim();
            String barcode = colBarcode == -1 ? "" : getCellValueAsString(row.getCell(colBarcode)).trim();

            if (ref.isBlank() && productCode.isBlank() && quantityStr.isBlank() && sku.isBlank() && barcode.isBlank()) {
                continue;
            }
            if (ref.isBlank()) {
                ref = "__row_" + (r + 1);
            }
            if (!seenRefs.add(ref)) {
                continue;
            }

            rawRows.add(new RawVariantRow(
                    ref,
                    r + 1,
                    productCode,
                    quantityStr,
                    getCellValueAsString(row.getCell(colUnit)).trim(),
                    getCellValueAsString(row.getCell(colSellingPrice)).trim(),
                    getCellValueAsString(row.getCell(colCostPrice)).trim(),
                    getCellValueAsString(row.getCell(colPolicy)).trim(),
                    getCellValueAsString(row.getCell(colStatus)).trim(),
                    sku,
                    barcode));
        }

        Map<String, ProductVariant> variantsBySku = mode == ImportMode.UPDATE ? loadVariantsBySku(rawRows) : Map.of();

        for (RawVariantRow raw : rawRows) {
            ProductVariant existing = raw.sku().isBlank() ? null : variantsBySku.get(raw.sku().toUpperCase());

            Product product = raw.productCode().isBlank() ? null : productsByCode.get(raw.productCode().toUpperCase());
            Unit unit = resolveUnit(units, raw.unitName());
            BigDecimal quantity = parseDecimal(raw.quantity());
            BigDecimal sellingPrice = parseDecimal(raw.sellingPrice());
            BigDecimal costPrice = parseDecimal(raw.costPrice());
            InventoryPolicy policy = "Không theo dõi".equalsIgnoreCase(raw.policy()) || "DO_NOT_TRACK".equalsIgnoreCase(raw.policy())
                    ? InventoryPolicy.DO_NOT_TRACK : InventoryPolicy.TRACK_STOCK;
            Status status = "Ngừng hoạt động".equalsIgnoreCase(raw.status()) || "INACTIVE".equalsIgnoreCase(raw.status())
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
            payload.put("importMode", mode.name());

            if (!raw.sku().isBlank()) {
                payload.put("sku", raw.sku());
            }
            payload.put("barcode", raw.barcode().isBlank() ? null : raw.barcode());
            if (mode == ImportMode.UPDATE) {
                payload.put("variantId", existing != null ? existing.getId().toString() : null);
                if (existing != null) {
                    fillBlankFromExisting(payload, raw, existing);
                }
            }

            payloadsByRef.put(raw.ref(), payload);
            rowIndexByRef.put(raw.ref(), raw.rowIndex());
        }
    }

    private Map<String, ProductVariant> loadVariantsBySku(List<RawVariantRow> rawRows) {
        Set<String> skus = rawRows.stream()
                .map(RawVariantRow::sku)
                .filter(sku -> !sku.isBlank())
                .collect(Collectors.toSet());
        if (skus.isEmpty()) return Map.of();
        return productVariantRepository.findAllBySkuInWithDetails(skus).stream()
                .collect(Collectors.toMap(v -> v.getSku().toUpperCase(), v -> v, (a, b) -> a));
    }

    private void fillBlankFromExisting(Map<String, Object> payload, RawVariantRow raw, ProductVariant existing) {
        payload.put("compositionRevision", existing.getCompositionRevision());
        if (raw.productCode().isBlank()) {
            payload.put("productId", existing.getProduct().getId().toString());
        }
        if (raw.quantity().isBlank()) {
            payload.put("quantity", existing.getQuantity());
        }
        if (raw.unitName().isBlank() && existing.getUnit() != null) {
            payload.put("unitId", existing.getUnit().getId().toString());
        }
        if (raw.sellingPrice().isBlank()) {
            payload.put("sellingPrice", existing.getSellingPrice());
        }
        if (raw.costPrice().isBlank()) {
            payload.put("costPrice", existing.getCostPrice());
        }
        if (raw.policy().isBlank() && existing.getInventoryPolicy() != null) {
            payload.put("inventoryPolicy", existing.getInventoryPolicy().name());
        }
        if (raw.status().isBlank() && existing.getStatus() != null) {
            payload.put("status", existing.getStatus().name());
        }
        if (raw.barcode().isBlank()) {
            payload.put("barcode", existing.getBarcode());
        }
    }

    private record RawVariantRow(
            String ref,
            int rowIndex,
            String productCode,
            String quantity,
            String unitName,
            String sellingPrice,
            String costPrice,
            String policy,
            String status,
            String sku,
            String barcode) {
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
        List<ValidationErrorResponse> errors = validate(payload, null, null, null);
        validateUpdateTarget(payload, null, null, errors);
        validateBarcode(payload, null, errors);
        return errors;
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        // Gộp toàn bộ id (product/unit/ingredient) của cả batch lại, chỉ tra cứu tồn tại bằng
        // 3 query findAllById() thay vì N*(1 product + 1 unit + 2*số dòng BOM) query existsById() riêng lẻ.
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> unitIds = new HashSet<>();
        Set<UUID> ingredientIds = new HashSet<>();
        Set<UUID> variantIds = new HashSet<>();
        Map<String, Integer> skuCounts = new HashMap<>();
        for (Map<String, Object> payload : payloads) {
            addIfPresent(variantIds, readUuid(payload.get("variantId")));
            String sku = readSku(payload);
            if (sku != null) {
                skuCounts.merge(sku.toUpperCase(), 1, Integer::sum);
            }
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
        Map<UUID, ProductVariant> existingVariants = variantIds.isEmpty() ? Map.of()
                : productVariantRepository.findAllById(variantIds).stream()
                        .filter(v -> !Boolean.TRUE.equals(v.getIsDeleted()))
                        .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<String> createSkus = payloads.stream()
                .filter(payload -> readMode(payload) == ImportMode.CREATE)
                .map(this::readSku)
                .filter(sku -> sku != null)
                .collect(Collectors.toSet());
        Set<String> takenSkus = createSkus.isEmpty() ? Set.of()
                : productVariantRepository.findSkusIn(createSkus).stream()
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());
        Map<String, Integer> barcodeCounts = new HashMap<>();
        for (Map<String, Object> payload : payloads) {
            String barcode = readBarcode(payload);
            if (barcode != null) {
                barcodeCounts.merge(barcode.toUpperCase(), 1, Integer::sum);
            }
        }
        Map<String, UUID> barcodeOwners = loadBarcodeOwners(barcodeCounts.keySet());

        return payloads.stream()
                .map(payload -> {
                    List<ValidationErrorResponse> errors =
                            validate(payload, existingProductIds, existingUnitIds, existingIngredientIds);
                    validateUpdateTarget(payload, existingVariants, takenSkus, errors);
                    validateBarcode(payload, barcodeOwners, errors);
                    String sku = readSku(payload);
                    if (sku != null && skuCounts.getOrDefault(sku.toUpperCase(), 0) > 1) {
                        errors.add(new ValidationErrorResponse("sku", "SKU " + sku + " bị lặp trong file import"));
                    }
                    String barcode = readBarcode(payload);
                    if (barcode != null && barcodeCounts.getOrDefault(barcode.toUpperCase(), 0) > 1) {
                        errors.add(new ValidationErrorResponse("barcode", "Barcode " + barcode + " bị lặp trong file import"));
                    }
                    return errors;
                })
                .toList();
    }

    private Map<String, UUID> loadBarcodeOwners(Collection<String> barcodes) {
        if (barcodes.isEmpty()) return Map.of();
        return productVariantRepository.findAllByBarcodeIn(barcodes).stream()
                .filter(v -> v.getBarcode() != null)
                .collect(Collectors.toMap(v -> v.getBarcode().toUpperCase(), ProductVariant::getId, (a, b) -> a));
    }

    private void validateBarcode(
            Map<String, Object> payload,
            Map<String, UUID> preloadedOwners,
            List<ValidationErrorResponse> errors) {
        String barcode = readBarcode(payload);
        if (barcode == null) return;
        if (barcode.length() > 100) {
            errors.add(new ValidationErrorResponse("barcode", "Barcode không được dài quá 100 ký tự"));
            return;
        }
        Map<String, UUID> owners = preloadedOwners != null ? preloadedOwners : loadBarcodeOwners(List.of(barcode));
        UUID ownerId = owners.get(barcode.toUpperCase());
        if (ownerId == null) return;
        boolean sameVariant = readMode(payload) == ImportMode.UPDATE
                && ownerId.equals(readUuid(payload.get("variantId")));
        if (!sameVariant) {
            errors.add(new ValidationErrorResponse("barcode", "Barcode " + barcode + " đã được dùng cho mặt hàng khác"));
        }
    }

    private void validateUpdateTarget(
            Map<String, Object> payload,
            Map<UUID, ProductVariant> preloadedVariants,
            Set<String> preloadedTakenSkus,
            List<ValidationErrorResponse> errors) {
        String sku = readSku(payload);
        if (readMode(payload) == ImportMode.CREATE) {
            if (sku == null) return;
            if (sku.length() > 100) {
                errors.add(new ValidationErrorResponse("sku", "SKU không được dài quá 100 ký tự"));
            }
            boolean taken = preloadedTakenSkus != null
                    ? preloadedTakenSkus.contains(sku.toUpperCase())
                    : productVariantRepository.existsBySku(sku);
            if (taken || CUSTOM_BUNDLE_SKU.equalsIgnoreCase(sku)) {
                errors.add(new ValidationErrorResponse("sku",
                        "SKU " + sku + " đã tồn tại — để trống để hệ thống tự sinh, hoặc chọn kiểu Cập nhật để sửa mặt hàng đó"));
            }
            return;
        }
        if (sku == null) {
            errors.add(new ValidationErrorResponse("sku", "Kiểu import Cập nhật bắt buộc nhập SKU"));
            return;
        }

        UUID variantId = readUuid(payload.get("variantId"));
        ProductVariant variant = null;
        if (variantId != null) {
            variant = preloadedVariants != null
                    ? preloadedVariants.get(variantId)
                    : productVariantRepository.findById(variantId)
                            .filter(v -> !Boolean.TRUE.equals(v.getIsDeleted()))
                            .orElse(null);
        }
        if (variant == null || !variant.getSku().equalsIgnoreCase(sku)) {
            errors.add(new ValidationErrorResponse("sku", "SKU " + sku + " không tồn tại hoặc đã bị xoá"));
            return;
        }
        if (variant.isCustomBundle()) {
            errors.add(new ValidationErrorResponse("sku", "Không thể cập nhật mặt hàng combo tự chọn qua import"));
        }
        UUID productId = readUuid(payload.get("productId"));
        if (productId != null && !productId.equals(variant.getProduct().getId())) {
            errors.add(new ValidationErrorResponse("productId", "Mã sản phẩm khác với sản phẩm hiện tại của SKU " + sku));
        }
        Long expectedRevision = readLong(payload.get("compositionRevision"));
        if (expectedRevision != null && expectedRevision != variant.getCompositionRevision()) {
            errors.add(new ValidationErrorResponse("sku",
                    "Định lượng/BOM của SKU " + sku + " đã thay đổi sau khi import, vui lòng import lại"));
        }
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
        ProductResponse.VariantResponse response;
        if (readMode(payload) == ImportMode.UPDATE) {
            UUID variantId = readUuid(payload.get("variantId"));
            if (variantId == null) {
                throw new BadRequestException("SKU " + readSku(payload) + " không tồn tại hoặc đã bị xoá");
            }
            response = productVariantService.update(variantId, request);
        } else {
            response = productVariantService.create(productId, request, readSku(payload));
        }

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
        variantFields.put("barcode", readBarcode(payload));
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

    private ImportMode readMode(Map<String, Object> payload) {
        return ImportMode.UPDATE.name().equals(payload.get("importMode")) ? ImportMode.UPDATE : ImportMode.CREATE;
    }

    private String readBarcode(Map<String, Object> payload) {
        Object value = payload.get("barcode");
        if (value == null) return null;
        String barcode = String.valueOf(value).trim();
        return barcode.isEmpty() ? null : barcode;
    }

    private String readSku(Map<String, Object> payload) {
        Object value = payload.get("sku");
        if (value == null) return null;
        String sku = String.valueOf(value).trim();
        return sku.isEmpty() ? null : sku;
    }

    private Long readLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
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
