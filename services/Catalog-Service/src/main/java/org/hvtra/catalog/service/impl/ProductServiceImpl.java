package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
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

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMapper mapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
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

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        updateProductFields(product, request);
        return mapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getProduct(id);
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
            
            String[] headers = {"Tên sản phẩm", "Danh mục", "Trạng thái", "Mô tả"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowIdx = 1;
            for (var p : products) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getName());
                row.createCell(1).setCellValue(p.getCategory() != null ? p.getCategory().getName() : "");
                row.createCell(2).setCellValue(p.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
                row.createCell(3).setCellValue(p.getDescription() != null ? p.getDescription() : "");
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
    public void importFromCsv(org.springframework.web.multipart.MultipartFile file) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.trim().equalsIgnoreCase("sep=,")) {
                    continue;
                }
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                var columns = parseCsvLine(line);
                if (columns.size() >= 3) {
                    String name = "";
                    String categoryName = "";
                    String statusStr = "ACTIVE";
                    String description = "";
                    
                    if (columns.get(0).matches("^[0-9a-fA-F-]{36}$")) {
                        name = columns.get(1);
                        categoryName = columns.get(2);
                        if (columns.size() > 3) statusStr = columns.get(3);
                        if (columns.size() > 4) description = columns.get(4);
                    } else {
                        name = columns.get(0);
                        categoryName = columns.get(1);
                        if (columns.size() > 2) statusStr = columns.get(2);
                        if (columns.size() > 3) description = columns.get(3);
                    }
                    
                    if (name.isBlank() || categoryName.isBlank()) {
                        continue;
                    }
                    
                    var categories = categoryRepository.findAll();
                    Category category = null;
                    for (var c : categories) {
                        if (c.getName().equalsIgnoreCase(categoryName)) {
                            category = c;
                            break;
                        }
                    }
                    if (category == null && !categories.isEmpty()) {
                        category = categories.get(0);
                    }
                    if (category == null) {
                        throw new RuntimeException("No categories found to associate with product " + name);
                    }
                    
                    Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr) 
                            ? Status.INACTIVE : Status.ACTIVE;
                            
                    Product product = new Product();
                    product.setName(name);
                    product.setCategory(category);
                    product.setDescription(description);
                    product.setStatus(status);
                    productRepository.save(product);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc file CSV: " + e.getMessage(), e);
        }
    }
    
    private java.util.List<String> parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder curVal = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = line.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < chars.length && chars[i + 1] == '\"') {
                        curVal.append('\"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    curVal.append(ch);
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    result.add(curVal.toString().trim());
                    curVal.setLength(0);
                } else {
                    curVal.append(ch);
                }
            }
        }
        result.add(curVal.toString().trim());
        return result;
    }
}

