package org.hvtra.iventoryservice.importing.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.hvtra.iventoryservice.document.dto.request.CreatePurchaseReceiptRequest;
import org.hvtra.iventoryservice.document.dto.response.InventoryDocumentResponse;
import org.hvtra.iventoryservice.document.service.InventoryDocumentService;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.commonservice.importing.domain.enums.ImportType;
import org.hvtra.commonservice.importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.importing.handler.ParsedImportRow;
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
 * Import Phiếu nhập kho (GoodsReceipt): 1 sheet dữ liệu, mỗi dòng Excel là 1 dòng nguyên liệu
 * (Mã nhà cung cấp, Mã kho nhận, Mã đơn mua (PO), Mã nguyên liệu, Số lượng đặt, Số lô NCC, Ghi chú).
 * Các dòng cùng Nhà cung cấp + Kho nhận + PO được gộp thành 1 {@code ImportBatchItem} (1 phiếu nhập
 * nhiều dòng), giống hệt logic gộp nhóm trước đây ở goodsReceiptExcel.ts (client-side, nay thay bằng
 * flow Draft→Duyệt dùng chung). Payload khớp 1:1 field của {@link CreatePurchaseReceiptRequest} để
 * commit() có thể convertValue thẳng, không cần lớp remap trung gian.
 */
@Component
public class GoodsReceiptImportHandler implements ImportTypeHandler {

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

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            if (rowCount <= 1) {
                return List.of();
            }

            Map<String, CatalogClient.SupplierLookupResponse> suppliersByCode = catalogClient.getAllSuppliers().stream()
                    .filter(s -> s.code() != null)
                    .collect(Collectors.toMap(s -> s.code().toUpperCase().trim(), s -> s, (a, b) -> a));
            Map<String, InventoryLocation> locationsByCode = locationRepository.findAll().stream()
                    .filter(l -> l.getCode() != null && !Boolean.TRUE.equals(l.getIsDeleted()))
                    .collect(Collectors.toMap(l -> l.getCode().toUpperCase().trim(), l -> l, (a, b) -> a));
            Map<String, InventoryItem> itemsByCode = inventoryItemRepository.findAll().stream()
                    .filter(i -> i.getReferenceCode() != null && !Boolean.TRUE.equals(i.getIsDeleted()))
                    .collect(Collectors.toMap(i -> i.getReferenceCode().toUpperCase().trim(), i -> i, (a, b) -> a));

