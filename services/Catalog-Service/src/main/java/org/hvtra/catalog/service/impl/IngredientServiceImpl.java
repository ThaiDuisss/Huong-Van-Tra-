package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.client.InventoryClient.CreateInventoryItemRequest;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public String generateIngredientCode(String ingredientName) {
        BusinessSequence seq = businessSequenceRepository
                .findBySequenceKeyWithLock("INGREDIENT")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("INGREDIENT");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        long nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        businessSequenceRepository.saveAndFlush(seq);

        String prefix = getInitials(ingredientName);
        return String.format("%s-%02d", prefix, nextVal);
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "NL";
        }

        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("Đ", "D")
                .replace("đ", "d");

        StringBuilder initials = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (!word.isEmpty()) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
        }

        return initials.toString();
    }

    @Override
    public PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable, UUID supplierId) {
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
        String code = generateIngredientCode(request.name());
        validateCode(code, null);
        
        Ingredient ingredient = new Ingredient();
        mapper.update(ingredient, request);
        ingredient.setIngredientCode(code);
        if (request.unitId() != null) {
            Unit unit = unitRepository.findById(request.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", request.unitId()));
            ingredient.setUnit(unit);
        } else {
            ingredient.setUnit(null);
        }
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
    public java.util.List<IngredientResponse> findByIds(java.util.List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return repository.findAllById(ids).stream()
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
             
            Sheet sheet = workbook.createSheet("Mẫu nhập nguyên liệu");
            
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            
            String[] headers = {"Mã nguyên liệu", "Tên nguyên liệu", "Trạng thái (Đang hoạt động / Ngừng hoạt động)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("NL001");
            row.createCell(1).setCellValue("Lá trà tươi ô long");
            row.createCell(2).setCellValue("Đang hoạt động");
            
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
                    String normalized = Normalizer.normalize(h, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (normalized.contains("ma nguyen lieu") || normalized.contains("ma nl") || normalized.contains("ten nguyen lieu") || normalized.contains("ten nl")) {
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
            int colCode = -1;
            int colName = -1;
            int colStatus = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String h = getCellValueAsString(headerRow.getCell(c)).toLowerCase().trim();
                    String normalized = Normalizer.normalize(h, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (normalized.contains("ma nguyen lieu") || normalized.contains("ma nl") || normalized.contains("ingredient code")) {
                        colCode = c;
                    } else if (normalized.contains("ten nguyen lieu") || normalized.contains("ten nl") || normalized.contains("ingredient name") || normalized.contains("nguyen lieu")) {
                        colName = c;
                    } else if (normalized.contains("trang thai") || normalized.contains("status")) {
                        colStatus = c;
                    }
                }
            }

            if (colCode == -1) colCode = 0;
            if (colName == -1) colName = 1;
            if (colStatus == -1) colStatus = 2;

            Map<String, Ingredient> existingByCode = repository.findAll().stream()
                    .filter(i -> i.getIngredientCode() != null)
                    .collect(Collectors.toMap(i -> i.getIngredientCode().toLowerCase().trim(), i -> i, (a, b) -> a));

            Set<String> seenCodesInFile = new HashSet<>();
            List<Ingredient> ingredientsToSave = new ArrayList<>();
            List<Ingredient> newIngredients = new ArrayList<>();

            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String ingredientCode = colCode >= 0 ? getCellValueAsString(row.getCell(colCode)) : "";
                String name = colName >= 0 ? getCellValueAsString(row.getCell(colName)) : "";
                String statusStr = colStatus >= 0 ? getCellValueAsString(row.getCell(colStatus)) : "";

                if (name.isBlank() && ingredientCode.isBlank()) {
                    continue;
                }

                final int rowNum = r + 1;
                if (ingredientCode.isBlank()) {
                    throw new BadRequestException("Dòng " + rowNum + ": Mã nguyên liệu không được để trống.");
                }
                if (name.isBlank()) {
                    throw new BadRequestException("Dòng " + rowNum + ": Tên nguyên liệu không được để trống.");
                }

                String cleanCode = ingredientCode.trim();
                String cleanName = name.trim();
                String codeKey = cleanCode.toLowerCase();

                if (seenCodesInFile.contains(codeKey)) {
                    throw new BadRequestException("Dòng " + rowNum + ": Mã nguyên liệu '" + cleanCode + "' bị trùng lặp trong file excel.");
                }
                seenCodesInFile.add(codeKey);

                Status status = Status.ACTIVE;
                if (statusStr.equalsIgnoreCase("Ngừng hoạt động") || statusStr.equalsIgnoreCase("INACTIVE")) {
                    status = Status.INACTIVE;
                }

                Ingredient ingredient = existingByCode.get(codeKey);
                boolean isNew = false;
                if (ingredient != null) {
                    ingredient.setIsDeleted(false);
                } else {
                    ingredient = new Ingredient();
                    isNew = true;
                }

                ingredient.setIngredientCode(cleanCode);
                ingredient.setName(cleanName);
                ingredient.setStatus(status);

                ingredientsToSave.add(ingredient);
                if (isNew) {
                    newIngredients.add(ingredient);
                }
            }

            if (!ingredientsToSave.isEmpty()) {
                repository.saveAll(ingredientsToSave);

                for (Ingredient saved : newIngredients) {
                    inventoryClient.createProductVariantItem(
                            CreateInventoryItemRequest.builder()
                                    .status(saved.getStatus().toString())
                                    .unitId(null)
                                    .referenceId(saved.getId())
                                    .referenceCode(saved.getIngredientCode())
                                    .name(saved.getName())
                                    .itemType("INGREDIENT")
                                    .reorderPoint(0)
                                    .build());
                }
            }
        } catch (Exception e) {
            if (e instanceof BadRequestException) {
                throw (BadRequestException) e;
            }
            throw new RuntimeException("Lỗi nhập dữ liệu từ Excel: " + e.getMessage(), e);
        }
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
