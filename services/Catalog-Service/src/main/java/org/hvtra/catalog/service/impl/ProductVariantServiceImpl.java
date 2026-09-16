package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.entity.BusinessSequence;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.response.LookupResponse;
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
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.repository.BusinessSequenceRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.util.ExcelExportHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Objects;
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
            UUID categoryId,
            Status status,
            Pageable pageable) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            boolean isCountQuery = Long.class.equals(resultType) || long.class.equals(resultType);
            Join<ProductVariant, Product> productJoin;
            if (isCountQuery) {
                productJoin = root.join("product", JoinType.LEFT);
            } else {
                jakarta.persistence.criteria.Fetch<ProductVariant, Product> productFetch =
                        root.fetch("product", JoinType.LEFT);
                productFetch.fetch("category", JoinType.LEFT);
                root.fetch("unit", JoinType.LEFT);
                productJoin = (Join<ProductVariant, Product>) (Object) productFetch;
            }

            var predicates = new ArrayList<Predicate>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("variantName")), pattern),
                        cb.like(cb.lower(productJoin.get("name")), pattern)
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
            if (categoryId != null) predicates.add(cb.equal(productJoin.get("category").get("id"), categoryId));
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

        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("SKU")
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
        Integer defaultReorder = (category != null && category.getDefaultReorderPoint() != null)
                ? category.getDefaultReorderPoint()
                : 10;
        Integer reorderPoint = request.reorderPoint() != null ? request.reorderPoint() : defaultReorder;

        String variantDisplayName = (savedVariant.getVariantName() != null && !savedVariant.getVariantName().isBlank())
                ? product.getName() + " - " + savedVariant.getVariantName()
                : product.getName();

        inventoryClient.createProductVariantItem(
                InventoryClient.CreateInventoryItemRequest.builder()
                        .status(savedVariant.getStatus().toString())
                        .unitId(savedVariant.getUnit() != null ? savedVariant.getUnit().getId() : null)
                        .referenceId(savedVariant.getId())
                        .referenceCode(savedVariant.getSku())
                        .name(variantDisplayName)
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
        ProductVariant savedVariant = repository.save(variant);

        syncInventoryItem(savedVariant);

        return mapper.toVariantResponse(savedVariant);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse updateStatus(UUID id, Status status) {
        ProductVariant variant = get(id);
        variant.setStatus(status);
        ProductVariant savedVariant = repository.save(variant);
        syncInventoryItem(savedVariant);
        return mapper.toVariantResponse(savedVariant);
    }

    private void syncInventoryItem(ProductVariant variant) {
        if (variant == null || variant.getId() == null) {
            return;
        }
        try {
            Product product = variant.getProduct();
            String productName = product != null ? product.getName() : "";
            String variantDisplayName = (variant.getVariantName() != null && !variant.getVariantName().isBlank() && !productName.isBlank())
                    ? productName + " - " + variant.getVariantName()
                    : (!productName.isBlank() ? productName : variant.getVariantName());

            inventoryClient.updateInventoryItemByReference(
                    InventoryItemType.PRODUCT_VARIANT.toString(),
                    variant.getId(),
                    InventoryClient.CreateInventoryItemRequest.builder()
                            .referenceId(variant.getId())
                            .referenceCode(variant.getSku())
                            .name(variantDisplayName)
                            .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                            .unitId(variant.getUnit() != null ? variant.getUnit().getId() : null)
                            .status(variant.getStatus() != null ? variant.getStatus().toString() : "ACTIVE")
                            .reorderPoint(10)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to sync inventory item for variant id: {}", variant.getId(), e);
        }
    }

    private ProductVariant get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id));
    }

    private void apply(ProductVariant variant, ProductVariantRequest request) {
        variant.setVariantName(request.variantName());
        variant.setSellingPrice(request.sellingPrice());
        variant.setCostPrice(request.costPrice());

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", request.unitId()));
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

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Danh sách Mặt hàng");
            ExcelExportHelper.ExportStyles styles = ExcelExportHelper.createExportStyles(workbook);

            String[] headers = {
                "STT",
                "Mã sản phẩm",
                "Tên sản phẩm",
                "Tên Mặt hàng",
                "SKU",
                "Giá bán",
                "Giá vốn",
                "Đơn vị",
                "Chính sách tồn kho",
                "Trạng thái"
            };

            ExcelExportHelper.createReportHeader(sheet, styles, "BÁO CÁO DANH SÁCH MẶT HÀNG", headers);

            // Fill data starting at row 5
            int rowIdx = 5;
            int stt = 1;
            for (ProductVariant v : variants) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(22);

                String prodCode = v.getProduct() != null ? v.getProduct().getProductCode() : "";
                String prodName = v.getProduct() != null ? v.getProduct().getName() : "";

                Cell cellStt = row.createCell(1);
                cellStt.setCellValue(stt++);
                cellStt.setCellStyle(styles.centerStyle());

                Cell cellProdCode = row.createCell(2);
                cellProdCode.setCellValue(prodCode != null ? prodCode : "");
                cellProdCode.setCellStyle(styles.textStyle());

                Cell cellProdName = row.createCell(3);
                cellProdName.setCellValue(prodName != null ? prodName : "");
                cellProdName.setCellStyle(styles.textStyle());

                Cell cellVariantName = row.createCell(4);
                cellVariantName.setCellValue(v.getVariantName() != null ? v.getVariantName() : "");
                cellVariantName.setCellStyle(styles.textStyle());

                Cell cellSku = row.createCell(5);
                cellSku.setCellValue(v.getSku() != null ? v.getSku() : "");
                cellSku.setCellStyle(styles.textStyle());

                Cell cellSelling = row.createCell(6);
                cellSelling.setCellValue(v.getSellingPrice() != null ? v.getSellingPrice().doubleValue() : 0.0);
                cellSelling.setCellStyle(styles.moneyStyle());

                Cell cellCost = row.createCell(7);
                cellCost.setCellValue(v.getCostPrice() != null ? v.getCostPrice().doubleValue() : 0.0);
                cellCost.setCellStyle(styles.moneyStyle());

                String unitStr = v.getUnit() != null ? v.getUnit().getName() : "";
                Cell cellUnit = row.createCell(8);
                cellUnit.setCellValue(unitStr);
                cellUnit.setCellStyle(styles.centerStyle());

                String policyStr = v.getInventoryPolicy() == InventoryPolicy.TRACK_STOCK ? "Theo dõi tồn kho" : "Không theo dõi";
                Cell cellPolicy = row.createCell(9);
                cellPolicy.setCellValue(policyStr);
                cellPolicy.setCellStyle(styles.centerStyle());

                String statusStr = v.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động";
                Cell cellStatus = row.createCell(10);
                cellStatus.setCellValue(statusStr);
                cellStatus.setCellStyle(styles.centerStyle());
            }

            ExcelExportHelper.finalizeSheet(sheet, headers.length, rowIdx);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất file Excel Mặt hàng: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void importFromExcel(MultipartFile file, UUID productId) {
        var units = unitRepository.findAll();

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
                    if (normalized.contains("ma san pham") || normalized.contains("ma sp") || normalized.contains("ten mat hang") || normalized.contains("ten bien the") || normalized.contains("gia ban")) {
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
            int colProductCode = -1;
            int colVariantName = -1;
            int colSellingPrice = -1;
            int colCostPrice = -1;
            int colUnit = -1;
            int colPolicy = -1;
            int colStatus = -1;

            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String h = getCellValueAsString(headerRow.getCell(c)).toLowerCase().trim();
                    String normalized = java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
                    if (normalized.contains("ma san pham") || normalized.contains("ma sp") || normalized.contains("product code") || normalized.contains("productcode")) {
                        colProductCode = c;
                    } else if (normalized.contains("ten mat hang") || normalized.contains("ten bien the") || normalized.contains("variant name") || (normalized.contains("mat hang") && !normalized.contains("danh sach")) || normalized.contains("ten san pham") || normalized.contains("product name")) {
                        colVariantName = c;
                    } else if (normalized.contains("gia ban") || normalized.contains("selling price")) {
                        colSellingPrice = c;
                    } else if (normalized.contains("gia von") || normalized.contains("cost price")) {
                        colCostPrice = c;
                    } else if (normalized.contains("don vi") || normalized.contains("unit")) {
                        colUnit = c;
                    } else if (normalized.contains("chinh sach") || normalized.contains("ton kho") || normalized.contains("policy")) {
                        colPolicy = c;
                    } else if (normalized.contains("trang thai") || normalized.contains("status")) {
                        colStatus = c;
                    }
                }
            }

            // Fallback to default indices if header matching was not detected
            if (colProductCode == -1) colProductCode = 0;
            if (colVariantName == -1) colVariantName = 1;
            if (colSellingPrice == -1) colSellingPrice = 2;
            if (colCostPrice == -1) colCostPrice = 3;
            if (colUnit == -1) colUnit = 4;
            if (colPolicy == -1) colPolicy = 5;
            if (colStatus == -1) colStatus = 6;

            Map<String, Product> productsByCode = productRepository.findAll().stream()
                    .filter(p -> p.getProductCode() != null && !Boolean.TRUE.equals(p.getIsDeleted()))
                    .collect(Collectors.toMap(p -> p.getProductCode().toUpperCase().trim(), p -> p, (a, b) -> a));

            List<ProductVariant> variantsToSave = new ArrayList<>();

            for (int r = headerRowIndex + 1; r < rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String productCode = colProductCode >= 0 ? getCellValueAsString(row.getCell(colProductCode)) : "";
                String variantName = colVariantName >= 0 ? getCellValueAsString(row.getCell(colVariantName)) : "";
                String sellingPriceStr = colSellingPrice >= 0 ? getCellValueAsString(row.getCell(colSellingPrice)) : "";
                String costPriceStr = colCostPrice >= 0 ? getCellValueAsString(row.getCell(colCostPrice)) : "";
                String unitName = colUnit >= 0 ? getCellValueAsString(row.getCell(colUnit)) : "";
                String policyStr = colPolicy >= 0 ? getCellValueAsString(row.getCell(colPolicy)) : "";
                String statusStr = colStatus >= 0 ? getCellValueAsString(row.getCell(colStatus)) : "";

                if (variantName.isBlank() && productCode.isBlank() && sellingPriceStr.isBlank() && unitName.isBlank()) {
                    continue;
                }

                final int rowNum = r + 1;
                Product product = null;
                if (!productCode.isBlank()) {
                    String cleanCode = productCode.trim().toUpperCase();
                    product = productsByCode.get(cleanCode);
                    if (product == null) {
                        product = productRepository.findByProductCode(productCode.trim())
                                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                                .orElse(null);
                    }
                    if (product == null) {
                        throw new BadRequestException("Dòng " + rowNum + ": Mã sản phẩm '" + productCode + "' không tồn tại trong hệ thống.");
                    }
                } else if (productId != null) {
                    product = productRepository.findById(productId)
                            .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                            .orElseThrow(() -> new BadRequestException("Dòng " + rowNum + ": Sản phẩm không tồn tại trong hệ thống."));
                } else {
                    throw new BadRequestException("Dòng " + rowNum + ": Mã sản phẩm không được để trống.");
                }

                if (variantName.isBlank()) {
                    variantName = product.getName();
                }

                if (unitName.isBlank()) {
                    throw new BadRequestException("Dòng " + rowNum + ": Đơn vị không được để trống.");
                }

                String cleanUnitName = unitName.trim();
                Unit resolvedUnit = units.stream()
                        .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                        .filter(u -> (u.getName() != null && u.getName().equalsIgnoreCase(cleanUnitName))
                                || (u.getSymbol() != null && u.getSymbol().equalsIgnoreCase(cleanUnitName)))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Dòng " + rowNum + ": Đơn vị '" + unitName + "' không tồn tại trong hệ thống."));

                BigDecimal sellingPrice = BigDecimal.ZERO;
                if (!sellingPriceStr.isBlank()) {
                    try {
                        sellingPrice = new BigDecimal(sellingPriceStr.replace(",", ""));
                        if (sellingPrice.compareTo(BigDecimal.ZERO) < 0) {
                            throw new BadRequestException("Dòng " + rowNum + ": Giá bán không được âm.");
                        }
                    } catch (NumberFormatException e) {
                        throw new BadRequestException("Dòng " + rowNum + ": Giá bán không hợp lệ.");
                    }
                }

                BigDecimal costPrice = BigDecimal.ZERO;
                if (!costPriceStr.isBlank()) {
                    try {
                        costPrice = new BigDecimal(costPriceStr.replace(",", ""));
                        if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
                            throw new BadRequestException("Dòng " + rowNum + ": Giá vốn không được âm.");
                        }
                    } catch (NumberFormatException e) {
                        throw new BadRequestException("Dòng " + rowNum + ": Giá vốn không hợp lệ.");
                    }
                }

                InventoryPolicy policy = InventoryPolicy.TRACK_STOCK;
                if ("Không theo dõi".equalsIgnoreCase(policyStr) || "DO_NOT_TRACK".equalsIgnoreCase(policyStr)) {
                    policy = InventoryPolicy.DO_NOT_TRACK;
                } else if ("Theo dõi tồn kho".equalsIgnoreCase(policyStr) || "TRACK_STOCK".equalsIgnoreCase(policyStr) || policyStr.isBlank()) {
                    policy = InventoryPolicy.TRACK_STOCK;
                } else {
                    throw new BadRequestException("Dòng " + rowNum + ": Chính sách tồn kho '" + policyStr + "' không hợp lệ.");
                }

                Status status = "Ngừng hoạt động".equalsIgnoreCase(statusStr) || "INACTIVE".equalsIgnoreCase(statusStr)
                        ? Status.INACTIVE : Status.ACTIVE;

                String autoGeneratedSku = generateSku(product);

                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                variant.setSku(autoGeneratedSku);
                variant.setVariantName(variantName.trim());
                variant.setSellingPrice(sellingPrice);
                variant.setCostPrice(costPrice);
                variant.setUnit(resolvedUnit);
                variant.setInventoryPolicy(policy);
                variant.setStatus(status);

                variantsToSave.add(variant);
            }

            if (!variantsToSave.isEmpty()) {
                List<ProductVariant> savedVariants = repository.saveAll(variantsToSave);

                for (ProductVariant savedVariant : savedVariants) {
                    String variantDisplayName = (savedVariant.getVariantName() != null && !savedVariant.getVariantName().isBlank())
                            ? savedVariant.getProduct().getName() + " - " + savedVariant.getVariantName()
                            : savedVariant.getProduct().getName();

                    inventoryClient.createProductVariantItem(
                            InventoryClient.CreateInventoryItemRequest.builder()
                                     .status(savedVariant.getStatus().toString())
                                    .unitId(savedVariant.getUnit() != null ? savedVariant.getUnit().getId() : null)
                                    .referenceId(savedVariant.getId())
                                    .referenceCode(savedVariant.getSku())
                                    .name(variantDisplayName)
                                    .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                                    .reorderPoint(10)
                                    .build());
                }
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
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
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Mẫu nhập Mặt hàng");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            CellStyle priceStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            priceStyle.setDataFormat(format.getFormat("#,##0"));

            String[] headers = {"Mã sản phẩm", "Tên Mặt hàng", "Giá bán", "Giá vốn", "Đơn vị", "Chính sách tồn kho", "Trạng thái"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Add template rows
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("PRD00001");
            row1.createCell(1).setCellValue("Thiết Quan Âm 100g");

            Cell cellSelling1 = row1.createCell(2);
            cellSelling1.setCellValue(150000.0);
            cellSelling1.setCellStyle(priceStyle);

            Cell cellCost1 = row1.createCell(3);
            cellCost1.setCellValue(100000.0);
            cellCost1.setCellStyle(priceStyle);

            row1.createCell(4).setCellValue("Gói 100g");
            row1.createCell(5).setCellValue("Theo dõi tồn kho");
            row1.createCell(6).setCellValue("Đang hoạt động");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("PRD00001");
            row2.createCell(1).setCellValue("Thiết Quan Âm 250g");

            Cell cellSelling2 = row2.createCell(2);
            cellSelling2.setCellValue(350000.0);
            cellSelling2.setCellStyle(priceStyle);

            Cell cellCost2 = row2.createCell(3);
            cellCost2.setCellValue(230000.0);
            cellCost2.setCellStyle(priceStyle);

            row2.createCell(4).setCellValue("Gói 250g");
            row2.createCell(5).setCellValue("Theo dõi tồn kho");
            row2.createCell(6).setCellValue("Đang hoạt động");

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 2, 0, headers.length - 1));

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
    public List<UUID> searchVariantIds(String keyword, String sku, String productCode, String productName, String categoryCode, UUID categoryId, UUID unitId) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
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

    @Override
    public PageResponse<LookupResponse> lookup(String search, Pageable pageable) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            boolean isCountQuery = Long.class.equals(resultType) || long.class.equals(resultType);
            Join<ProductVariant, Product> productJoin = isCountQuery
                    ? root.join("product", JoinType.LEFT)
                    : (Join<ProductVariant, Product>) (Object) root.fetch("product", JoinType.LEFT);

            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("status"), Status.ACTIVE));

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                var searchPredicates = new Predicate[] {
                    cb.like(cb.lower(root.get("variantName")), searchPattern),
                    cb.like(cb.lower(root.get("sku")), searchPattern),
                    cb.like(cb.lower(productJoin.get("name")), searchPattern)
                };
                predicates.add(cb.or(searchPredicates));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        org.springframework.data.domain.Page<ProductVariant> page = repository.findAll(spec, pageable);

        return new PageResponse<>(
                page.getContent().stream()
                        .map(v -> {
                            String displayName = v.getVariantName();
                            if (displayName == null || displayName.trim().isEmpty()) {
                                displayName = v.getProduct().getName();
                            }
                            return new LookupResponse(v.getId().toString(), displayName);
                        })
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