            int headerRowIndex = findHeaderRowIndex(sheet, "ma nha cung cap", "ma nguyen lieu");
            Row headerRow = sheet.getRow(headerRowIndex);
            int colSupplier = -1, colLocation = -1, colPo = -1, colItem = -1, colQuantity = -1, colLot = -1, colNote = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                    if (normalized.contains("ma nha cung cap")) {
                        colSupplier = c;
                    } else if (normalized.contains("ma kho")) {
                        colLocation = c;
                    } else if (normalized.contains("ma don mua") || normalized.contains("ma po")) {
                        colPo = c;
                    } else if (normalized.contains("ma nguyen lieu")) {
                        colItem = c;
                    } else if (normalized.contains("so luong")) {
                        colQuantity = c;
                    } else if (normalized.contains("so lo")) {
                        colLot = c;
                    } else if (normalized.contains("ghi chu")) {
                        colNote = c;
                    }
                }
            }
            if (colSupplier == -1) colSupplier = 0;
            if (colLocation == -1) colLocation = 2;
            if (colPo == -1) colPo = 4;
            if (colItem == -1) colItem = 5;
            if (colQuantity == -1) colQuantity = 7;
            if (colLot == -1) colLot = 9;
            if (colNote == -1) colNote = 10;

            LinkedHashMap<String, Map<String, Object>> payloadsByGroupKey = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> rowIndexByGroupKey = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> occurrenceCountByGroupKey = new LinkedHashMap<>();

            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String supplierCode = getCellValueAsString(row.getCell(colSupplier)).trim();
                String locationCode = getCellValueAsString(row.getCell(colLocation)).trim();
                String poCode = getCellValueAsString(row.getCell(colPo)).trim();
                String itemCode = getCellValueAsString(row.getCell(colItem)).trim();
                String quantityStr = getCellValueAsString(row.getCell(colQuantity)).trim();
                String lot = getCellValueAsString(row.getCell(colLot)).trim();
                String note = getCellValueAsString(row.getCell(colNote)).trim();

                if (supplierCode.isBlank() && itemCode.isBlank() && quantityStr.isBlank()) {
                    continue;
                }

                CatalogClient.SupplierLookupResponse supplier = supplierCode.isBlank()
                        ? null : suppliersByCode.get(supplierCode.toUpperCase());
                InventoryLocation location = locationCode.isBlank()
                        ? null : locationsByCode.get(locationCode.toUpperCase());
                InventoryItem item = itemCode.isBlank() ? null : itemsByCode.get(itemCode.toUpperCase());
                BigDecimal quantity = parseDecimal(quantityStr);

                String groupKey = poCode.isBlank()
                        ? "__row_" + (r + 1)
                        : supplierCode.toUpperCase() + "__" + locationCode.toUpperCase() + "__" + poCode.toUpperCase();

                Map<String, Object> payload = payloadsByGroupKey.get(groupKey);
                if (payload == null) {
                    payload = new HashMap<>();
                    payload.put("supplierId", supplier != null ? supplier.id().toString() : null);
                    payload.put("locationId", location != null ? location.getId().toString() : null);
                    payload.put("branchId", null);
                    payload.put("expectedDate", null);
                    payload.put("referenceType", "PURCHASE_ORDER");
                    payload.put("referenceId", poCode.isBlank() ? null : poCode);
                    payload.put("note", null);
                    payload.put("items", new ArrayList<Map<String, Object>>());
                    payloadsByGroupKey.put(groupKey, payload);
                    rowIndexByGroupKey.put(groupKey, r + 1);
                    occurrenceCountByGroupKey.put(groupKey, 0);
                }

                Map<String, Object> line = new HashMap<>();
                line.put("inventoryItemId", item != null ? item.getId().toString() : null);
                line.put("unitId", item != null && item.getUnitId() != null ? item.getUnitId().toString() : null);
                line.put("orderedQuantity", quantity);
                line.put("note", buildLineNote(note, lot));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("items");
                lines.add(line);
                occurrenceCountByGroupKey.merge(groupKey, 1, Integer::sum);
            }

            List<ParsedImportRow> rows = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : payloadsByGroupKey.entrySet()) {
                String key = entry.getKey();
                rows.add(new ParsedImportRow(rowIndexByGroupKey.get(key), entry.getValue(), occurrenceCountByGroupKey.get(key)));
            }
            return rows;
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc file Excel phiếu nhập kho: " + e.getMessage());
        }
    }

    private String buildLineNote(String note, String supplierLot) {
        if (supplierLot.isBlank()) {
            return note.isBlank() ? null : note;
        }
        String lotNote = "Lô NCC: " + supplierLot;
        return note.isBlank() ? lotNote : note + " (" + lotNote + ")";
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        return validate(payload, null, null, null);
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        Set<UUID> supplierIds = new HashSet<>();
        Set<UUID> locationIds = new HashSet<>();
        Set<UUID> inventoryItemIds = new HashSet<>();
        for (Map<String, Object> payload : payloads) {
            addIfPresent(supplierIds, readUuid(payload.get("supplierId")));
            addIfPresent(locationIds, readUuid(payload.get("locationId")));
            for (Map<String, Object> line : readItems(payload)) {
                addIfPresent(inventoryItemIds, readUuid(line.get("inventoryItemId")));
            }
        }

        Set<UUID> existingSupplierIds = supplierIds.isEmpty() ? Set.of() : catalogClient.getSuppliersBatch(List.copyOf(supplierIds))
                .stream().map(CatalogClient.SupplierResponse::id).collect(Collectors.toSet());
        Set<UUID> existingLocationIds = locationIds.isEmpty() ? Set.of() : locationRepository.findAllById(locationIds)
                .stream().map(InventoryLocation::getId).collect(Collectors.toSet());
        Set<UUID> existingInventoryItemIds = inventoryItemIds.isEmpty() ? Set.of() : inventoryItemRepository.findAllById(inventoryItemIds)
                .stream().map(InventoryItem::getId).collect(Collectors.toSet());

        return payloads.stream()
                .map(payload -> validate(payload, existingSupplierIds, existingLocationIds, existingInventoryItemIds))
                .toList();
    }

    private List<ValidationErrorResponse> validate(
            Map<String, Object> payload,
            Set<UUID> existingSupplierIds,
            Set<UUID> existingLocationIds,
            Set<UUID> existingInventoryItemIds) {
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
            if (inventoryItemId == null) {
                errors.add(new ValidationErrorResponse(prefix + "inventoryItemId", "Nguyên liệu không được để trống hoặc không tồn tại"));
            } else if (!existsInventoryItem(existingInventoryItemIds, inventoryItemId)) {
                errors.add(new ValidationErrorResponse(prefix + "inventoryItemId", "Nguyên liệu không tồn tại"));
            }

            BigDecimal quantity = readDecimal(line.get("orderedQuantity"));
            if (quantity == null || quantity.signum() <= 0) {
                errors.add(new ValidationErrorResponse(prefix + "orderedQuantity", "Số lượng đặt phải lớn hơn 0"));
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
        return preloadedExistingIds != null ? preloadedExistingIds.contains(id) : inventoryItemRepository.existsById(id);
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        CreatePurchaseReceiptRequest request = toRequest(payload);
        for (ConstraintViolation<CreatePurchaseReceiptRequest> violation : validator.validate(request)) {
            throw new BadRequestException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        InventoryDocumentResponse response = inventoryDocumentService.createPurchaseReceipt(request);
        return response.id();
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
