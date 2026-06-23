package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.InventoryItemType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductVariantRepository repository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final InventoryClient inventoryClient;
    private final UnitRepository unitRepository;

    public ProductVariantServiceImpl(
            ProductVariantRepository repository,
            ProductRepository productRepository,
            ProductMapper mapper,
            InventoryClient inventoryClient,
            UnitRepository unitRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.inventoryClient = inventoryClient;
        this.unitRepository = unitRepository;
    }

    @Override
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String sku,
            String variantName,
            String barcode,
            UUID productId,
            Status status,
            Pageable pageable) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (sku != null && !sku.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sku")), "%" + sku.toLowerCase() + "%"));
            }
            if (variantName != null && !variantName.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("variantName")), "%" + variantName.toLowerCase() + "%"));
            }
            if (barcode != null && !barcode.isBlank()) {
                query.distinct(true);
                predicates.add(cb.like(
                        cb.lower(root.join("barcodes", JoinType.LEFT).get("barcodeValue")),
                        "%" + barcode.toLowerCase() + "%"));
            }
            if (productId != null) predicates.add(cb.equal(root.get("product").get("id"), productId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toVariantSummary));
    }

    @Override
    public ProductResponse.VariantResponse findById(UUID id) {
        return mapper.toVariantResponse(get(id));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request) {
        validateSku(request.sku(), null);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        apply(variant, request);
        ProductVariant savedVariant = repository.saveAndFlush(variant);
        inventoryClient.createProductVariantItem(
                InventoryClient.CreateInventoryItemRequest.builder()
                        .status(savedVariant.getStatus().toString())
                        .baseUnitId(savedVariant.getBaseUnitId())
                        .referenceId(savedVariant.getId())
                        .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                        .build());
        return mapper.toVariantResponse(savedVariant);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request) {
        ProductVariant variant = get(id);
        validateSku(request.sku(), id);
        apply(variant, request);
        return mapper.toVariantResponse(repository.save(variant));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse updateStatus(UUID id, Status status) {
        ProductVariant variant = get(id);
        variant.setStatus(status);
        return mapper.toVariantResponse(repository.save(variant));
    }

    private ProductVariant get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant", id));
    }

    private void apply(ProductVariant variant, ProductVariantRequest request) {
        if (!unitRepository.existsById(request.baseUnitId())) {
            throw new ResourceNotFoundException("Unit", request.baseUnitId());
        }
        variant.setSku(request.sku().trim());
        variant.setVariantName(request.variantName().trim());
        variant.setSellingPrice(request.sellingPrice());
        variant.setCostPrice(request.costPrice());
        variant.setBaseUnitId(request.baseUnitId());
        variant.setInventoryPolicy(request.inventoryPolicy());
        variant.setStatus(request.status());
    }

    private void validateSku(String sku, UUID id) {
        String normalized = sku.trim();
        boolean exists = id == null
                ? repository.existsBySku(normalized)
                : repository.existsBySkuAndIdNot(normalized, id);
        if (exists) throw new BadRequestException("SKU already exists: " + normalized);
    }

    @Override
    public byte[] exportToExcel(UUID productId) {
        java.util.List<ProductVariant> variants;
        if (productId != null) {
            variants = repository.findByProductId(productId);
        } else {
            variants = repository.findAll();
        }
        var units = unitRepository.findAll();
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
             
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Danh sách biến thể");
            
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            
            org.apache.poi.ss.usermodel.CellStyle priceStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
            priceStyle.setDataFormat(format.getFormat("#,##0"));
            
            String[] headers = {"Tên biến thể", "SKU", "Giá bán", "Giá vốn", "Đơn vị", "Chính sách tồn kho", "Trạng thái"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowIdx = 1;
            for (var v : variants) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                
                row.createCell(0).setCellValue(v.getVariantName());
                row.createCell(1).setCellValue(v.getSku());
                
                org.apache.poi.ss.usermodel.Cell cellSelling = row.createCell(2);
                if (v.getSellingPrice() != null) {
                    cellSelling.setCellValue(v.getSellingPrice().doubleValue());
                } else {
                    cellSelling.setCellValue(0.0);
                }
                cellSelling.setCellStyle(priceStyle);
                
                org.apache.poi.ss.usermodel.Cell cellCost = row.createCell(3);
                if (v.getCostPrice() != null) {
                    cellCost.setCellValue(v.getCostPrice().doubleValue());
                } else {
                    cellCost.setCellValue(0.0);
                }
                cellCost.setCellStyle(priceStyle);
                
                String unitStr = "";
                if (v.getBaseUnitId() != null) {
                    var matched = units.stream().filter(u -> u.getId().equals(v.getBaseUnitId())).findFirst();
                    if (matched.isPresent()) {
                        unitStr = matched.get().getName();
                    } else {
                        unitStr = v.getBaseUnitId().toString();
                    }
                }
                row.createCell(4).setCellValue(unitStr);
                
                String policyStr = "Không theo dõi";
                if (v.getInventoryPolicy() == org.hvtra.catalog.enums.InventoryPolicy.TRACK_STOCK) {
                    policyStr = "Theo dõi tồn kho";
                }
                row.createCell(5).setCellValue(policyStr);
                
                row.createCell(6).setCellValue(v.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
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
            throw new RuntimeException("Lỗi xuất file Excel biến thể: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void importFromCsv(org.springframework.web.multipart.MultipartFile file, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
                
        var units = unitRepository.findAll();
        
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
                    String variantName = columns.get(1);
                    String sku = columns.get(2);
                    
                    if (variantName.isBlank() || sku.isBlank()) {
                        continue;
                    }
                    
                    // Skip if SKU already exists in database
                    if (repository.existsBySku(sku.trim())) {
                        continue;
                    }
                    
                    java.math.BigDecimal sellingPrice = java.math.BigDecimal.ZERO;
                    if (columns.size() > 3 && !columns.get(3).isBlank()) {
                        try {
                            sellingPrice = new java.math.BigDecimal(columns.get(3));
                        } catch (Exception ignored) {}
                    }
                    
                    java.math.BigDecimal costPrice = java.math.BigDecimal.ZERO;
                    if (columns.size() > 4 && !columns.get(4).isBlank()) {
                        try {
                            costPrice = new java.math.BigDecimal(columns.get(4));
                        } catch (Exception ignored) {}
                    }
                    
                    String unitName = columns.size() > 5 ? columns.get(5) : "";
                    UUID baseUnitId = null;
                    for (var u : units) {
                        if (u.getName().equalsIgnoreCase(unitName) || u.getSymbol().equalsIgnoreCase(unitName)) {
                            baseUnitId = u.getId();
                            break;
                        }
                    }
                    if (baseUnitId == null && !units.isEmpty()) {
                        baseUnitId = units.get(0).getId();
                    }
                    if (baseUnitId == null) {
                        continue;
                    }
                    
                    org.hvtra.catalog.enums.InventoryPolicy policy = org.hvtra.catalog.enums.InventoryPolicy.TRACK_STOCK;
                    if (columns.size() > 6 && !columns.get(6).isBlank()) {
                        String policyStr = columns.get(6).trim().toUpperCase();
                        if ("TRACK".equals(policyStr) || "TRACK_STOCK".equals(policyStr)) {
                            policy = org.hvtra.catalog.enums.InventoryPolicy.TRACK_STOCK;
                        } else if ("DO_NOT_TRACK".equals(policyStr)) {
                            policy = org.hvtra.catalog.enums.InventoryPolicy.DO_NOT_TRACK;
                        }
                    }
                    
                    String statusStr = columns.size() > 7 ? columns.get(7) : "ACTIVE";
                    Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr) 
                            ? Status.INACTIVE : Status.ACTIVE;
                            
                    ProductVariant variant = new ProductVariant();
                    variant.setProduct(product);
                    variant.setSku(sku.trim());
                    variant.setVariantName(variantName.trim());
                    variant.setSellingPrice(sellingPrice);
                    variant.setCostPrice(costPrice);
                    variant.setBaseUnitId(baseUnitId);
                    variant.setInventoryPolicy(policy);
                    variant.setStatus(status);
                    
                    ProductVariant savedVariant = repository.saveAndFlush(variant);
                    
                    inventoryClient.createProductVariantItem(
                            InventoryClient.CreateInventoryItemRequest.builder()
                                    .status(savedVariant.getStatus().toString())
                                    .baseUnitId(savedVariant.getBaseUnitId())
                                    .referenceId(savedVariant.getId())
                                    .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                                    .build());
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

