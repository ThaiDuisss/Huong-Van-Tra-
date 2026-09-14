package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.client.FileClient;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.BusinessSequence;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.BusinessSequenceRepository;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.service.ProductService;
import org.hvtra.catalog.util.ExcelExportHelper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessSequenceRepository businessSequenceRepository;
    private final ProductMapper mapper;
    private final FileClient fileClient;

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              BusinessSequenceRepository businessSequenceRepository,
                              ProductMapper mapper,
                              FileClient fileClient) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.businessSequenceRepository = businessSequenceRepository;
        this.mapper = mapper;
        this.fileClient = fileClient;
    }

    @Override
    public PageResponse<ProductResponse> findAll(
            String name, UUID categoryId, Status status, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        List<Product> products = productPage.getContent();
        List<ProductResponse> productResponses = products.stream().map(mapper::toResponse).toList();
        return new PageResponse<>(
                productResponses,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isFirst(),
                productPage.isLast()
        );
    }

    @Override
    public PageResponse<LookupResponse> lookup(String search, Pageable pageable) {
        Page<Product> page = productRepository.findByNameContainingIgnoreCaseAndIsDeletedFalse(search, pageable);
        return new PageResponse<>(
                page.getContent().stream()
                        .map(p -> new LookupResponse(p.getId().toString(), p.getName()))
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
    public ProductResponse findById(UUID id) {
        return mapper.toResponse(getProduct(id));
    }

    @Transactional
    public String generateProductCode() {
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")
                .orElseThrow(() -> new RuntimeException("Product sequence not initialized"));
        long nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        businessSequenceRepository.saveAndFlush(seq);
        return String.format("PRD%05d", nextVal);
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        String code = request.productCode();
        if (code == null || code.isBlank()) {
            code = generateProductCode();
        } else {
            code = code.trim();
            if (code.length() > 50) {
                throw new BadRequestException("Mã sản phẩm tối đa 50 ký tự");
            }
            if (productRepository.existsByProductCode(code)) {
                throw new BadRequestException("Mã sản phẩm đã tồn tại: " + code);
            }
        }
        product.setProductCode(code);
        updateProductFields(product, request);
        Product savedProduct = productRepository.save(product);
        fileClient.attachFile(FileClient.FileAttach.builder()
                .refId(savedProduct.getId())
                .fieldId(request.fieldId())
                .build());
        return mapper.toResponse(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getProduct(id);
        
        String code = request.productCode();
        if (code != null && !code.isBlank()) {
            code = code.trim();
            if (code.length() > 50) {
                throw new BadRequestException("Mã sản phẩm tối đa 50 ký tự");
            }
            if (!code.equalsIgnoreCase(product.getProductCode())) {
                if (productRepository.existsByProductCode(code)) {
                    throw new BadRequestException("Mã sản phẩm đã tồn tại: " + code);
                }
                product.setProductCode(code);
            }
        }
        
        updateProductFields(product, request);
        return mapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(UUID id, Status status) {
        Product product = getProduct(id);
        product.setStatus(status);
        return mapper.toResponse(productRepository.save(product));
    }

    private void updateProductFields(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setStatus(request.status());
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Override
    public byte[] exportToExcel() {
        var products = productRepository.findAll();
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
              
            Sheet sheet = workbook.createSheet("Danh sách sản phẩm");
            ExcelExportHelper.ExportStyles styles = ExcelExportHelper.createExportStyles(workbook);
            
            String[] headers = {"STT", "Mã sản phẩm", "Tên sản phẩm", "Mã danh mục", "Trạng thái", "Mô tả"};
            ExcelExportHelper.createReportHeader(sheet, styles, "BÁO CÁO DANH SÁCH SẢN PHẨM", headers);
            
            int rowIdx = 5;
            int stt = 1;
            for (var p : products) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(22);
                
                Cell cellStt = row.createCell(1);
                cellStt.setCellValue(stt++);
                cellStt.setCellStyle(styles.centerStyle());
                
                Cell cellCode = row.createCell(2);
                cellCode.setCellValue(p.getProductCode() != null ? p.getProductCode() : "");
                cellCode.setCellStyle(styles.textStyle());
                
                Cell cellName = row.createCell(3);
                cellName.setCellValue(p.getName() != null ? p.getName() : "");
                cellName.setCellStyle(styles.textStyle());
                
                Cell cellCat = row.createCell(4);
                cellCat.setCellValue(p.getCategory() != null ? p.getCategory().getCategoryCode() : "");
                cellCat.setCellStyle(styles.centerStyle());
                
                Cell cellStatus = row.createCell(5);
                cellStatus.setCellValue(p.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
                cellStatus.setCellStyle(styles.centerStyle());
                
                Cell cellDesc = row.createCell(6);
                cellDesc.setCellValue(p.getDescription() != null ? p.getDescription() : "");
                cellDesc.setCellStyle(styles.textStyle());
            }
            
            ExcelExportHelper.finalizeSheet(sheet, headers.length, rowIdx);
            
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất file Excel sản phẩm: " + e.getMessage(), e);
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
                    if (normalized.contains("ma danh muc") || normalized.contains("ten san pham") || normalized.contains("category code") || normalized.contains("product name")) {
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
            int colCategoryCode = -1;
            int colName = -1;
            int colStatus = -1;
            int colDescription = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String h = getCellValueAsString(headerRow.getCell(c)).toLowerCase().trim();
                    String normalized = java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (normalized.contains("ma danh muc") || normalized.contains("category code") || normalized.contains("danh muc")) {
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

            List<ProductImportRow> validRows = new ArrayList<>();

            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                
                String categoryCode = colCategoryCode >= 0 ? getCellValueAsString(row.getCell(colCategoryCode)) : "";
                String name = colName >= 0 ? getCellValueAsString(row.getCell(colName)) : "";
                String statusStr = colStatus >= 0 ? getCellValueAsString(row.getCell(colStatus)) : "";
                String description = colDescription >= 0 ? getCellValueAsString(row.getCell(colDescription)) : "";
                
                if (name.isBlank() && categoryCode.isBlank()) {
                    continue;
                }
                
                final int rowNum = r + 1;
                if (categoryCode.isBlank()) {
                    throw new BadRequestException("Row " + rowNum + ": Category Code is blank.");
                }
                if (name.isBlank()) {
                    throw new BadRequestException("Row " + rowNum + ": Product Name is blank.");
                }
                
                Category category = categoriesByCode.get(categoryCode.trim().toUpperCase());
                if (category == null) {
                    throw new BadRequestException("Row " + rowNum + ": Category Code '" + categoryCode + "' does not exist.");
                }
                
                Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr) 
                        ? Status.INACTIVE : Status.ACTIVE;
                        
                validRows.add(new ProductImportRow(name.trim(), category, description, status));
            }

            if (validRows.isEmpty()) {
                return;
            }

            BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")
                    .orElseThrow(() -> new RuntimeException("Product sequence not initialized"));
            long currentVal = seq.getCurrentValue();
            seq.setCurrentValue(currentVal + validRows.size());
            businessSequenceRepository.saveAndFlush(seq);

            List<Product> productsToSave = new ArrayList<>();
            for (int i = 0; i < validRows.size(); i++) {
                ProductImportRow rowData = validRows.get(i);
                Product product = new Product();
                product.setProductCode(String.format("PRD%05d", currentVal + 1 + i));
                product.setName(rowData.name());
                product.setCategory(rowData.category());
                product.setDescription(rowData.description());
                product.setStatus(rowData.status());
                productsToSave.add(product);
            }

            productRepository.saveAll(productsToSave);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi nhập dữ liệu từ Excel: " + e.getMessage(), e);
        }
    }

    private record ProductImportRow(String name, Category category, String description, Status status) {}

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
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

    @Override
    public byte[] getImportTemplate() {
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
             
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Mẫu nhập sản phẩm");
            
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            
            String[] headers = {"Mã danh mục", "Tên sản phẩm", "Trạng thái", "Mô tả"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Add template rows
            org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("CAT00001");
            row1.createCell(1).setCellValue("Trà Thiết Quan Âm");
            row1.createCell(2).setCellValue("Đang hoạt động");
            row1.createCell(3).setCellValue("Trà thiết quan âm thượng hạng thơm ngon");
 
            org.apache.poi.ss.usermodel.Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("CAT00001");
            row2.createCell(1).setCellValue("Trà Sen Tây Hồ");
            row2.createCell(2).setCellValue("Đang hoạt động");
            row2.createCell(3).setCellValue("Trà sen Tây Hồ ướp hương sen tự nhiên");
            
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 2, 0, headers.length - 1));
            
            for (int i = 0; i < headers.length; i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Exception ignored) {}
            }
            
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo file mẫu Excel sản phẩm: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ProductResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return productRepository.findAllById(ids).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (product.getIsDeleted()) {
            throw new ResourceNotFoundException("Product", id);
        }
        product.setIsDeleted(true);
        productRepository.save(product);
    }
}
