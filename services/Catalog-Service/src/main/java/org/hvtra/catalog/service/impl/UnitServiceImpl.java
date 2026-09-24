package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.UnitMapper;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.UnitService;
import org.hvtra.catalog.util.ExcelExportHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UnitServiceImpl implements UnitService {
    private final UnitRepository repository;
    private final UnitMapper mapper;

    public UnitServiceImpl(UnitRepository repository, UnitMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<UnitResponse> findAll(String name, Status status, UnitType type, Pageable pageable) {
        Specification<Unit> spec = (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (!Long.class.equals(resultType) && !long.class.equals(resultType)) {
                root.fetch("baseUnit", JoinType.LEFT); // tên đơn vị gốc, không lazy-load từng dòng
            }
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponseWithBaseUnitName));
    }

    @Override
    public PageResponse<LookupResponse> lookup(String search, boolean baseOnly, List<UUID> ids, Pageable pageable) {
        if (ids != null && !ids.isEmpty()) {
            // Lấy tên theo id cho giá trị đã chọn sẵn trong dropdown
            List<LookupResponse> content = repository.findAllById(ids).stream()
                    .map(u -> new LookupResponse(u.getId().toString(), u.getName()))
                    .toList();
            return new PageResponse<>(content, 0, content.size(), content.size(), 1, true, true);
        }
        Specification<Unit> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("status"), Status.ACTIVE));
            if (baseOnly) {
                predicates.add(cb.isNull(root.get("baseUnit"))); // chỉ đơn vị gốc
            }

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), searchPattern),
                        cb.like(cb.lower(root.get("symbol")), searchPattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Unit> page = repository.findAll(spec, pageable);

        return new PageResponse<>(
                page.getContent().stream()
                        .map(u -> new LookupResponse(u.getId().toString(), u.getName()))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Override
    public List<UnitResponse> findAll() {
        return repository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<UnitResponse> findBases() {
        return repository.findByBaseUnitIsNullAndIsDeletedFalseAndStatus(Status.ACTIVE).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UnitResponse create(UnitRequest request) {
        validateSymbol(request.symbol(), null);
        Unit unit = new Unit();
        Unit baseUnit = resolveBaseUnit(request);
        mapper.update(unit, request, baseUnit);
        return mapper.toResponse(repository.save(unit));
    }

    @Override
    @Transactional
    public UnitResponse update(UUID id, UnitRequest request) {
        validateSymbol(request.symbol(), id);
        Unit unit = get(id);
        Unit baseUnit = resolveBaseUnit(request);
        mapper.update(unit, request, baseUnit);
        return mapper.toResponse(repository.save(unit));
    }

    private void validateSymbol(String symbol, UUID currentId) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        String cleanSymbol = symbol.trim();
        boolean exists = currentId == null
                ? repository.existsBySymbolIgnoreCaseAndIsDeletedFalse(cleanSymbol)
                : repository.existsBySymbolIgnoreCaseAndIdNotAndIsDeletedFalse(cleanSymbol, currentId);
        if (exists) {
            throw new BadRequestException("Ký hiệu đơn vị '" + cleanSymbol + "' đã tồn tại trong hệ thống.");
        }
    }

    @Override
    @Transactional
    public UnitResponse updateStatus(UUID id, Status status) {
        Unit unit = get(id);
        unit.setStatus(status);
        return mapper.toResponse(repository.save(unit));
    }

    private Unit get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unit", id));
    }

    /**
     * Resolve entity đơn vị gốc từ request.
     */
    private Unit resolveBaseUnit(UnitRequest request) {
        if (request.baseUnitId() != null) {
            if (request.conversionRate() == null || request.conversionRate().compareTo(new BigDecimal("0.0001")) < 0) {
                throw new BadRequestException("Tỷ lệ quy đổi phải lớn hơn hoặc bằng 0.0001");
            }
            return repository.findById(request.baseUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit (base)", request.baseUnitId()));
        }
        return null;
    }

    @Override
    public List<UnitResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllById(ids).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Unit unit = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", id));
        if (unit.getIsDeleted()) {
            throw new ResourceNotFoundException("Unit", id);
        }
        unit.setIsDeleted(true);
        repository.save(unit);
    }

    @Override
    public byte[] exportToExcel(String search, Status status, UnitType type) {
        Specification<Unit> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("symbol")), pattern)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<Unit> units = repository.findAll(spec, Sort.by("name"));

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Danh sách Đơn vị tính");
            ExcelExportHelper.ExportStyles styles = ExcelExportHelper.createExportStyles(workbook);

            String[] headers = {"STT", "Tên đơn vị", "Ký hiệu", "Phân loại", "Độ chính xác", "Trạng thái"};
            ExcelExportHelper.createReportHeader(sheet, styles, "BÁO CÁO DANH SÁCH ĐƠN VỊ TÍNH", headers);

            int rowIdx = 5;
            int stt = 1;
            for (var u : units) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(22);
                
                Cell cellStt = row.createCell(1);
                cellStt.setCellValue(stt++);
                cellStt.setCellStyle(styles.centerStyle());
                
                Cell cellName = row.createCell(2);
                cellName.setCellValue(u.getName() != null ? u.getName() : "");
                cellName.setCellStyle(styles.textStyle());
                
                Cell cellSymbol = row.createCell(3);
                cellSymbol.setCellValue(u.getSymbol() != null ? u.getSymbol() : "");
                cellSymbol.setCellStyle(styles.centerStyle());
                
                Cell cellType = row.createCell(4);
                cellType.setCellValue(formatUnitType(u.getType()));
                cellType.setCellStyle(styles.centerStyle());
                
                Cell cellPrecision = row.createCell(5);
                cellPrecision.setCellValue(u.getPrecisionScale() != null ? u.getPrecisionScale() : 0);
                cellPrecision.setCellStyle(styles.centerStyle());
                
                Cell cellStatus = row.createCell(6);
                cellStatus.setCellValue(u.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
                cellStatus.setCellStyle(styles.centerStyle());
            }

            ExcelExportHelper.finalizeSheet(sheet, headers.length, rowIdx);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất file Excel đơn vị tính: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] getImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Mẫu nhập đơn vị");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            String[] headers = {
                    "Tên đơn vị (*)",
                    "Ký hiệu (*)",
                    "Phân loại (Khối lượng / Thể tích / Đếm) (*)",
                    "Độ chính xác (0-10)",
                    "Trạng thái (Đang hoạt động / Ngừng hoạt động)"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dòng mẫu 1
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Kilogram");
            row1.createCell(1).setCellValue("kg");
            row1.createCell(2).setCellValue("Khối lượng");
            row1.createCell(3).setCellValue(3);
            row1.createCell(4).setCellValue("Đang hoạt động");

            // Dòng mẫu 2
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Lít");
            row2.createCell(1).setCellValue("l");
            row2.createCell(2).setCellValue("Thể tích");
            row2.createCell(3).setCellValue(2);
            row2.createCell(4).setCellValue("Đang hoạt động");

            // Dòng mẫu 3
            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("Hộp");
            row3.createCell(1).setCellValue("hộp");
            row3.createCell(2).setCellValue("Đếm");
            row3.createCell(3).setCellValue(0);
            row3.createCell(4).setCellValue("Đang hoạt động");

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Exception ignored) {}
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tải file mẫu: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void importFromExcel(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            if (rowCount <= 1) {
                return;
            }

            int headerRowIndex = 0;
            for (int r = 0; r < Math.min(10, rowCount); r++) {
                Row rRow = sheet.getRow(r);
                if (rRow == null) continue;
                boolean hasMatch = false;
                for (int c = 0; c < rRow.getLastCellNum(); c++) {
                    String h = getCellValueAsString(rRow.getCell(c)).toLowerCase().trim();
                    String normalized = java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (normalized.contains("ten don vi") || normalized.contains("ky hieu") || normalized.contains("phan loai") || normalized.contains("unit name")) {
                        hasMatch = true;
                        break;
                    }
                }
                if (hasMatch) {
                    headerRowIndex = r;
                    break;
                }
            }

            Row headerRow = sheet.getRow(headerRowIndex);
            int colName = -1;
            int colSymbol = -1;
            int colType = -1;
            int colPrecision = -1;
            int colStatus = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String h = getCellValueAsString(headerRow.getCell(c)).toLowerCase().trim();
                    String normalized = java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (normalized.contains("ten don vi") || (normalized.contains("don vi") && !normalized.contains("mau")) || normalized.contains("unit name")) {
                        colName = c;
                    } else if (normalized.contains("ky hieu") || normalized.contains("symbol")) {
                        colSymbol = c;
                    } else if (normalized.contains("phan loai") || normalized.contains("loai") || normalized.contains("type")) {
                        colType = c;
                    } else if (normalized.contains("do chinh xac") || normalized.contains("precision") || normalized.contains("scale")) {
                        colPrecision = c;
                    } else if (normalized.contains("trang thai") || normalized.contains("status")) {
                        colStatus = c;
                    }
                }
            }

            if (colName == -1) colName = 0;
            if (colSymbol == -1) colSymbol = 1;
            if (colType == -1) colType = 2;
            if (colPrecision == -1) colPrecision = 3;
            if (colStatus == -1) colStatus = 4;

            Set<String> seenSymbolsInFile = new HashSet<>();

            List<Unit> unitsToSave = new ArrayList<>();

            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name = colName >= 0 ? getCellValueAsString(row.getCell(colName)) : "";
                String symbol = colSymbol >= 0 ? getCellValueAsString(row.getCell(colSymbol)) : "";
                String typeStr = colType >= 0 ? getCellValueAsString(row.getCell(colType)) : "";
                String precisionStr = colPrecision >= 0 ? getCellValueAsString(row.getCell(colPrecision)) : "";
                String statusStr = colStatus >= 0 ? getCellValueAsString(row.getCell(colStatus)) : "";

                if (name.isBlank() && symbol.isBlank()) {
                    continue;
                }

                final int rowNum = r + 1;
                if (name.isBlank()) {
                    throw new BadRequestException("Dòng " + rowNum + ": Tên đơn vị không được để trống.");
                }
                if (symbol.isBlank()) {
                    throw new BadRequestException("Dòng " + rowNum + ": Ký hiệu đơn vị không được để trống.");
                }

                String cleanName = name.trim();
                String cleanSymbol = symbol.trim();
                String symbolKey = cleanSymbol.toLowerCase();

                if (seenSymbolsInFile.contains(symbolKey)) {
                    throw new BadRequestException("Dòng " + rowNum + ": Ký hiệu đơn vị '" + cleanSymbol + "' bị trùng lặp trong file excel.");
                }
                seenSymbolsInFile.add(symbolKey);

                UnitType type = parseUnitType(typeStr);

                int precision = 0;
                if (!precisionStr.isBlank()) {
                    try {
                        precision = Math.max(0, Math.min(10, (int) Double.parseDouble(precisionStr)));
                    } catch (Exception ignored) {}
                }

                Status status = Status.ACTIVE;
                if (statusStr.equalsIgnoreCase("Ngừng hoạt động") || statusStr.equalsIgnoreCase("INACTIVE")) {
                    status = Status.INACTIVE;
                }

                Optional<Unit> existingBySymbol = repository.findBySymbolIgnoreCaseAndIsDeletedFalse(cleanSymbol);
                Optional<Unit> existingByName = repository.findByNameIgnoreCaseAndIsDeletedFalse(cleanName);

                Unit unit;
                if (existingBySymbol.isPresent()) {
                    unit = existingBySymbol.get();
                    unit.setIsDeleted(false);
                } else if (existingByName.isPresent()) {
                    unit = existingByName.get();
                    unit.setIsDeleted(false);
                } else {
                    unit = new Unit();
                    unit.setConversionRate(BigDecimal.ONE);
                }

                unit.setName(cleanName);
                unit.setSymbol(cleanSymbol);
                unit.setType(type);
                unit.setPrecisionScale(precision);
                unit.setStatus(status);
                unitsToSave.add(unit);
            }

            if (!unitsToSave.isEmpty()) {
                repository.saveAll(unitsToSave);
            }
        } catch (Exception e) {
            if (e instanceof BadRequestException) {
                throw (BadRequestException) e;
            }
            throw new RuntimeException("Lỗi nhập dữ liệu từ Excel: " + e.getMessage(), e);
        }
    }

    private String formatUnitType(UnitType type) {
        if (type == null) return "Đếm";
        return switch (type) {
            case WEIGHT -> "Khối lượng";
            case VOLUME -> "Thể tích";
            case COUNT -> "Đếm";
        };
    }

    private UnitType parseUnitType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return UnitType.COUNT;
        String lower = typeStr.trim().toLowerCase();
        if (lower.contains("khối lượng") || lower.contains("weight")) {
            return UnitType.WEIGHT;
        }
        if (lower.contains("thể tích") || lower.contains("volume")) {
            return UnitType.VOLUME;
        }
        return UnitType.COUNT;
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
                if (val == (long) val) {
                    return String.format("%d", (long) val);
                } else {
                    return String.format("%s", val);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return "";
                }
            default:
                return "";
        }
    }
}
