package org.hvtra.iventoryservice.importing.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.iventoryservice.document.dto.request.CreatePurchaseReceiptRequest;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.document.service.InventoryDocumentService;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.shared.async.RequestContextAsync;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.common_importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.common_importing.handler.ParsedImportRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Import Phiếu nhập kho (GoodsReceipt): file gồm 5 sheet — "Hướng dẫn", "Phiếu nhập" (1 dòng = 1 phiếu,
 * có cột Ref tự đặt để liên kết), "Nguyên liệu" (nhiều dòng, có cột "Ref Phiếu nhập" trỏ về Ref ở
 * sheet "Phiếu nhập"), "Nguyên liệu mới" (khai báo nguyên liệu chưa có trong hệ thống) và "Tham chiếu"
 * (tra cứu mã kho/nguyên liệu/nhà cung cấp/đơn vị).
 * Payload khớp 1:1 field của {@link CreatePurchaseReceiptRequest} để commit() có thể convertValue thẳng,
 * không cần lớp remap trung gian — trừ các field tạm {@code newIngredient*} được resolve/xoá đi ngay
 * trong commit() trước khi convertValue.
 */
@Component
public class GoodsReceiptImportHandler implements ImportTypeHandler {

    private static final byte[] BRAND_GREEN_RGB = new byte[]{(byte) 7, (byte) 136, (byte) 58}; // #07883A

