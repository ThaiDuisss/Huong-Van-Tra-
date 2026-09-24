package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.client.InventoryClient.CreateInventoryItemRequest;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.entity.BusinessSequence;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.IngredientMapper;
import org.hvtra.catalog.repository.BusinessSequenceRepository;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.IngredientService;
import org.hvtra.catalog.util.ExcelExportHelper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import org.hvtra.commonservice.common_core.util.CodeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IngredientServiceImpl implements IngredientService {
    private final IngredientRepository repository;
    private final IngredientMapper mapper;
    private final BusinessSequenceRepository businessSequenceRepository;
    private final UnitRepository unitRepository;
    private final InventoryClient inventoryClient;

    public IngredientServiceImpl(
            IngredientRepository repository,
            IngredientMapper mapper,
            BusinessSequenceRepository businessSequenceRepository,
            UnitRepository unitRepository,
            InventoryClient inventoryClient) {
        this.repository = repository;
        this.mapper = mapper;
        this.businessSequenceRepository = businessSequenceRepository;
        this.unitRepository = unitRepository;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public String generateIngredientCode(String ingredientName, Integer quantity, String symbolUnit) {
        String prefix = CodeUtils.getInitials(ingredientName, "NL", quantity, symbolUnit);
        BusinessSequence seq = businessSequenceRepository
                .findBySequenceKeyWithLock("INGREDIENT")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("INGREDIENT");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        String code;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            code = String.format("%s-%02d", prefix, nextVal);
        } while (repository.existsByIngredientCode(code));

        businessSequenceRepository.saveAndFlush(seq);
        return code;
    }

    @Transactional
    public String generateIngredientCode(String ingredientName) {
        return generateIngredientCode(ingredientName, null, null);
    }

    @Override
    public PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable, UUID supplierId) {
        Specification<Ingredient> spec = (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            boolean isCountQuery = Long.class.equals(resultType) || long.class.equals(resultType);
            if (!isCountQuery) {
                root.fetch("unit", JoinType.LEFT);
            }

            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("ingredientCode")), pattern)));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));

            if (supplierId != null) {
                var supplierJoin = root.join("suppliers", JoinType.INNER);
                predicates.add(cb.equal(supplierJoin.get("id"), supplierId));
                if (query != null) {
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public IngredientResponse findById(UUID id) {
        Ingredient ingredient = get(id);
        if (ingredient.getIsDeleted()) {
            throw new ResourceNotFoundException("Ingredient", id);
        }
        return mapper.toResponse(ingredient);
    }

    @Override
    @Transactional
    public IngredientResponse create(IngredientRequest request) {
        return createWithCode(request, null);
    }

    @Override
    @Transactional
    public IngredientResponse createWithCode(IngredientRequest request, String explicitCode) {
        Unit unit = request.unitId() != null
                ? unitRepository.findById(request.unitId())
                        .orElseThrow(() -> new ResourceNotFoundException("Unit", request.unitId()))
                : null;
        String symbol = unit != null ? unit.getSymbol() : null;

        String code = explicitCode != null && !explicitCode.isBlank()
                ? explicitCode.trim()
                : generateIngredientCode(request.name(), null, symbol);
        validateCode(code, null);

        Ingredient ingredient = new Ingredient();
        mapper.update(ingredient, request);
        ingredient.setIngredientCode(code);
        ingredient.setUnit(unit);
        Ingredient savedIngredient = repository.saveAndFlush(ingredient);

        inventoryClient.createProductVariantItem(
                CreateInventoryItemRequest.builder()
                        .status(savedIngredient.getStatus().toString())
                        .unitId(savedIngredient.getUnit() != null ? savedIngredient.getUnit().getId() : null)
                        .referenceId(savedIngredient.getId())
                        .referenceCode(savedIngredient.getIngredientCode())
                        .name(savedIngredient.getName())
                        .itemType("INGREDIENT")
                        .reorderPoint(savedIngredient.getReorderPoint() != null ? savedIngredient.getReorderPoint() : 0)
                        .build());

        return mapper.toResponse(savedIngredient);
    }

    @Override
    @Transactional
    public IngredientResponse update(UUID id, IngredientRequest request) {
        Ingredient ingredient = get(id);
        if (ingredient.getIsDeleted()) {
            throw new ResourceNotFoundException("Ingredient", id);
        }
        mapper.update(ingredient, request);
        if (request.unitId() != null) {
            Unit unit = unitRepository.findById(request.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", request.unitId()));
            ingredient.setUnit(unit);
        } else {
            ingredient.setUnit(null);
        }
        Ingredient saved = repository.save(ingredient);

        syncInventoryItem(saved);

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public IngredientResponse updateStatus(UUID id, Status status) {
        Ingredient ingredient = get(id);
        if (ingredient.getIsDeleted()) {
            throw new ResourceNotFoundException("Ingredient", id);
        }
        ingredient.setStatus(status);
        Ingredient saved = repository.save(ingredient);

        syncInventoryItem(saved);

        return mapper.toResponse(saved);
    }

    private void syncInventoryItem(Ingredient ingredient) {
        if (ingredient == null || ingredient.getId() == null) {
            return;
        }
        try {
            inventoryClient.updateInventoryItemByReference(
                    "INGREDIENT",
                    ingredient.getId(),
                    CreateInventoryItemRequest.builder()
                            .referenceId(ingredient.getId())
                            .referenceCode(ingredient.getIngredientCode())
                            .name(ingredient.getName())
                            .itemType("INGREDIENT")
                            .unitId(ingredient.getUnit() != null ? ingredient.getUnit().getId() : null)
                            .status(ingredient.getStatus() != null ? ingredient.getStatus().toString() : "ACTIVE")
                            .reorderPoint(ingredient.getReorderPoint() != null ? ingredient.getReorderPoint() : 0)
                            .build()
            );
        } catch (Exception e) {
            // Log/gracefully continue
        }
    }

    @Override
    public PageResponse<LookupResponse> lookup(String search, Status status, List<UUID> ids, Pageable pageable) {
        if (ids != null && !ids.isEmpty()) {
            List<LookupResponse> content = repository.findLookupByIds(ids).stream()
                    .map(v -> new LookupResponse(v.getId().toString(), v.getName()))
                    .toList();
            return new PageResponse<>(content, 0, content.size(), content.size(), 1, true, true);
        }
        String keyword = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(repository.findLookup(keyword, status, pageable)
                .map(v -> new LookupResponse(v.getId().toString(), v.getName())));
    }

    @Override
    public java.util.List<IngredientResponse> findByIds(java.util.List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return repository.findAllByIdWithUnit(ids).stream()
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .map(mapper::toResponse)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public java.util.List<UUID> searchIngredientIds(String keyword, String code, String name, UUID unitId) {
        Specification<Ingredient> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("ingredientCode")), pattern)
                ));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("ingredientCode")), "%" + code.toLowerCase() + "%"));
            }
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (unitId != null) {
                predicates.add(cb.equal(root.get("unit").get("id"), unitId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec).stream()
                .map(Ingredient::getId)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Ingredient ingredient = get(id);
        if (ingredient.getIsDeleted()) {
            throw new ResourceNotFoundException("Ingredient", id);
        }
        ingredient.setIsDeleted(true);
        repository.save(ingredient);
    }

    @Override
    public byte[] exportToExcel(String search, Status status) {
        Specification<Ingredient> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("ingredientCode")), pattern)));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var ingredients = repository.findAll(spec);
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
             
            Sheet sheet = workbook.createSheet("Danh sách Nguyên liệu");
            ExcelExportHelper.ExportStyles styles = ExcelExportHelper.createExportStyles(workbook);
            
            String[] headers = {"STT", "Mã nguyên liệu", "Tên nguyên liệu", "Trạng thái"};
            ExcelExportHelper.createReportHeader(sheet, styles, "BÁO CÁO DANH SÁCH NGUYÊN LIỆU", headers);
            
            int rowIdx = 5;
            int stt = 1;
            for (var ing : ingredients) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(22);
                
                Cell cellStt = row.createCell(1);
                cellStt.setCellValue(stt++);
                cellStt.setCellStyle(styles.centerStyle());
                
                Cell cellCode = row.createCell(2);
                cellCode.setCellValue(ing.getIngredientCode() != null ? ing.getIngredientCode() : "");
                cellCode.setCellStyle(styles.textStyle());
                
                Cell cellName = row.createCell(3);
                cellName.setCellValue(ing.getName() != null ? ing.getName() : "");
                cellName.setCellStyle(styles.textStyle());
                
                Cell cellStatus = row.createCell(4);
                cellStatus.setCellValue(ing.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
                cellStatus.setCellStyle(styles.centerStyle());
            }
            
            ExcelExportHelper.finalizeSheet(sheet, headers.length, rowIdx);
            
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất file Excel nguyên liệu: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] getImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildImportInstructionSheet(workbook);
            buildImportIngredientSheet(workbook);
            buildImportReferenceSheet(workbook);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo file mẫu Excel Nguyên liệu: " + e.getMessage(), e);
        }
    }

    private void buildImportInstructionSheet(Workbook workbook) {
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

        String[] titleLines = {"HƯỚNG DẪN NHẬP NGUYÊN LIỆU TỪ EXCEL"};
        String[][] sections = {
                {
                        "1. Cấu trúc file",
                        "File gồm 3 sheet:",
                        "- \"Hướng dẫn\": sheet này.",
                        "- \"Nguyên liệu\": nhập dữ liệu nguyên liệu cần tạo, mỗi dòng là 1 nguyên liệu.",
                        "- \"Tham chiếu\": tra cứu đơn vị để điền vào sheet \"Nguyên liệu\"."
                },
                {
                        "2. Cách điền sheet \"Nguyên liệu\"",
                        "- Tên nguyên liệu (*): bắt buộc. Mã nguyên liệu sẽ được hệ thống tự sinh, không cần nhập.",
                        "- Đơn vị: lấy từ cột \"Đơn vị\" ở sheet \"Tham chiếu\", để trống nếu chưa xác định.",
                        "- Ngưỡng tối thiểu: số nguyên, để trống sẽ mặc định là 0.",
                        "- Trạng thái: \"Đang hoạt động\" hoặc \"Ngừng hoạt động\" (mặc định Đang hoạt động)."
                },
                {
                        "3. Sau khi tải file lên hệ thống",
                        "- Dữ liệu được phân tích thành bản nháp để bạn kiểm tra/sửa trực tiếp trên hệ thống trước khi gửi duyệt.",
                        "- Dữ liệu chỉ được ghi chính thức vào hệ thống sau khi quản lý duyệt."
                },
                {
                        "4. Lưu ý",
                        "- Không đổi tên các sheet, không đổi tên cột header ở dòng đầu tiên của sheet \"Nguyên liệu\".",
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
        headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        if (headerStyle instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle xssfHeaderStyle) {
            xssfHeaderStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    ExcelExportHelper.BRAND_GREEN_RGB, new org.apache.poi.xssf.usermodel.DefaultIndexedColorMap()));
        }
        headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        return headerStyle;
    }

    private void buildImportIngredientSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Nguyên liệu");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Tên nguyên liệu", "Đơn vị", "Ngưỡng tối thiểu", "Trạng thái"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("Lá trà tươi ô long");
        row1.createCell(1).setCellValue("Kg");
        row1.createCell(2).setCellValue(10.0);
        row1.createCell(3).setCellValue("Đang hoạt động");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("Hoa lài sấy khô");
        row2.createCell(1).setCellValue("Kg");
        row2.createCell(2).setCellValue(5.0);
        row2.createCell(3).setCellValue("Đang hoạt động");

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 2, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildImportReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Tham chiếu");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Ký hiệu đơn vị", "Tên đơn vị"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        record CodeName(String code, String name) {}

        List<CodeName> units = unitRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> u.getSymbol() != null && !u.getSymbol().isBlank())
                .sorted((a, b) -> a.getSymbol().compareToIgnoreCase(b.getSymbol()))
                .map(u -> new CodeName(u.getSymbol(), u.getName() != null ? u.getName() : ""))
                .toList();

        for (int i = 0; i < units.size(); i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(units.get(i).code());
            row.createCell(1).setCellValue(units.get(i).name());
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

    private void validateCode(String code, UUID id) {
        boolean exists = id == null
                ? repository.existsByIngredientCode(code)
                : repository.existsByIngredientCodeAndIdNot(code, id);
        if (exists) throw new BadRequestException("Ingredient code already exists: " + code);
    }

    private Ingredient get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ingredient", id));
    }
}
