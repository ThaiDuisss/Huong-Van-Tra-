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
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.UnitService;
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

@Component
public class UnitImportHandler implements ImportTypeHandler {

    private final UnitRepository unitRepository;
    private final UnitService unitService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public UnitImportHandler(
            UnitRepository unitRepository,
            UnitService unitService,
            Validator validator,
            ObjectMapper objectMapper) {
        this.unitRepository = unitRepository;
        this.unitService = unitService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportType type() {
        return ImportType.UNIT;
    }

    @Override
    public List<ParsedImportRow> parseExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = findSheetByName(workbook, "don vi");
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

            int headerRowIndex = findHeaderRowIndex(sheet, "ten don vi", "ky hieu");
            Row headerRow = sheet.getRow(headerRowIndex);
            int colName = -1, colSymbol = -1, colType = -1, colBaseUnit = -1, colConversionRate = -1, colStatus = -1, colPrecisionScale = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String normalized = normalize(getCellValueAsString(headerRow.getCell(c)));
                    if (normalized.contains("ten don vi")) {
                        colName = c;
                    } else if (normalized.contains("ky hieu")) {
                        colSymbol = c;
                    } else if (normalized.contains("loai\b") || normalized.contains("type")) {
                        colType = c;
                    } else if (normalized.contains("don vi goc") || normalized.contains("base unit")) {
                        colBaseUnit = c;
                    } else if (normalized.contains("ti le") || normalized.contains("conversion")) {
                        colConversionRate = c;
                    } else if (normalized.contains("trang thai")) {
                        colStatus = c;
                    } else if (normalized.contains("do chinh xac") || normalized.contains("precision")) {
                        colPrecisionScale = c;
                    }
                }
            }
            if (colName == -1) colName = 0;
            if (colSymbol == -1) colSymbol = 1;
            if (colType == -1) colType = 2;
            if (colBaseUnit == -1) colBaseUnit = 3;
            if (colConversionRate == -1) colConversionRate = 4;
            if (colPrecisionScale == -1) colPrecisionScale = 5;
            if (colStatus == -1) colStatus = 6;

            List<Unit> units = unitRepository.findAll().stream()
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .toList();

            List<ParsedImportRow> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name = getCellValueAsString(row.getCell(colName)).trim();
                String symbol = getCellValueAsString(row.getCell(colSymbol)).trim();
                String typeStr = getCellValueAsString(row.getCell(colType)).trim();
                String baseUnitName = getCellValueAsString(row.getCell(colBaseUnit)).trim();
                String conversionRateStr = getCellValueAsString(row.getCell(colConversionRate)).trim();
                String statusStr = getCellValueAsString(row.getCell(colStatus)).trim();
                String precisionScaleStr = getCellValueAsString(row.getCell(colPrecisionScale)).trim();

                if (name.isBlank() || symbol.isBlank()) {
                    continue;
                }

                UnitType type = resolveUnitType(typeStr);
                Unit baseUnit = baseUnitName.isBlank() ? null : resolveUnit(units, baseUnitName);
                BigDecimal conversionRate = parseDecimal(conversionRateStr);
                if (conversionRate == null) {
                    conversionRate = BigDecimal.ONE;
                }
                Status status = "Ngừng hoạt động".equalsIgnoreCase(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr)
                        ? Status.INACTIVE : Status.ACTIVE;
                Integer precisionScale = parseInt(precisionScaleStr);
                if (precisionScale == null) {
                    precisionScale = 0;
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("name", name);
                payload.put("symbol", symbol);
                payload.put("type", type.name());
                payload.put("baseUnitId", baseUnit != null ? baseUnit.getId().toString() : null);
                payload.put("conversionRate", conversionRate);
                payload.put("precisionScale", precisionScale);
                payload.put("status", status.name());

                rows.add(new ParsedImportRow(r + 1, payload));
            }

            return mergeDuplicateRows(rows);
        } catch (Exception e) {
            throw new BadRequestException("Lỗi đọc file Excel đơn vị: " + e.getMessage());
        }
    }

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
                String.valueOf(payload.get("symbol")).trim().toLowerCase(),
                String.valueOf(payload.get("type")),
                String.valueOf(payload.get("baseUnitId")),
                String.valueOf(payload.get("conversionRate")),
                String.valueOf(payload.get("status")));
    }

    @Override
    public List<ValidationErrorResponse> validate(Map<String, Object> payload) {
        return validate(payload, null);
    }

    @Override
    public List<List<ValidationErrorResponse>> validateAll(List<Map<String, Object>> payloads) {
        Set<UUID> unitIds = new HashSet<>();
        for (Map<String, Object> payload : payloads) {
            UUID baseUnitId = readUuid(payload.get("baseUnitId"));
            if (baseUnitId != null) unitIds.add(baseUnitId);
        }
        Set<UUID> existingUnitIds = unitIds.isEmpty()
                ? Set.of()
                : unitRepository.findAllById(unitIds).stream().map(Unit::getId).collect(Collectors.toSet());

        return payloads.stream().map(payload -> validate(payload, existingUnitIds)).toList();
    }

    private List<ValidationErrorResponse> validate(Map<String, Object> payload, Set<UUID> preloadedExistingUnitIds) {
        UnitRequest request = toRequest(payload);
        List<ValidationErrorResponse> errors = new ArrayList<>();

        for (ConstraintViolation<UnitRequest> violation : validator.validate(request)) {
            errors.add(new ValidationErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()));
        }

        if (request.baseUnitId() != null) {
            boolean exists = preloadedExistingUnitIds != null
                    ? preloadedExistingUnitIds.contains(request.baseUnitId())
                    : unitRepository.existsById(request.baseUnitId());
            if (!exists) {
                errors.add(new ValidationErrorResponse("baseUnitId", "Đơn vị gốc không tồn tại"));
            }
        }

        return errors;
    }

    @Override
    public UUID commit(Map<String, Object> payload) {
        UnitRequest request = toRequest(payload);
        return unitService.create(request).id();
    }

    private UnitRequest toRequest(Map<String, Object> payload) {
        return objectMapper.convertValue(payload, UnitRequest.class);
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

    private UnitType resolveUnitType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return UnitType.COUNT;
        String clean = typeStr.trim().toUpperCase();
        try {
            return UnitType.valueOf(clean);
        } catch (IllegalArgumentException e) {
            if (clean.contains("PACKAGING") || clean.contains("DÓNG GÓI")) return UnitType.PACKAGING;
            if (clean.contains("COUNT") || clean.contains("CÁI")) return UnitType.COUNT;
            if (clean.contains("WEIGHT") || clean.contains("CÂN NẶNG")) return UnitType.WEIGHT;
            if (clean.contains("VOLUME") || clean.contains("THỂ TÍCH")) return UnitType.VOLUME;
            return UnitType.COUNT;
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