    private final CatalogClient catalogClient;
    private final InventoryLocationRepository locationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryDocumentService inventoryDocumentService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public GoodsReceiptImportHandler(
            CatalogClient catalogClient,
            InventoryLocationRepository locationRepository,
            InventoryItemRepository inventoryItemRepository,
            InventoryDocumentService inventoryDocumentService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.catalogClient = catalogClient;
        this.locationRepository = locationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryDocumentService = inventoryDocumentService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportType type() {
        return ImportType.GOODS_RECEIPT;
    }

    // ---------------------------------------------------------------------
    // Nhỏ gọn để mang theo thông tin 1 nguyên liệu MỚI (khai báo ở sheet riêng) theo mã nguyên liệu.
    // ---------------------------------------------------------------------
    private record NewIngredientInfo(String code, String name, UUID unitId) {}

    // Key tạm trong payload lúc parse, bị xoá trong resolveCodes()
    private static final String SUPPLIER_CODE_KEY = "__supplierCode";
    private static final String LOCATION_CODE_KEY = "__locationCode";
    private static final String ITEM_CODE_KEY = "__itemCode";

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet receiptSheet = findSheetByName(workbook, "phieu nhap");
            if (receiptSheet == null) {
                receiptSheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            // "Nguyên liệu" phải khớp CHÍNH XÁC — nếu dùng "contains" sẽ vô tình khớp luôn sheet
            // "Nguyên liệu mới" (tên cũng chứa "nguyen lieu"), tuỳ thứ tự sheet trong file.
            Sheet lineSheet = findSheetByExactName(workbook, "nguyen lieu");
            Sheet newIngredientSheet = findSheetByName(workbook, "nguyen lieu moi");

            // Nhà cung cấp lấy song song trong lúc đọc file; đơn vị chỉ cần khi có sheet "Nguyên liệu mới"
            CompletableFuture<List<CatalogClient.SupplierLookupResponse>> suppliersFuture =
                    RequestContextAsync.supply(catalogClient::getAllSuppliers);
            boolean hasNewIngredientRows = newIngredientSheet != null && newIngredientSheet.getPhysicalNumberOfRows() > 1;
            Map<String, NewIngredientInfo> newIngredientsByCode = hasNewIngredientRows
                    ? parseNewIngredientSheet(newIngredientSheet, catalogClient.getAllUnits()) : Map.of();

            LinkedHashMap<String, Map<String, Object>> payloadsByRef = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> rowIndexByRef = new LinkedHashMap<>();

            if (receiptSheet != null) {
                parseReceiptSheet(receiptSheet, payloadsByRef, rowIndexByRef);
            }
            if (lineSheet != null) {
                parseLineSheet(lineSheet, newIngredientsByCode, payloadsByRef);
            }
            resolveCodes(payloadsByRef.values(), suppliersFuture.join());

            List<ParsedImportRow> rows = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : payloadsByRef.entrySet()) {
                rows.add(new ParsedImportRow(rowIndexByRef.get(entry.getKey()), entry.getValue()));
            }
            return rows;
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc file Excel phiếu nhập kho: " + e.getMessage());
        }
    }

    private Map<String, NewIngredientInfo> parseNewIngredientSheet(Sheet sheet, List<CatalogClient.UnitResponse> units) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        if (rowCount <= 1) return Map.of();

        int headerRowIndex = findHeaderRowIndex(sheet, "ten nguyen lieu", "ma nguyen lieu");
        Row headerRow = sheet.getRow(headerRowIndex);
        int colCode = -1, colName = -1, colUnit = -1;

        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                if (normalized.contains("ma nguyen lieu")) {
                    colCode = c;
                } else if (normalized.contains("ten nguyen lieu")) {
                    colName = c;
                } else if (normalized.contains("don vi")) {
                    colUnit = c;
                }
            }
        }
        if (colCode == -1) colCode = 0;
        if (colName == -1) colName = 1;
        if (colUnit == -1) colUnit = 2;

        Map<String, NewIngredientInfo> result = new HashMap<>();
        for (int r = headerRowIndex + 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String code = colCode != -1 ? getCellValueAsString(row.getCell(colCode)).trim() : "";
            String name = colName != -1 ? getCellValueAsString(row.getCell(colName)).trim() : "";
            if (code.isBlank() && name.isBlank()) continue;

            String unitName = colUnit != -1 ? getCellValueAsString(row.getCell(colUnit)).trim() : "";
            CatalogClient.UnitResponse unit = resolveUnit(units, unitName);

            NewIngredientInfo info = new NewIngredientInfo(
                    code.isBlank() ? null : code,
                    name,
                    unit != null ? unit.id() : null);

            if (!code.isBlank()) {
                result.put(code.toUpperCase(), info);
            }
        }
        return result;
    }

    private void parseReceiptSheet(
            Sheet sheet,
            Map<String, Map<String, Object>> payloadsByRef,
            Map<String, Integer> rowIndexByRef) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        if (rowCount <= 1) return;

        int headerRowIndex = findHeaderRowIndex(sheet, "ma nha cung cap", "ma kho");
        Row headerRow = sheet.getRow(headerRowIndex);
        int colRef = -1, colSupplier = -1, colLocation = -1, colExpectedDate = -1, colNote = -1;

        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                if (normalized.equals("ref") || (normalized.contains("ref") && !normalized.contains("ma") && !normalized.contains("nha cung cap"))) {
                    colRef = c;
                } else if (normalized.contains("ma nha cung cap") || normalized.contains("nha cung cap")) {
                    colSupplier = c;
                } else if (normalized.contains("ma kho") || normalized.contains("kho")) {
                    colLocation = c;
                } else if (normalized.contains("ngay du kien") || normalized.contains("ngay")) {
                    colExpectedDate = c;
                } else if (normalized.contains("ghi chu")) {
                    colNote = c;
                }
            }
        }
        if (colRef == -1) colRef = 0;
        if (colSupplier == -1) colSupplier = 1;
        if (colLocation == -1) colLocation = 2;
        if (colExpectedDate == -1) colExpectedDate = 3;
        if (colNote == -1) colNote = 4;

        for (int r = headerRowIndex + 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String ref = getCellValueAsString(row.getCell(colRef)).trim();
            String supplierCode = colSupplier != -1 ? getCellValueAsString(row.getCell(colSupplier)).trim() : "";
            String locationCode = colLocation != -1 ? getCellValueAsString(row.getCell(colLocation)).trim() : "";
            Instant expectedDate = parseExpectedDate(row.getCell(colExpectedDate));
            String note = colNote != -1 ? getCellValueAsString(row.getCell(colNote)).trim() : "";

            if (ref.isBlank() && supplierCode.isBlank() && locationCode.isBlank()) {
                continue;
            }
            if (ref.isBlank()) {
                ref = "__row_" + (r + 1);
            }
            if (payloadsByRef.containsKey(ref)) {
                continue;
            }

            Map<String, Object> payload = new HashMap<>();
            // Mã thô — resolveCodes() đổi sang id sau khi đã gom đủ mã của cả file
            payload.put(SUPPLIER_CODE_KEY, supplierCode);
            payload.put(LOCATION_CODE_KEY, locationCode);
            payload.put("branchId", null);
            payload.put("expectedDate", expectedDate != null ? expectedDate.toString() : null);
            payload.put("referenceType", null);
            payload.put("referenceId", null);
            payload.put("note", note.isBlank() ? null : note);
            payload.put("items", new ArrayList<Map<String, Object>>());

            payloadsByRef.put(ref, payload);
            rowIndexByRef.put(ref, r + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseLineSheet(
            Sheet sheet,
            Map<String, NewIngredientInfo> newIngredientsByCode,
            Map<String, Map<String, Object>> payloadsByRef) {
        int rowCount = sheet.getPhysicalNumberOfRows();
        if (rowCount <= 1) return;

        int headerRowIndex = findHeaderRowIndex(sheet, "ma nguyen lieu", "ref");
        Row headerRow = sheet.getRow(headerRowIndex);
        int colReceiptRef = -1, colItemCode = -1, colIsNewIngredient = -1,
                colQuantity = -1, colNote = -1;

        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                if (normalized.equals("ref") || (normalized.contains("ref") && !normalized.contains("nguyen lieu"))) {
                    colReceiptRef = c;
                } else if (normalized.contains("nguyen lieu moi")) {
                    colIsNewIngredient = c;
                } else if (normalized.contains("ma nguyen lieu")) {
                    colItemCode = c;
                } else if (normalized.contains("so luong")) {
                    colQuantity = c;
                } else if (normalized.contains("ghi chu")) {
                    colNote = c;
                }
            }
        }
        if (colReceiptRef == -1) colReceiptRef = 0;
        if (colItemCode == -1) colItemCode = 1;
        if (colIsNewIngredient == -1) colIsNewIngredient = 2;
        if (colQuantity == -1) colQuantity = 3;
        if (colNote == -1) colNote = 4;

        for (int r = headerRowIndex + 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String receiptRef = getCellValueAsString(row.getCell(colReceiptRef)).trim();
            String itemCode = getCellValueAsString(row.getCell(colItemCode)).trim();
            boolean isNewIngredient = isTicked(row.getCell(colIsNewIngredient));
            String quantityStr = getCellValueAsString(row.getCell(colQuantity)).trim();
            String note = getCellValueAsString(row.getCell(colNote)).trim();

            if (receiptRef.isBlank() && itemCode.isBlank() && quantityStr.isBlank()) {
                continue;
            }
            Map<String, Object> receiptPayload = payloadsByRef.get(receiptRef);
            if (receiptPayload == null) {
                continue; // Ref Phiếu nhập không khớp dòng nào ở sheet "Phiếu nhập" — bỏ qua dòng này.
            }

            BigDecimal quantity = parseDecimal(quantityStr);
            Map<String, Object> line = new HashMap<>();

            NewIngredientInfo newIngredientInfo = null;
            if (isNewIngredient) {
                if (!itemCode.isBlank()) {
                    newIngredientInfo = newIngredientsByCode.get(itemCode.toUpperCase());
                }
            } else if (!itemCode.isBlank() && newIngredientsByCode.containsKey(itemCode.toUpperCase())) {
                newIngredientInfo = newIngredientsByCode.get(itemCode.toUpperCase());
            }

            if (newIngredientInfo != null) {
                line.put("inventoryItemId", null);
                line.put("unitId", null);
                line.put("newIngredientCode", newIngredientInfo.code() != null ? newIngredientInfo.code() : (itemCode.isBlank() ? null : itemCode));
                line.put("newIngredientName", newIngredientInfo.name());
                line.put("newIngredientUnitId", newIngredientInfo.unitId() != null ? newIngredientInfo.unitId().toString() : null);
            } else {
                line.put(ITEM_CODE_KEY, itemCode);
            }
            line.put("orderedQuantity", quantity);
            line.put("note", note.isBlank() ? null : note);

            ((List<Map<String, Object>>) receiptPayload.get("items")).add(line);
        }
    }

    /**
     * Đổi mã nhà cung cấp / kho / nguyên liệu trong payload sang id. Location và item chỉ query theo
     * đúng các mã xuất hiện trong file (không load cả bảng); so khớp mã không phân biệt hoa thường.
     */
    @SuppressWarnings("unchecked")
    private void resolveCodes(
            Collection<Map<String, Object>> payloads,
            List<CatalogClient.SupplierLookupResponse> suppliers) {
        Set<String> locationCodes = new HashSet<>();
        Set<String> itemCodes = new HashSet<>();
        for (Map<String, Object> payload : payloads) {
            addIfNotBlank(locationCodes, (String) payload.get(LOCATION_CODE_KEY));
            for (Map<String, Object> line : (List<Map<String, Object>>) payload.get("items")) {
                addIfNotBlank(itemCodes, (String) line.get(ITEM_CODE_KEY));
            }
        }

        Map<String, CatalogClient.SupplierLookupResponse> suppliersByCode = suppliers.stream()
                .filter(s -> s.code() != null)
                .collect(Collectors.toMap(s -> s.code().toUpperCase().trim(), s -> s, (a, b) -> a));
        Map<String, InventoryLocation> locationsByCode = locationCodes.isEmpty() ? Map.of()
                : locationRepository.findByCodeIn(locationCodes).stream()
                        .filter(l -> l.getCode() != null && !Boolean.TRUE.equals(l.getIsDeleted()))
                        .collect(Collectors.toMap(l -> l.getCode().toUpperCase().trim(), l -> l, (a, b) -> a));
        Map<String, InventoryItem> itemsByCode = itemCodes.isEmpty() ? Map.of()
                : inventoryItemRepository.findByItemTypeAndReferenceCodeIn(InventoryItemType.INGREDIENT, itemCodes).stream()
                        .filter(i -> i.getReferenceCode() != null && !Boolean.TRUE.equals(i.getIsDeleted()))
                        .collect(Collectors.toMap(i -> i.getReferenceCode().toUpperCase().trim(), i -> i, (a, b) -> a));

        for (Map<String, Object> payload : payloads) {
            String supplierCode = (String) payload.remove(SUPPLIER_CODE_KEY);
            String locationCode = (String) payload.remove(LOCATION_CODE_KEY);
            CatalogClient.SupplierLookupResponse supplier = isBlank(supplierCode) ? null : suppliersByCode.get(supplierCode.toUpperCase());
            InventoryLocation location = isBlank(locationCode) ? null : locationsByCode.get(locationCode.toUpperCase());
            payload.put("supplierId", supplier != null ? supplier.id().toString() : null);
            payload.put("locationId", location != null ? location.getId().toString() : null);

            for (Map<String, Object> line : (List<Map<String, Object>>) payload.get("items")) {
                if (!line.containsKey(ITEM_CODE_KEY)) continue; // dòng nguyên liệu mới
                String itemCode = (String) line.remove(ITEM_CODE_KEY);
                InventoryItem item = isBlank(itemCode) ? null : itemsByCode.get(itemCode.toUpperCase());
                line.put("inventoryItemId", item != null ? item.getId().toString() : null);
                line.put("unitId", item != null && item.getUnitId() != null ? item.getUnitId().toString() : null);
            }
        }
    }

    private static void addIfNotBlank(Set<String> values, String value) {
        if (!isBlank(value)) values.add(value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isTicked(Cell cell) {
        String v = getCellValueAsString(cell).trim().toLowerCase();
        if (v.isBlank()) return false;
        return !(v.equals("0") || v.equals("false") || v.equals("no") || v.equals("khong")
                || v.equals("không") || v.equals("k"));
    }

    private CatalogClient.UnitResponse resolveUnit(List<CatalogClient.UnitResponse> units, String unitName) {
        if (unitName == null || unitName.isBlank()) return null;
        String clean = unitName.trim();
        return units.stream()
                .filter(u -> (u.name() != null && u.name().equalsIgnoreCase(clean))
                        || (u.symbol() != null && u.symbol().equalsIgnoreCase(clean)))
                .findFirst()
                .orElse(null);
    }

    private Instant parseExpectedDate(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant();
        }
        String text = getCellValueAsString(cell).trim();
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        return validate(payload, null, null, null, null, null, Set.of());
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        Set<UUID> supplierIds = new HashSet<>();
        Set<UUID> locationIds = new HashSet<>();
        Set<UUID> inventoryItemIds = new HashSet<>();
        Set<UUID> newIngredientUnitIds = new HashSet<>();
        Map<String, Integer> newIngredientCodeCounts = new HashMap<>();
        for (Map<String, Object> payload : payloads) {
            addIfPresent(supplierIds, readUuid(payload.get("supplierId")));
            addIfPresent(locationIds, readUuid(payload.get("locationId")));
            for (Map<String, Object> line : readItems(payload)) {
                addIfPresent(inventoryItemIds, readUuid(line.get("inventoryItemId")));
                addIfPresent(newIngredientUnitIds, readUuid(line.get("newIngredientUnitId")));
                String newCode = newIngredientCodeOf(line);
                if (newCode != null) newIngredientCodeCounts.merge(newCode.toUpperCase(), 1, Integer::sum);
            }
        }
        // Mã nguyên liệu mới: đã có trong hệ thống (tra hàng tồn kho INGREDIENT — mỗi nguyên liệu Catalog có 1
        // inventory item với referenceCode = mã nguyên liệu) hoặc bị trùng giữa các dòng trong file.
        Set<String> duplicatedNewCodes = newIngredientCodeCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> existingNewCodes = newIngredientCodeCounts.isEmpty() ? Set.of()
                : findExistingIngredientCodes(newIngredientCodeCounts.keySet());

        // 2 lời gọi Catalog chạy song song
        CompletableFuture<Set<UUID>> existingSupplierIdsFuture = supplierIds.isEmpty()
                ? CompletableFuture.completedFuture(Set.of())
                : RequestContextAsync.supply(() -> catalogClient.getSuppliersBatch(List.copyOf(supplierIds))
                        .stream().map(CatalogClient.SupplierResponse::id).collect(Collectors.toSet()));
        Set<UUID> existingLocationIds = locationIds.isEmpty() ? Set.of() : locationRepository.findAllById(locationIds)
                .stream().map(InventoryLocation::getId).collect(Collectors.toSet());
        Set<UUID> existingInventoryItemIds = inventoryItemIds.isEmpty() ? Set.of() : inventoryItemRepository.findAllById(inventoryItemIds)
                .stream()
                .filter(i -> i.getItemType() == InventoryItemType.INGREDIENT && !Boolean.TRUE.equals(i.getIsDeleted()))
                .map(InventoryItem::getId).collect(Collectors.toSet());
        Set<UUID> existingUnitIds = newIngredientUnitIds.isEmpty() ? Set.of() : catalogClient.getUnitsBatch(List.copyOf(newIngredientUnitIds))
                .stream().map(CatalogClient.UnitResponse::id).collect(Collectors.toSet());
        Set<UUID> existingSupplierIds = existingSupplierIdsFuture.join();

        return payloads.stream()
                .map(payload -> validate(payload, existingSupplierIds, existingLocationIds, existingInventoryItemIds,
                        existingUnitIds, existingNewCodes, duplicatedNewCodes))
                .toList();
    }

    /** Mã nguyên liệu mới của 1 dòng (chỉ dòng tạo nguyên liệu mới và có nhập mã), null nếu không có. */
    private String newIngredientCodeOf(Map<String, Object> line) {
        if (line.get("inventoryItemId") != null || readString(line.get("newIngredientName")).isBlank()) return null;
        String code = readString(line.get("newIngredientCode")).trim();
        return code.isEmpty() ? null : code;
    }

    private Set<String> findExistingIngredientCodes(Collection<String> codes) {
        return inventoryItemRepository.findByItemTypeAndReferenceCodeIn(InventoryItemType.INGREDIENT, codes).stream()
                .map(InventoryItem::getReferenceCode)
                .filter(code -> code != null)
                .map(code -> code.trim().toUpperCase())
                .collect(Collectors.toSet());
    }

    private List<ValidationErrorResponse> validate(
            Map<String, Object> payload,
            Set<UUID> existingSupplierIds,
            Set<UUID> existingLocationIds,
            Set<UUID> existingInventoryItemIds,
            Set<UUID> existingUnitIds,
            Set<String> existingNewIngredientCodes,
            Set<String> duplicatedNewIngredientCodes) {
        List<ValidationErrorResponse> errors = new ArrayList<>();

        UUID supplierId = readUuid(payload.get("supplierId"));
        if (supplierId == null) {
            errors.add(new ValidationErrorResponse("supplierId", "Nhà cung cấp không được để trống hoặc không tồn tại"));
        } else if (!existsSupplier(existingSupplierIds, supplierId)) {
            errors.add(new ValidationErrorResponse("supplierId", "Nhà cung cấp không tồn tại"));
        }

        UUID locationId = readUuid(payload.get("locationId"));
        if (locationId == null) {
            errors.add(new ValidationErrorResponse("locationId", "Kho nhận không được để trống hoặc không tồn tại"));
        } else if (!existsLocation(existingLocationIds, locationId)) {
            errors.add(new ValidationErrorResponse("locationId", "Kho nhận không tồn tại"));
        }

        List<Map<String, Object>> lines = readItems(payload);
        if (lines.isEmpty()) {
            errors.add(new ValidationErrorResponse("items", "Phiếu nhập phải có ít nhất 1 dòng nguyên liệu"));
        }
        for (int i = 0; i < lines.size(); i++) {
            String prefix = "items[" + i + "].";
            Map<String, Object> line = lines.get(i);
            UUID inventoryItemId = readUuid(line.get("inventoryItemId"));
            String newIngredientName = readString(line.get("newIngredientName"));

            if (inventoryItemId != null) {
                if (!existsInventoryItem(existingInventoryItemIds, inventoryItemId)) {
                    errors.add(new ValidationErrorResponse(prefix + "inventoryItemId", "Nguyên liệu không tồn tại"));
                }
            } else if (!newIngredientName.isBlank()) {
                // Nguyên liệu mới — sẽ được tự tạo lúc duyệt, chỉ cần đơn vị hợp lệ.
                UUID newIngredientUnitId = readUuid(line.get("newIngredientUnitId"));
                if (newIngredientUnitId == null) {
                    errors.add(new ValidationErrorResponse(prefix + "newIngredientUnitId", "Đơn vị của nguyên liệu mới không được để trống hoặc không tồn tại"));
                } else if (!existsUnit(existingUnitIds, newIngredientUnitId)) {
                    errors.add(new ValidationErrorResponse(prefix + "newIngredientUnitId", "Đơn vị không tồn tại"));
                }
                // Catalog từ chối mã trùng lúc duyệt (tạo nguyên liệu) — báo sớm ở đây
                String newCode = newIngredientCodeOf(line);
                if (newCode != null) {
                    Set<String> existingCodes = existingNewIngredientCodes != null
                            ? existingNewIngredientCodes
                            : findExistingIngredientCodes(List.of(newCode));
                    if (existingCodes.contains(newCode.toUpperCase())) {
                        errors.add(new ValidationErrorResponse(prefix + "newIngredientCode", "Mã nguyên liệu đã tồn tại: " + newCode));
                    } else if (duplicatedNewIngredientCodes.contains(newCode.toUpperCase())) {
                        errors.add(new ValidationErrorResponse(prefix + "newIngredientCode", "Mã nguyên liệu mới bị trùng với dòng khác trong file: " + newCode));
                    }
                }
            } else {
                errors.add(new ValidationErrorResponse(prefix + "inventoryItemId",
                        "Nguyên liệu không được để trống hoặc không tồn tại (dùng sheet \"Nguyên liệu mới\" nếu muốn tạo mới)"));
            }

            BigDecimal quantity = readDecimal(line.get("orderedQuantity"));
            if (quantity == null || quantity.signum() <= 0) {
                errors.add(new ValidationErrorResponse(prefix + "orderedQuantity", "Số lượng phải lớn hơn 0"));
            }
        }

        return errors;
    }

    private void addIfPresent(Set<UUID> ids, UUID id) {
        if (id != null) ids.add(id);
    }

    private boolean existsSupplier(Set<UUID> preloadedExistingIds, UUID id) {
        if (preloadedExistingIds != null) return preloadedExistingIds.contains(id);
        return !catalogClient.getSuppliersBatch(List.of(id)).isEmpty();
    }

    private boolean existsLocation(Set<UUID> preloadedExistingIds, UUID id) {
        return preloadedExistingIds != null ? preloadedExistingIds.contains(id) : locationRepository.existsById(id);
    }

    private boolean existsInventoryItem(Set<UUID> preloadedExistingIds, UUID id) {
        if (preloadedExistingIds != null) return preloadedExistingIds.contains(id);
        return inventoryItemRepository.findById(id)
                .filter(i -> i.getItemType() == InventoryItemType.INGREDIENT && !Boolean.TRUE.equals(i.getIsDeleted()))
                .isPresent();
    }

    private boolean existsUnit(Set<UUID> preloadedExistingIds, UUID id) {
        if (preloadedExistingIds != null) return preloadedExistingIds.contains(id);
        return !catalogClient.getUnitsBatch(List.of(id)).isEmpty();
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        resolveNewIngredients(payload);
        CreatePurchaseReceiptRequest request = toRequest(payload);
        for (ConstraintViolation<CreatePurchaseReceiptRequest> violation : validator.validate(request)) {
            throw new BadRequestException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        InventoryDocumentResponse response = inventoryDocumentService.createPurchaseReceipt(request);
        return response.id();
    }

    /**
     * Với dòng chưa có {@code inventoryItemId} nhưng có {@code newIngredientName} (khai báo ở sheet
     * "Nguyên liệu mới"): tạo nguyên liệu mới bên Catalog-Service — dùng đúng {@code newIngredientCode}
     * nếu người dùng tự nhập, để trống thì tự sinh mã. Catalog-Service tự đồng bộ InventoryItem tương
     * ứng ngay trong lúc tạo (xem {@code IngredientServiceImpl.createWithCode()}), sau đó tra lại
     * InventoryItem vừa được tạo để lấy id gán vào dòng.
     * Đồng thời thêm các nguyên liệu mới này vào danh sách nguyên liệu mà nhà cung cấp của phiếu được phép cấp.
     * Chỉ chạy lúc duyệt (commit).
     */
    private void resolveNewIngredients(Map<String, Object> payload) {
        UUID supplierId = readUuid(payload.get("supplierId"));
        List<UUID> createdIngredientIds = new ArrayList<>();

        for (Map<String, Object> line : readItems(payload)) {
            if (line.get("inventoryItemId") != null) continue;
            String name = readString(line.get("newIngredientName"));
            if (name.isBlank()) continue;

            String code = readString(line.get("newIngredientCode"));
            UUID unitId = readUuid(line.get("newIngredientUnitId"));
            CatalogClient.IngredientResponse created = catalogClient.createIngredient(
                    code.isBlank() ? null : code,
                    new CatalogClient.CreateIngredientRequest(name, unitId, null, "ACTIVE"));

            InventoryItem createdItem = inventoryItemRepository
                    .findByReferenceIdAndItemTypeLocked(created.id(), InventoryItemType.INGREDIENT)
                    .orElseGet(() -> {
                        InventoryItem newItem = new InventoryItem();
                        newItem.setReferenceId(created.id());
                        newItem.setReferenceCode(created.ingredientCode());
                        newItem.setName(created.name());
                        newItem.setItemType(InventoryItemType.INGREDIENT);
                        newItem.setUnitId(created.unitId());
                        newItem.setStatus(InventoryStatus.ACTIVE);
                        newItem.setReorderPoint(created.reorderPoint() != null ? created.reorderPoint() : 0);
                        return inventoryItemRepository.save(newItem);
                    });

            createdIngredientIds.add(created.id());

            line.put("inventoryItemId", createdItem.getId().toString());
            line.put("unitId", createdItem.getUnitId() != null ? createdItem.getUnitId().toString() : null);
            line.remove("newIngredientCode");
            line.remove("newIngredientName");
            line.remove("newIngredientUnitId");
        }

        if (supplierId != null && !createdIngredientIds.isEmpty()) {
            catalogClient.addIngredientsToSupplier(supplierId, createdIngredientIds);
        }
    }

    private CreatePurchaseReceiptRequest toRequest(Map<String, Object> payload) {
        return objectMapper.convertValue(payload, CreatePurchaseReceiptRequest.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readItems(Map<String, Object> payload) {
        Object raw = payload.get("items");
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

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal readDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(value));
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

    private Sheet findSheetByName(Workbook workbook, String normalizedNameContains) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (normalize(workbook.getSheetName(i)).contains(normalizedNameContains)) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    /** Khớp CHÍNH XÁC tên sheet đã chuẩn hoá — dùng khi tên có thể là substring của 1 sheet khác (vd
     * "Nguyên liệu" là substring của "Nguyên liệu mới"). */
    private Sheet findSheetByExactName(Workbook workbook, String normalizedName) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (normalize(workbook.getSheetName(i)).equals(normalizedName)) {
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

    // ---------------------------------------------------------------------
    // File mẫu Excel (5 sheet: Hướng dẫn, Phiếu nhập, Nguyên liệu, Nguyên liệu mới, Tham chiếu)
    // ---------------------------------------------------------------------

    public byte[] buildImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildInstructionSheet(workbook);
            buildReceiptSheet(workbook);
            buildLineSheet(workbook);
            buildNewIngredientSheet(workbook);
            buildReferenceSheet(workbook);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo file mẫu Excel Phiếu nhập kho: " + e.getMessage(), e);
        }
    }

    private void buildInstructionSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Hướng dẫn");

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);

        Font sectionFont = workbook.createFont();
        sectionFont.setBold(true);
        CellStyle sectionStyle = workbook.createCellStyle();
        sectionStyle.setFont(sectionFont);

        CellStyle textStyle = workbook.createCellStyle();

        String[] titleLines = {"HƯỚNG DẪN NHẬP PHIẾU NHẬP KHO TỪ EXCEL"};
        String[][] sections = {
                {
                        "1. Cấu trúc file",
                        "File gồm 5 sheet:",
                        "- \"Hướng dẫn\": sheet này.",
                        "- \"Phiếu nhập\": nhập thông tin từng phiếu nhập kho, mỗi dòng là 1 phiếu.",
                        "- \"Nguyên liệu\": nhập danh sách nguyên liệu cho từng phiếu ở sheet \"Phiếu nhập\".",
                        "- \"Nguyên liệu mới\": khai báo nguyên liệu CHƯA có trong hệ thống (nếu cần).",
                        "- \"Tham chiếu\": tra cứu mã kho/nguyên liệu/nhà cung cấp/đơn vị đã có sẵn."
                },
                {
                        "2. Cách liên kết sheet \"Phiếu nhập\" và sheet \"Nguyên liệu\"",
                        "- Ở sheet \"Phiếu nhập\", cột \"Ref\" là mã tự đặt riêng cho từng dòng trong file này (ví dụ PN1, PN2...), dùng để liên kết — không phải mã hệ thống.",
                        "- Ở sheet \"Nguyên liệu\", cột \"Ref Phiếu nhập\" phải điền đúng giá trị \"Ref\" tương ứng ở sheet \"Phiếu nhập\".",
                        "- 1 phiếu nhập có nhiều dòng nguyên liệu; mỗi phiếu phải có ít nhất 1 dòng nguyên liệu."
                },
                {
                        "3. Cách điền sheet \"Phiếu nhập\"",
                        "- Mã nhà cung cấp (*): bắt buộc, lấy từ sheet \"Tham chiếu\".",
                        "- Mã kho nhận hàng (*): bắt buộc, lấy từ sheet \"Tham chiếu\".",
                        "- Ngày dự kiến giao: định dạng yyyy-MM-dd hoặc dd/MM/yyyy, để trống nếu chưa xác định.",
                        "- Ghi chú: không bắt buộc."
                },
                {
                        "4. Cách điền sheet \"Nguyên liệu\"",
                        "- Ref Phiếu nhập (*): phải khớp với 1 giá trị \"Ref\" ở sheet \"Phiếu nhập\".",
                        "- Mã nguyên liệu (*):",
                        "  + Nếu là nguyên liệu đã có sẵn: điền mã từ sheet \"Tham chiếu\".",
                        "  + Nếu là nguyên liệu mới: điền mã tương ứng ở sheet \"Nguyên liệu mới\" và đánh dấu \"x\" ở cột \"Nguyên liệu mới\".",
                        "- Nguyên liệu mới: đánh dấu \"x\" NẾU nguyên liệu CHƯA có trong hệ thống.",
                        "- Số lượng (*): bắt buộc, phải lớn hơn 0.",
                        "- Ghi chú: không bắt buộc."
                },
                {
                        "5. Cách điền sheet \"Nguyên liệu mới\"",
                        "- Mã nguyên liệu (*): mã nguyên liệu dùng để đối chiếu với sheet \"Nguyên liệu\". Mã này cũng sẽ là mã chính thức của nguyên liệu trong hệ thống.",
                        "- Tên nguyên liệu (*): bắt buộc.",
                        "- Đơn vị (*): bắt buộc, lấy từ sheet \"Tham chiếu\"."
                },
                {
                        "6. Sau khi tải file lên hệ thống",
                        "- Dữ liệu được phân tích thành bản nháp để bạn kiểm tra/sửa trực tiếp trên hệ thống trước khi gửi duyệt.",
                        "- Dữ liệu (phiếu nhập + nguyên liệu + nguyên liệu mới) chỉ được ghi chính thức vào hệ thống sau khi quản lý duyệt."
                },
                {
                        "7. Lưu ý",
                        "- Không đổi tên các sheet, không đổi tên cột header ở dòng đầu tiên của mỗi sheet.",
                        "- Không xoá dòng header."
                }
        };

        int rowIdx = 0;
        for (String line : titleLines) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(22);
            Cell cell = row.createCell(0);
            cell.setCellValue(line);
            cell.setCellStyle(titleStyle);
        }
        rowIdx++;

        for (String[] section : sections) {
            Row sectionRow = sheet.createRow(rowIdx++);
            Cell sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue(section[0]);
            sectionCell.setCellStyle(sectionStyle);
            for (int i = 1; i < section.length; i++) {
                Row row = sheet.createRow(rowIdx++);
                Cell cell = row.createCell(0);
                cell.setCellValue(section[i]);
                cell.setCellStyle(textStyle);
            }
            rowIdx++;
        }

        sheet.setColumnWidth(0, 100 * 256);
    }

    private CellStyle createGreenHeaderStyle(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        if (headerStyle instanceof XSSFCellStyle xssfHeaderStyle) {
            xssfHeaderStyle.setFillForegroundColor(new XSSFColor(BRAND_GREEN_RGB, new DefaultIndexedColorMap()));
        }
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return headerStyle;
    }

    private void buildReceiptSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Phiếu nhập");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Ref", "Mã nhà cung cấp", "Mã kho nhận hàng", "Ngày dự kiến giao", "Ghi chú"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("PN1");
        row1.createCell(1).setCellValue("NCC00001");
        row1.createCell(2).setCellValue("KHO-TONG");
        row1.createCell(3).setCellValue("2026-01-15");
        row1.createCell(4).setCellValue("Nhập nguyên liệu vụ sớm");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("PN2");
        row2.createCell(1).setCellValue("NCC00001");
        row2.createCell(2).setCellValue("KHO-TONG");
        row2.createCell(3).setCellValue("2026-01-20");
        row2.createCell(4).setCellValue("Phiếu nhập bổ sung");

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 2, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildLineSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Nguyên liệu");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Ref Phiếu nhập", "Mã nguyên liệu", "Nguyên liệu mới", "Số lượng", "Ghi chú"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("PN1");
        row1.createCell(1).setCellValue("NL00001");
        row1.createCell(2).setCellValue("");
        row1.createCell(3).setCellValue(100.0);
        row1.createCell(4).setCellValue("");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("PN2");
        row2.createCell(1).setCellValue("NLM00001");
        row2.createCell(2).setCellValue("x");
        row2.createCell(3).setCellValue(25.0);
        row2.createCell(4).setCellValue("Nguyên liệu mới, xem sheet \"Nguyên liệu mới\"");

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 2, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildNewIngredientSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Nguyên liệu mới");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Mã nguyên liệu", "Tên nguyên liệu", "Đơn vị"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("NLM00001");
        row1.createCell(1).setCellValue("Hoa nhài tươi");
        row1.createCell(2).setCellValue("Kg");

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 1, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Tham chiếu");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        // Mỗi thực thể tham chiếu gồm 2 cột: Mã và Tên
        String[] headers = {
                "Mã kho", "Tên kho",
                "Mã nguyên liệu", "Tên nguyên liệu",
                "Mã nhà cung cấp", "Tên nhà cung cấp",
                "Ký hiệu đơn vị", "Tên đơn vị"
        };
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        record CodeName(String code, String name) {}

        List<CodeName> locations = locationRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> l.getCode() != null && !l.getCode().isBlank())
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .map(l -> new CodeName(l.getCode(), l.getName() != null ? l.getName() : ""))
                .toList();

        List<CodeName> items = inventoryItemRepository.findAll().stream()
                .filter(i -> i.getItemType() == InventoryItemType.INGREDIENT && !Boolean.TRUE.equals(i.getIsDeleted()))
                .filter(i -> i.getReferenceCode() != null && !i.getReferenceCode().isBlank())
                .sorted((a, b) -> a.getReferenceCode().compareToIgnoreCase(b.getReferenceCode()))
                .map(i -> new CodeName(i.getReferenceCode(), i.getName() != null ? i.getName() : ""))
                .toList();

        List<CodeName> suppliers = catalogClient.getAllSuppliers().stream()
                .filter(s -> s.code() != null && !s.code().isBlank())
                .sorted((a, b) -> a.code().compareToIgnoreCase(b.code()))
                .map(s -> new CodeName(s.code(), s.name() != null ? s.name() : ""))
                .toList();

        List<CodeName> units = catalogClient.getAllUnits().stream()
                .filter(u -> u.symbol() != null && !u.symbol().isBlank())
                .sorted((a, b) -> a.symbol().compareToIgnoreCase(b.symbol()))
                .map(u -> new CodeName(u.symbol(), u.name() != null ? u.name() : ""))
                .toList();

        int maxRows = Math.max(Math.max(locations.size(), items.size()), Math.max(suppliers.size(), units.size()));
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i + 1);
            if (i < locations.size()) {
                row.createCell(0).setCellValue(locations.get(i).code());
                row.createCell(1).setCellValue(locations.get(i).name());
            }
            if (i < items.size()) {
                row.createCell(2).setCellValue(items.get(i).code());
                row.createCell(3).setCellValue(items.get(i).name());
            }
            if (i < suppliers.size()) {
                row.createCell(4).setCellValue(suppliers.get(i).code());
                row.createCell(5).setCellValue(suppliers.get(i).name());
            }
            if (i < units.size()) {
                row.createCell(6).setCellValue(units.get(i).code());
                row.createCell(7).setCellValue(units.get(i).name());
            }
        }

        sheet.createFreezePane(0, 1);
        widenColumns(sheet, headers.length);
    }

    /** Auto-size rồi thêm đệm cho các cột trong file mẫu Excel để dễ đọc hơn, không bị chật chữ. */
    private void widenColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            try {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i) + 3000;
                sheet.setColumnWidth(i, Math.min(Math.max(width, 4500), 18000));
            } catch (Exception ignored) {
            }
        }
    }
}
