package org.hvtra.catalog.service.impl;

import feign.FeignException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductVariantProductRefResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryItemType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.InventoryServiceUnavailableException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.repository.BusinessSequenceRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.commonservice.exception.FeignClientException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Objects;
import org.hvtra.catalog.dto.response.ProductVariantSearchViewResponse;
import org.hvtra.catalog.enums.InventoryPolicy;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductVariantRepository repository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final InventoryClient inventoryClient;
    private final UnitRepository unitRepository;
    private final BusinessSequenceRepository businessSequenceRepository;

    public ProductVariantServiceImpl(
            ProductVariantRepository repository,
            ProductRepository productRepository,
            ProductMapper mapper,
            InventoryClient inventoryClient,
            UnitRepository unitRepository,
            BusinessSequenceRepository businessSequenceRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.inventoryClient = inventoryClient;
        this.unitRepository = unitRepository;
        this.businessSequenceRepository = businessSequenceRepository;
    }

    @Override
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String keyword,
            String sku,
            String variantName,
            String barcode,
            UUID productId,
            Status status,
            Pageable pageable) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("variantName")), pattern),
                        cb.like(cb.lower(root.get("product").get("name")), pattern)
                ));
            }
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

    @Transactional
    public String generateSku(Product product) {
        String name = product.getName();
        String normalized = java.text.Normalizer.normalize(name != null ? name : "", java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        String clean = normalized.replaceAll("[^a-zA-Z0-9\\s]", "").toUpperCase().trim();
        String[] words = clean.split("\\s+");
        String prefix = Arrays.stream(words)
                .filter(w -> !w.isEmpty())
                .map(w -> w.substring(0, 1))
                .collect(Collectors.joining());

        if (prefix.isEmpty()) {
            prefix = "PR";
        }

        org.hvtra.catalog.entity.BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("SKU")
                .orElseThrow(() -> new RuntimeException("SKU sequence not initialized"));
        long nextVal = seq.getCurrentValue() + 1;
        seq.setCurrentValue(nextVal);
        businessSequenceRepository.saveAndFlush(seq);

        return String.format("%s-%05d", prefix, nextVal);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        apply(variant, request);

        String generatedSku = generateSku(product);
        variant.setSku(generatedSku);

        ProductVariant savedVariant = repository.saveAndFlush(variant);

        Category category = product.getCategory();
        Integer reorderPoint = (category != null && category.getDefaultReorderPoint() != null)
                ? category.getDefaultReorderPoint()
                : 10;

        inventoryClient.createProductVariantItem(
                InventoryClient.CreateInventoryItemRequest.builder()
                        .status(savedVariant.getStatus().toString())
                        .unitId(savedVariant.getUnit() != null ? savedVariant.getUnit().getId() : null)
                        .referenceId(savedVariant.getId())
                        .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                        .reorderPoint(reorderPoint)
                        .build());
        return mapper.toVariantResponse(savedVariant);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request) {
        ProductVariant variant = get(id);
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
        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", request.unitId()));
        variant.setVariantName(request.variantName().trim());
        variant.setSellingPrice(request.sellingPrice());
        variant.setCostPrice(request.costPrice());
        variant.setUnit(unit);
        variant.setInventoryPolicy(request.inventoryPolicy());
        variant.setStatus(request.status());
    }

    @Override
    public byte[] exportToExcel(UUID productId) {
        List<ProductVariant> variants;
        if (productId != null) {
            variants = repository.findByProductId(productId);
        } else {
            variants = repository.findAll();
        }
        var units = unitRepository.findAll();
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
             
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Danh sách Mặt hàng");
            
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            
            org.apache.poi.ss.usermodel.CellStyle priceStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
            priceStyle.setDataFormat(format.getFormat("#,##0"));
            
            String[] headers = {"Mã sản phẩm", "Tên Mặt hàng", "SKU", "Giá bán", "Giá vốn", "Đơn vị bán", "Chính sách tồn kho", "Trạng thái"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowIdx = 1;
            for (var v : variants) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                
                row.createCell(0).setCellValue(v.getProduct() != null ? v.getProduct().getProductCode() : "");
                row.createCell(1).setCellValue(v.getVariantName());
                row.createCell(2).setCellValue(v.getSku());
                
                org.apache.poi.ss.usermodel.Cell cellSelling = row.createCell(3);
                if (v.getSellingPrice() != null) {
                    cellSelling.setCellValue(v.getSellingPrice().doubleValue());
                } else {
                    cellSelling.setCellValue(0.0);
                }
                cellSelling.setCellStyle(priceStyle);
                
                org.apache.poi.ss.usermodel.Cell cellCost = row.createCell(4);
                if (v.getCostPrice() != null) {
                    cellCost.setCellValue(v.getCostPrice().doubleValue());
                } else {
                    cellCost.setCellValue(0.0);
                }
                cellCost.setCellStyle(priceStyle);
                
                String unitStr = "";
                if (v.getUnit() != null) {
                    unitStr = v.getUnit().getName();
                }
                row.createCell(5).setCellValue(unitStr);
                
                String policyStr = "Không theo dõi";
                if (v.getInventoryPolicy() == InventoryPolicy.TRACK_STOCK) {
                    policyStr = "Theo dõi tồn kho";
                }
                row.createCell(6).setCellValue(policyStr);
                
                row.createCell(7).setCellValue(v.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động");
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
            throw new RuntimeException("Lỗi xuất file Excel Mặt hàng: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void importFromExcel(org.springframework.web.multipart.MultipartFile file, UUID productId) {
        var units = unitRepository.findAll();
        
        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            
            for (int r = 1; r < rowCount; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row == null) continue;
                
                String productCode = getCellValueAsString(row.getCell(0));
                String variantName = getCellValueAsString(row.getCell(1));
                String sku = getCellValueAsString(row.getCell(2));
                String sellingPriceStr = getCellValueAsString(row.getCell(3));
                String costPriceStr = getCellValueAsString(row.getCell(4));
                String unitName = getCellValueAsString(row.getCell(5));
                String policyStr = getCellValueAsString(row.getCell(6));
                String statusStr = getCellValueAsString(row.getCell(7));
                
                if (variantName.isBlank() && sku.isBlank() && productCode.isBlank()) {
                    continue;
                }
                
                final int rowNum = r + 1;
                if (productCode.isBlank()) {
                    throw new BadRequestException("Row " + rowNum + ": Product Code is blank.");
                }
                if (variantName.isBlank()) {
                    throw new BadRequestException("Row " + rowNum + ": Variant Name is blank.");
                }

                Product product = productRepository.findByProductCode(productCode)
                        .orElseThrow(() -> new BadRequestException("Row " + rowNum + ": Product Code '" + productCode + "' does not exist."));

                String finalSku;
                if (sku.isBlank()) {
                    finalSku = generateSku(product);
                } else {
                    finalSku = sku.trim();
                    // Skip if SKU already exists in database
                    if (repository.existsBySku(finalSku)) {
                        continue;
                    }
                }

                java.math.BigDecimal sellingPrice = java.math.BigDecimal.ZERO;
                if (!sellingPriceStr.isBlank()) {
                    try {
                        sellingPrice = new java.math.BigDecimal(sellingPriceStr);
                    } catch (Exception ignored) {}
                }

                java.math.BigDecimal costPrice = java.math.BigDecimal.ZERO;
                if (!costPriceStr.isBlank()) {
                    try {
                        costPrice = new java.math.BigDecimal(costPriceStr);
                    } catch (Exception ignored) {}
                }

                Unit resolvedUnit = null;
                for (var u : units) {
                    if (u.getName().equalsIgnoreCase(unitName) || u.getSymbol().equalsIgnoreCase(unitName)) {
                        resolvedUnit = u;
                        break;
                    }
                }
                if (resolvedUnit == null && !units.isEmpty()) {
                    resolvedUnit = units.get(0);
                }
                if (resolvedUnit == null) {
                    continue;
                }

                InventoryPolicy policy = InventoryPolicy.TRACK_STOCK;
                if ("Theo dõi tồn kho".equalsIgnoreCase(policyStr) || "TRACK_STOCK".equalsIgnoreCase(policyStr)) {
                    policy = InventoryPolicy.TRACK_STOCK;
                } else if ("Không theo dõi".equalsIgnoreCase(policyStr) || "DO_NOT_TRACK".equalsIgnoreCase(policyStr)) {
                    policy = InventoryPolicy.DO_NOT_TRACK;
                }

                Status status = "Ngừng hoạt động".equals(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr) 
                        ? Status.INACTIVE : Status.ACTIVE;

                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                variant.setSku(finalSku);
                variant.setVariantName(variantName.trim());
                variant.setSellingPrice(sellingPrice);
                variant.setCostPrice(costPrice);
                variant.setUnit(resolvedUnit);
                variant.setInventoryPolicy(policy);
                variant.setStatus(status);
                
                ProductVariant savedVariant = repository.saveAndFlush(variant);
                
                inventoryClient.createProductVariantItem(
                        InventoryClient.CreateInventoryItemRequest.builder()
                                .status(savedVariant.getStatus().toString())
                                .unitId(savedVariant.getUnit() != null ? savedVariant.getUnit().getId() : null)
                                .referenceId(savedVariant.getId())
                                .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                                .build());
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
             
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Mẫu nhập Mặt hàng");
            
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            
            org.apache.poi.ss.usermodel.CellStyle priceStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();
            priceStyle.setDataFormat(format.getFormat("#,##0"));
            
            String[] headers = {"Mã sản phẩm", "Tên Mặt hàng", "SKU", "Giá bán", "Giá vốn", "Đơn vị", "Định lượng", "Chính sách tồn kho", "Trạng thái"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Add template rows
            org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("PRD00001");
            row1.createCell(1).setCellValue("Thiết Quan Âm 100g");
            row1.createCell(2).setCellValue("TQA-100G");
            
            org.apache.poi.ss.usermodel.Cell cellSelling1 = row1.createCell(3);
            cellSelling1.setCellValue(150000.0);
            cellSelling1.setCellStyle(priceStyle);
            
            org.apache.poi.ss.usermodel.Cell cellCost1 = row1.createCell(4);
            cellCost1.setCellValue(100000.0);
            cellCost1.setCellStyle(priceStyle);
            
            row1.createCell(5).setCellValue("Gói 100g");
            row1.createCell(6).setCellValue("Theo dõi tồn kho");
            row1.createCell(7).setCellValue("Đang hoạt động");
            
            org.apache.poi.ss.usermodel.Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("PRD00001");
            row2.createCell(1).setCellValue("Thiết Quan Âm 250g");
            row2.createCell(2).setCellValue("TQA-250G");
            
            org.apache.poi.ss.usermodel.Cell cellSelling2 = row2.createCell(3);
            cellSelling2.setCellValue(350000.0);
            cellSelling2.setCellStyle(priceStyle);
            
            org.apache.poi.ss.usermodel.Cell cellCost2 = row2.createCell(4);
            cellCost2.setCellValue(230000.0);
            cellCost2.setCellStyle(priceStyle);
            
            row2.createCell(5).setCellValue("Gói 250g");
            row2.createCell(6).setCellValue("Theo dõi tồn kho");
            row2.createCell(7).setCellValue("Đang hoạt động");
            
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
            throw new RuntimeException("Lỗi tạo file mẫu Excel Mặt hàng: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ProductResponse.VariantResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllById(ids).stream()
                .map(mapper::toVariantResponse)
                .toList();
    }

    @Override
    public List<UUID> searchVariantIds(String sku, String productCode, String productName, String categoryCode, UUID categoryId, UUID unitId) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (sku != null && !sku.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sku")), "%" + sku.toLowerCase() + "%"));
            }
            if (productCode != null && !productCode.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("product").get("productCode")), productCode.toLowerCase()));
            }
            if (productName != null && !productName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("product").get("name")), "%" + productName.toLowerCase() + "%"));
            }
            if (categoryCode != null && !categoryCode.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("product").get("category").get("categoryCode")), categoryCode.toLowerCase()));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("product").get("category").get("id"), categoryId));
            }
            if (unitId != null) {
                predicates.add(cb.equal(root.get("unit").get("id"), unitId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec).stream()
                .map(ProductVariant::getId)
                .toList();
    }

    @Override
    public List<ProductVariantSearchViewResponse> searchVariants(String keyword, String sku, String productName) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (keyword != null && !keyword.isBlank()) {
                String normalizedKeyword = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), normalizedKeyword),
                        cb.like(cb.lower(root.get("variantName")), normalizedKeyword),
                        cb.like(cb.lower(root.get("product").get("name")), normalizedKeyword)
                ));
            }
            if (sku != null && !sku.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sku")), "%" + sku.toLowerCase() + "%"));
            }
            if (productName != null && !productName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("product").get("name")), "%" + productName.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<ProductVariant> variants = repository.findAll(spec);
        Map<UUID, InventoryClient.StockVariantBatchResponse> stockMap = loadVariantStockMap(variants.stream()
                .map(ProductVariant::getId)
                .distinct()
                .toList());

        return variants.stream()
                .map(v -> toPosSearchView(v, stockMap.get(v.getId())))
                .toList();
    }

    @Override
    public ProductVariantSearchViewResponse getPosView(UUID variantId) {
        ProductVariantSearchViewResponse response = repository.findPosViewByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant", variantId));
        InventoryClient.StockVariantBatchResponse stock = loadVariantStockMap(List.of(response.variantId()))
                .get(response.variantId());
        return new ProductVariantSearchViewResponse(
                response.productId(),
                response.variantId(),
                response.sku(),
                response.productName(),
                response.variantName(),
                response.categoryName(),
                response.sellingPrice(),
                response.imageUrl(),
                stock != null ? stock.inventoryLocationId() : null,
                stock != null ? stock.availableQuantity() : BigDecimal.ZERO,
                stock != null ? stock.reservedQuantity() : BigDecimal.ZERO,
                stock != null ? stock.stockStatus() : "OUT_OF_STOCK"
        );
    }

    @Override
    public List<ProductVariantProductRefResponse> findRefsByProductIds(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> distinctProductIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctProductIds.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByProductIds(distinctProductIds).stream()
                .map(variant -> new ProductVariantProductRefResponse(
                        variant.getId(),
                        variant.getProduct().getId()))
                .toList();
    }

    private Map<UUID, InventoryClient.StockBatchResponse> loadStockMap(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return inventoryClient.getStocksByProductIds(new InventoryClient.StockBatchRequest(productIds)).stream()
                    .collect(Collectors.toMap(
                            InventoryClient.StockBatchResponse::productId,
                            Function.identity(),
                            (left, ignored) -> left));
        } catch (FeignClientException | FeignException exception) {
            throw new InventoryServiceUnavailableException("Inventory service is temporarily unavailable", exception);
        }
    }

    private Map<UUID, InventoryClient.StockVariantBatchResponse> loadVariantStockMap(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }

            Map<UUID, InventoryClient.StockVariantBatchResponse> a   = inventoryClient.getStocksByVariantIds(new InventoryClient.StockVariantBatchRequest(variantIds)).stream()
                    .collect(Collectors.toMap(
                            InventoryClient.StockVariantBatchResponse::variantId,
                            Function.identity(),
                            (left, ignored) -> left));
            log.info("Loaded {} variant stocks", a);

            return a;

    }

    private ProductVariantSearchViewResponse toPosSearchView(
            ProductVariant variant,
            InventoryClient.StockVariantBatchResponse stock) {
        return new ProductVariantSearchViewResponse(
                variant.getProduct().getId(),
                variant.getId(),
                variant.getSku(),
                variant.getProduct().getName(),
                variant.getVariantName(),
                variant.getProduct().getCategory() != null ? variant.getProduct().getCategory().getName() : null,
                variant.getSellingPrice(),
                variant.getProduct().getImageUrl(),
                stock != null ? stock.inventoryLocationId() : null,
                stock != null ? stock.availableQuantity() : BigDecimal.ZERO,
                stock != null ? stock.reservedQuantity() : BigDecimal.ZERO,
                stock != null ? stock.stockStatus() : "OUT_OF_STOCK"
        );
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ProductVariant variant = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id));
        if (variant.getIsDeleted()) {
            throw new ResourceNotFoundException("ProductVariant", id);
        }
        variant.setIsDeleted(true);
        repository.save(variant);
    }
}
