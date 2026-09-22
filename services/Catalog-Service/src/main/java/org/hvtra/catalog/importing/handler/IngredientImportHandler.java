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
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.IngredientService;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.response.ValidationErrorResponse;
import org.hvtra.commonservice.common_importing.handler.ImportTypeHandler;
import org.hvtra.commonservice.common_importing.handler.ParsedImportRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
 * Import Nguyên liệu: 1 sheet dữ liệu ("Nguyên liệu"), mỗi dòng là 1 nguyên liệu (Tên, Đơn vị, Điểm đặt
 * lại, Trạng thái) — mã nguyên liệu do hệ thống tự sinh (giống hệt lúc tạo thủ công qua form), không nhập
 * từ Excel. Payload khớp 1:1 field của {@link IngredientRequest} để commit() convertValue thẳng.
 */
@Component
public class IngredientImportHandler implements ImportTypeHandler {

    private final UnitRepository unitRepository;
    private final IngredientService ingredientService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public IngredientImportHandler(
            UnitRepository unitRepository,
            IngredientService ingredientService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.unitRepository = unitRepository;
        this.ingredientService = ingredientService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportType type() {
        return ImportType.INGREDIENT;
    }

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = findSheetByName(workbook, "nguyen lieu");
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                return List.of();
            }
            int rowCount = sheet.getPhysicalNumberOfRows();
            if (rowCount <= 1) {
                return List.of();
            }

            int headerRowIndex = findHeaderRowIndex(sheet, "ten nguyen lieu", "don vi");
            Row headerRow = sheet.getRow(headerRowIndex);
            int colName = -1, colUnit = -1, colReorderPoint = -1, colStatus = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                    if (normalized.contains("ten nguyen lieu")) {
                        colName = c;
                    } else if (normalized.contains("don vi")) {
                        colUnit = c;
                    } else if (normalized.contains("diem dat lai") || normalized.contains("ton kho toi thieu") || normalized.contains("reorder")) {
                        colReorderPoint = c;
                    } else if (normalized.contains("trang thai")) {
                        colStatus = c;
                    }
                }
            }
            if (colName == -1) colName = 0;
            if (colUnit == -1) colUnit = 1;
            if (colReorderPoint == -1) colReorderPoint = 2;
            if (colStatus == -1) colStatus = 3;

            List<Unit> units = unitRepository.findAll().stream()
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .toList();

            List<ParsedImportRow> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name = getCellValueAsString(row.getCell(colName)).trim();
                String unitName = getCellValueAsString(row.getCell(colUnit)).trim();
                String reorderPointStr = getCellValueAsString(row.getCell(colReorderPoint)).trim();
                String statusStr = getCellValueAsString(row.getCell(colStatus)).trim();

                if (name.isBlank()) {
                    continue;
                }

                Unit unit = resolveUnit(units, unitName);
                Integer reorderPoint = parseInt(reorderPointStr);
                Status status = "Ngừng hoạt động".equalsIgnoreCase(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr)
                        ? Status.INACTIVE : Status.ACTIVE;

                Map<String, Object> payload = new HashMap<>();
                payload.put("name", name);
                payload.put("unitId", unit != null ? unit.getId().toString() : null);
                payload.put("reorderPoint", reorderPoint);
                payload.put("status", status.name());

                rows.add(new ParsedImportRow(r + 1, payload));
            }

            return mergeDuplicateRows(rows);
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc file Excel nguyên liệu: " + e.getMessage());
        }
    }

    /**
     * Các dòng Excel giống hệt nhau ở TẤT CẢ các trường chỉ giữ lại dòng xuất hiện đầu tiên;
     * occurrenceCount ghi lại số lần dòng đó lặp lại để trả về FE cảnh báo.
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
        return String.join("",
                String.valueOf(payload.get("name")).trim().toLowerCase(),
                String.valueOf(payload.get("unitId")),
                String.valueOf(payload.get("reorderPoint")),
                String.valueOf(payload.get("status")));
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        return validate(payload, null);
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        // Gộp toàn bộ unitId của cả batch lại, chỉ tra cứu tồn tại bằng 1 query findAllById()
        // thay vì N query existsById() riêng lẻ (mỗi dòng Excel 1 query).
        Set<UUID> unitIds = new HashSet<>();
        for (Map<String, Object> payload : payloads) {
            UUID unitId = readUuid(payload.get("unitId"));
            if (unitId != null) unitIds.add(unitId);
        }
        Set<UUID> existingUnitIds = unitIds.isEmpty()
                ? Set.of()
                : unitRepository.findAllById(unitIds).stream().map(Unit::getId).collect(Collectors.toSet());

        return payloads.stream().map(payload -> validate(payload, existingUnitIds)).toList();
    }

    private List<ValidationErrorResponse> validate(Map<String, Object> payload, Set<UUID> preloadedExistingUnitIds) {
        IngredientRequest request = toRequest(payload);
        List<ValidationErrorResponse> errors = new ArrayList<>();

        for (ConstraintViolation<IngredientRequest> violation : validator.validate(request)) {
            errors.add(new ValidationErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()));
        }

        if (request.unitId() != null) {
            boolean exists = preloadedExistingUnitIds != null
                    ? preloadedExistingUnitIds.contains(request.unitId())
                    : unitRepository.existsById(request.unitId());
            if (!exists) {
                errors.add(new ValidationErrorResponse("unitId", "Đơn vị không tồn tại"));
            }
        }

        return errors;
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        IngredientRequest request = toRequest(payload);
        return ingredientService.create(request).id();
    }

    private IngredientRequest toRequest(Map<String, Object> payload) {
        return objectMapper.convertValue(payload, IngredientRequest.class);
    }

    private UUID readUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return (int) Double.parseDouble(value.replace(",", ""));
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
