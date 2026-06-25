package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;
    private final BusinessSequenceRepository businessSequenceRepository;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMapper mapper,
            BusinessSequenceRepository businessSequenceRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
        this.businessSequenceRepository = businessSequenceRepository;
    }

    @Override
    public PageResponse<ProductSummaryResponse> findAll(
            String name, UUID categoryId, Status status, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(productRepository.findAll(spec, pageable).map(mapper::toSummaryResponse));
    }

    @Override
    public ProductResponse findById(UUID id) {
        return mapper.toResponse(getProduct(id));
    }

    @Transactional
    public String generateProductCode() {
        org.hvtra.catalog.entity.BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")
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
        return mapper.toResponse(productRepository.save(product));
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
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
              
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Danh sách sản phẩm");
            
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            
            String[] headers = {"Mã sản phẩm", "Tên sản phẩm", "Mã danh mục", "Trạng thái", "Mô tả"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowIdx = 1;
            for (var p : products) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getProductCode());
                row.createCell(1).setCellValue(p.getName());
                row.createCell(2).setCellValue(p.getCategory() != null ? p.getCategory().getCategoryCode() : "");
                row.createCell(3).setCellValue(p.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
                row.createCell(4).setCellValue(p.getDescription() != null ? p.getDescription() : "");
            }
            
            sheet.createFreezePane(0, 1);
            
            if (rowIdx > 1) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIdx - 1, 0, headers.length - 1));
            }
            
            for (int i = 0; i < headers.length; i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Exception ignored) {}
            }
            
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất file Excel sản phẩm: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void importFromExcel(org.springframework.web.multipart.MultipartFile file) {
        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            
            for (int r = 1; r < rowCount; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row == null) continue;
                
                String categoryCode = getCellValueAsString(row.getCell(0));
                String name = getCellValueAsString(row.getCell(1));
                String statusStr = getCellValueAsString(row.getCell(2));
                String description = getCellValueAsString(row.getCell(3));
                
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
                
                Category category = categoryRepository.findByCategoryCode(categoryCode)
                        .orElseThrow(() -> new BadRequestException("Row " + rowNum + ": Category Code '" + categoryCode + "' does not exist."));

                
                Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr) 
                        ? Status.INACTIVE : Status.ACTIVE;
                        
                Product product = new Product();
                product.setProductCode(generateProductCode());
                product.setName(name);
                product.setCategory(category);
                product.setDescription(description);
                product.setStatus(status);
                productRepository.save(product);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi nhập dữ liệu từ Excel: " + e.getMessage(), e);
        }
    }

    private String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
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
    public java.util.List<ProductResponse> findAllByIds(java.util.List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return productRepository.findAllById(ids).stream()
                .map(mapper::toResponse)
                .toList();
    }
}

