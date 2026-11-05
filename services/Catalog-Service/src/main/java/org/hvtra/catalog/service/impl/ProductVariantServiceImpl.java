package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.commonservice.common_core.util.CodeUtils;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.SellableConfigRequest;
import org.hvtra.catalog.dto.request.SellableQuantityAdjustmentRequest;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductVariantProductRefResponse;
import org.hvtra.catalog.dto.response.SellableQuantityAdjustmentResponse;
import org.hvtra.catalog.dto.response.VariantCostResponse;
import org.hvtra.catalog.dto.response.VariantInventorySummaryResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.SellableAdjustment;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryItemType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.outbox.publisher.CatalogOutboxPublisher;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.SellableAdjustmentRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.util.ExcelExportHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import org.hvtra.catalog.enums.InventoryPolicy;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {

    private static final String CUSTOM_BUNDLE_SKU = "CUSTOM-BUNDLE";

    private final ProductVariantRepository repository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final UnitRepository unitRepository;
    private final BusinessSequenceRepository businessSequenceRepository;
    private final IngredientRepository ingredientRepository;
    private final SellableAdjustmentRepository adjustmentRepository;
    private final CatalogOutboxPublisher catalogOutboxPublisher;

    public ProductVariantServiceImpl(
            ProductVariantRepository repository,
            ProductRepository productRepository,
            ProductMapper mapper,
            UnitRepository unitRepository,
            BusinessSequenceRepository businessSequenceRepository,
            IngredientRepository ingredientRepository,
            SellableAdjustmentRepository adjustmentRepository,
            CatalogOutboxPublisher catalogOutboxPublisher) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.unitRepository = unitRepository;
        this.businessSequenceRepository = businessSequenceRepository;
        this.ingredientRepository = ingredientRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.catalogOutboxPublisher = catalogOutboxPublisher;
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
        Specification<ProductVariant> spec =
                searchSpecification(keyword, sku, variantName, barcode, productId, categoryId, status);
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toVariantSummary));
    }

    /** Điều kiện tìm biến thể; keyword khớp SKU, tên biến thể, tên sản phẩm hoặc đúng mã vạch. */
    static Specification<ProductVariant> searchSpecification(
            String keyword,
            String sku,
            String variantName,
            String barcode,
            UUID productId,
            UUID categoryId,
            Status status) {
        return (root, query, cb) -> {
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
            predicates.add(cb.isFalse(root.get("customBundle")));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("variantName")), pattern),
                        cb.like(cb.lower(productJoin.get("name")), pattern),
                        cb.like(cb.lower(root.get("barcode")), pattern)
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
                predicates.add(cb.like(
                        cb.lower(root.get("barcode")),
                        "%" + barcode.toLowerCase() + "%"));
            }
            if (productId != null) predicates.add(cb.equal(root.get("product").get("id"), productId));
            if (categoryId != null) predicates.add(cb.equal(productJoin.get("category").get("id"), categoryId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public ProductResponse.VariantResponse findById(UUID id) {
        return mapper.toVariantResponse(get(id));
    }

    @Override
    public ProductResponse.VariantResponse findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new BadRequestException("Barcode không được để trống");
        }
        return repository.findByBarcodeAndIsDeletedFalse(barcode.trim())
                .map(mapper::toVariantResponse)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", barcode));
    }

    @Override
    public ProductResponse.VariantResponse findCustomBundleVariant() {
        return repository.findFirstByCustomBundleTrueAndIsDeletedFalse()
                .map(mapper::toVariantResponse)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", CUSTOM_BUNDLE_SKU));
    }

    @Transactional
    public String generateSku(Product product, Integer quantity, String symbolUnit) {
        String prefix = CodeUtils.getInitials(product != null ? product.getName() : null, "PR", quantity, symbolUnit);
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("SKU")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("SKU");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        String sku;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            sku = String.format("%s-%05d", prefix, nextVal);
        } while (repository.existsBySku(sku));

        businessSequenceRepository.saveAndFlush(seq);
        return sku;
    }

    @Transactional
    public String generateSku(Product product) {
        return generateSku(product, null, null);
    }

    @Override
    @Transactional
    public String generateBarcode() {
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("BARCODE")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("BARCODE");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        String barcode;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            barcode = calculateEan13("893", nextVal);
        } while (repository.existsByBarcode(barcode));

        businessSequenceRepository.saveAndFlush(seq);
        return barcode;
    }

    private static String calculateEan13(String prefix, long seqNum) {
        String base12 = String.format("%s%09d", prefix, seqNum);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(base12.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return base12 + checkDigit;
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        apply(variant, request);

        Integer quantity = variant.getQuantity() != null ? variant.getQuantity().intValue() : null;
        String symbol = variant.getUnit() != null ? variant.getUnit().getSymbol() : null;
        String generatedSku = generateSku(product, quantity, symbol);
        variant.setSku(generatedSku);

        if (variant.getBarcode() == null || variant.getBarcode().isBlank()) {
            variant.setBarcode(generateBarcode());
        }

        ProductVariant savedVariant = repository.saveAndFlush(variant);

        if (catalogOutboxPublisher != null) {
            catalogOutboxPublisher.publishVariantCreated(savedVariant);
        }
        return mapper.toVariantResponseForNewlyCreated(savedVariant);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request) {
        ProductVariant variant = get(id);
        requireNotCustomBundle(variant);
        apply(variant, request);
        ProductVariant savedVariant = repository.save(variant);

        if (catalogOutboxPublisher != null) {
            catalogOutboxPublisher.publishVariantUpdated(savedVariant);
        }

        return mapper.toVariantResponse(savedVariant);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse updateStatus(UUID id, Status status) {
        ProductVariant variant = get(id);
        requireNotCustomBundle(variant);
        variant.setStatus(status);
        ProductVariant savedVariant = repository.save(variant);
        if (catalogOutboxPublisher != null) {
            catalogOutboxPublisher.publishVariantUpdated(savedVariant);
        }
        return mapper.toVariantResponse(savedVariant);
    }

    /**
     * Khóa dòng trước khi ghi để không đè lên lượt trừ/hoàn sellable của đơn hàng đang chạy song song.
     */
    @Override
    @Transactional
    public ProductResponse.VariantResponse updateSellableConfig(UUID id, SellableConfigRequest request) {
        ProductVariant variant = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id));
        requireNotCustomBundle(variant);
        variant.setSellableQuantity(request.sellableQuantity());
        variant.setProductionThreshold(request.productionThreshold());
        variant.setProductionBatchQuantity(request.productionBatchQuantity());
        ProductVariant saved = repository.save(variant);
        if (catalogOutboxPublisher != null) {
            catalogOutboxPublisher.publishVariantUpdated(saved);
        }
        return mapper.toVariantResponse(saved);
    }

    @Override
    @Transactional
    public SellableQuantityAdjustmentResponse decrementSellableQuantity(
            UUID id,
            SellableQuantityAdjustmentRequest request) {
        BigDecimal quantity = requirePositiveQuantity(request.quantity());
        ProductVariant before = request.operationKey() == null ? get(id) : repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id));
        SellableAdjustment prior = findAdjustment(id, request);
        if (prior != null) {
            return adjustmentResponse(before, request, prior);
        }
        BigDecimal oldSellableQuantity = safeQuantity(before.getSellableQuantity());
        if (before.isCustomBundle()) {
            // Bundle trừ tồn theo nguyên liệu thành phần, không theo sellable quantity của variant.
            return toSellableAdjustmentResponse(before, request, quantity, oldSellableQuantity, oldSellableQuantity, false);
        }
        int updated = repository.decrementSellableIfEnough(id, quantity);
        if (updated == 0) {
            throw new IllegalArgumentException("Insufficient sellable quantity for product variant " + id);
        }
        ProductVariant after = get(id);
        BigDecimal newSellableQuantity = safeQuantity(after.getSellableQuantity());
        boolean crossed = thresholdCrossed(oldSellableQuantity, newSellableQuantity, after.getProductionThreshold());
        recordAdjustment(id, request, quantity, oldSellableQuantity, newSellableQuantity, crossed);
        return toSellableAdjustmentResponse(
                after,
                request,
                quantity,
                oldSellableQuantity,
                newSellableQuantity,
                crossed);
    }

    @Override
    @Transactional
    public SellableQuantityAdjustmentResponse restoreSellableQuantity(
            UUID id,
            SellableQuantityAdjustmentRequest request) {
        BigDecimal quantity = requirePositiveQuantity(request.quantity());
        ProductVariant before = request.operationKey() == null ? get(id) : repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id));
        SellableAdjustment prior = findAdjustment(id, request);
        if (prior != null) {
            return adjustmentResponse(before, request, prior);
        }
        BigDecimal oldSellableQuantity = safeQuantity(before.getSellableQuantity());
        if (before.isCustomBundle()) {
            // Bundle trừ tồn theo nguyên liệu thành phần, không theo sellable quantity của variant.
            return toSellableAdjustmentResponse(before, request, quantity, oldSellableQuantity, oldSellableQuantity, false);
        }
        int updated = repository.restoreSellable(id, quantity);
        if (updated == 0) {
            throw new ResourceNotFoundException("ProductVariant", id);
        }
        ProductVariant after = get(id);
        BigDecimal newSellableQuantity = safeQuantity(after.getSellableQuantity());
        recordAdjustment(id, request, quantity, oldSellableQuantity, newSellableQuantity, false);
        return toSellableAdjustmentResponse(
                after,
                request,
                quantity,
                oldSellableQuantity,
                newSellableQuantity,
                false);
    }

    private SellableAdjustment findAdjustment(UUID variantId, SellableQuantityAdjustmentRequest request) {
        return request.operationKey() == null ? null : adjustmentRepository
                .findByVariantIdAndOperationKey(variantId, request.operationKey()).orElse(null);
    }

    private SellableQuantityAdjustmentResponse adjustmentResponse(ProductVariant variant,
            SellableQuantityAdjustmentRequest request, SellableAdjustment prior) {
        if (prior.getQuantity().compareTo(request.quantity()) != 0) {
            throw new IllegalArgumentException("Operation key reused with different quantity");
        }
        return toSellableAdjustmentResponse(variant, request, prior.getQuantity(), prior.getOldQuantity(),
                prior.getNewQuantity(), prior.isThresholdCrossed());
    }

    private void recordAdjustment(UUID variantId, SellableQuantityAdjustmentRequest request, BigDecimal quantity,
            BigDecimal oldQuantity, BigDecimal newQuantity, boolean crossed) {
        if (request.operationKey() == null) {
            return;
        }
        SellableAdjustment adjustment = new SellableAdjustment();
        adjustment.setVariantId(variantId);
        adjustment.setOperationKey(request.operationKey());
        adjustment.setQuantity(quantity);
        adjustment.setOldQuantity(oldQuantity);
        adjustment.setNewQuantity(newQuantity);
        adjustment.setThresholdCrossed(crossed);
        adjustmentRepository.save(adjustment);
    }

    /** Variant hệ thống "Trà đóng theo yêu cầu" do migration tạo; không cho sửa/xoá qua API quản lý. */
    private void requireNotCustomBundle(ProductVariant variant) {
        if (variant.isCustomBundle()) {
            throw new BadRequestException("Mặt hàng hệ thống \"Trà đóng theo yêu cầu\" không được chỉnh sửa hoặc xoá");
        }
    }

    private ProductVariant get(UUID id) {
        ProductVariant variant = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id));
        if (Boolean.TRUE.equals(variant.getIsDeleted())) {
            throw new ResourceNotFoundException("ProductVariant", id);
        }
        return variant;
    }

    private void apply(ProductVariant variant, ProductVariantRequest request) {
        variant.setQuantity(request.quantity());
        variant.setSellingPrice(request.sellingPrice());
        variant.setCostPrice(request.costPrice());

        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", request.unitId()));
        variant.setUnit(unit);

        variant.setInventoryPolicy(request.inventoryPolicy());
        variant.setStatus(request.status());

        if (request.barcode() != null && !request.barcode().isBlank()) {
            String trimmedBarcode = request.barcode().trim();
            boolean exists = variant.getId() != null
                    ? repository.existsByBarcodeAndIdNot(trimmedBarcode, variant.getId())
                    : repository.existsByBarcode(trimmedBarcode);
            if (exists) {
                throw new BadRequestException("Mã vạch đã tồn tại: " + trimmedBarcode);
            }
            variant.setBarcode(trimmedBarcode);
        } else if (variant.getId() == null) {
            variant.setBarcode(null);
        }

        variant.setVariantName(composeVariantName(variant.getProduct(), request.quantity(), unit));
    }

    private BigDecimal requirePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        return quantity;
    }

    private BigDecimal safeQuantity(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean thresholdCrossed(BigDecimal oldQuantity, BigDecimal newQuantity, BigDecimal threshold) {
        BigDecimal safeThreshold = safeQuantity(threshold);
        return oldQuantity.compareTo(safeThreshold) > 0 && newQuantity.compareTo(safeThreshold) <= 0;
    }

    private SellableQuantityAdjustmentResponse toSellableAdjustmentResponse(
            ProductVariant variant,
            SellableQuantityAdjustmentRequest request,
            BigDecimal requestedQuantity,
            BigDecimal oldSellableQuantity,
            BigDecimal newSellableQuantity,
            boolean thresholdCrossed) {
        return new SellableQuantityAdjustmentResponse(
                variant.getId(),
                requestedQuantity,
                oldSellableQuantity,
                newSellableQuantity,
                thresholdCrossed,
                safeQuantity(variant.getProductionThreshold()),
                safeQuantity(variant.getProductionBatchQuantity()),
                request.locationId(),
                request.reason());
    }

    /**
     * Tên Mặt hàng luôn được hệ thống tự sinh, không cho người dùng tự nhập,
     * theo công thức "{Tên sản phẩm} {Định lượng} {Tên đơn vị}".
     */
    private String composeVariantName(Product product, BigDecimal quantity, Unit unit) {
        String productName = product != null && product.getName() != null ? product.getName().trim() : "";
        String qtyStr = quantity != null ? quantity.stripTrailingZeros().toPlainString() : "";
        String unitName = unit != null && unit.getName() != null ? unit.getName().trim() : "";
        return (productName + " " + qtyStr + " " + unitName).trim().replaceAll("\\s+", " ");
    }

    @Override
    public byte[] exportToExcel(UUID productId) {
        List<ProductVariant> variants = repository.findAllForExport(productId);

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
                "Định lượng",
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

                Cell cellQuantity = row.createCell(6);
                cellQuantity.setCellValue(v.getQuantity() != null ? v.getQuantity().doubleValue() : 0.0);
                cellQuantity.setCellStyle(styles.centerStyle());

                Cell cellSelling = row.createCell(7);
                cellSelling.setCellValue(v.getSellingPrice() != null ? v.getSellingPrice().doubleValue() : 0.0);
                cellSelling.setCellStyle(styles.moneyStyle());

                Cell cellCost = row.createCell(8);
                cellCost.setCellValue(v.getCostPrice() != null ? v.getCostPrice().doubleValue() : 0.0);
                cellCost.setCellStyle(styles.moneyStyle());

                String unitStr = v.getUnit() != null ? v.getUnit().getName() : "";
                Cell cellUnit = row.createCell(9);
                cellUnit.setCellValue(unitStr);
                cellUnit.setCellStyle(styles.centerStyle());

                String policyStr = v.getInventoryPolicy() == InventoryPolicy.TRACK_STOCK ? "Theo dõi tồn kho" : "Không theo dõi";
                Cell cellPolicy = row.createCell(10);
                cellPolicy.setCellValue(policyStr);
                cellPolicy.setCellStyle(styles.centerStyle());

                String statusStr = v.getStatus() == Status.ACTIVE ? "Đang hoạt động" : "Ngừng hoạt động";
                Cell cellStatus = row.createCell(11);
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
    public byte[] getImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildImportInstructionSheet(workbook);
            buildImportVariantSheet(workbook);
            buildImportBomSheet(workbook);
            buildImportReferenceSheet(workbook);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo file mẫu Excel Mặt hàng: " + e.getMessage(), e);
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

        String[] titleLines = {"HƯỚNG DẪN NHẬP MẶT HÀNG (KÈM BOM) TỪ EXCEL"};
        String[][] sections = {
                {
                        "1. Cấu trúc file",
                        "File gồm 4 sheet:",
                        "- \"Hướng dẫn\": sheet này.",
                        "- \"Mặt hàng\": nhập dữ liệu mặt hàng cần tạo, mỗi dòng là 1 mặt hàng.",
                        "- \"BOM\": nhập định mức nguyên liệu (nếu có) cho từng mặt hàng ở sheet \"Mặt hàng\".",
                        "- \"Tham chiếu\": tra cứu mã sản phẩm/nguyên liệu/đơn vị để điền vào 2 sheet trên."
                },
                {
                        "2. Cách liên kết sheet \"Mặt hàng\" và sheet \"BOM\"",
                        "- Ở sheet \"Mặt hàng\", cột \"Ref\" là mã tự đặt riêng cho từng dòng trong file này (ví dụ MH1, MH2...), dùng để liên kết — không phải mã hệ thống.",
                        "- Ở sheet \"BOM\", cột \"Ref mặt hàng\" phải điền đúng giá trị \"Ref\" tương ứng ở sheet \"Mặt hàng\" để hệ thống biết dòng nguyên liệu đó thuộc mặt hàng nào.",
                        "- 1 mặt hàng có thể có nhiều dòng BOM (nhiều nguyên liệu); mặt hàng không cần BOM thì không cần thêm dòng nào ở sheet \"BOM\"."
                },
                {
                        "3. Cách điền sheet \"Mặt hàng\"",
                        "- Mã sản phẩm (*): bắt buộc, lấy từ cột \"Mã sản phẩm\" ở sheet \"Tham chiếu\".",
                        "- Định lượng (*), Đơn vị (*): bắt buộc. Tên Mặt hàng và SKU sẽ được hệ thống tự sinh, không cần nhập.",
                        "- Giá bán, Giá vốn: để trống sẽ mặc định là 0.",
                        "- Chính sách tồn kho: \"Theo dõi tồn kho\" hoặc \"Không theo dõi\" (mặc định Theo dõi tồn kho).",
                        "- Trạng thái: \"Đang hoạt động\" hoặc \"Ngừng hoạt động\" (mặc định Đang hoạt động)."
                },
                {
                        "4. Cách điền sheet \"BOM\"",
                        "- Ref mặt hàng (*): phải khớp với 1 giá trị \"Ref\" ở sheet \"Mặt hàng\".",
                        "- Mã nguyên liệu (*): lấy từ cột \"Mã nguyên liệu\" ở sheet \"Tham chiếu\".",
                        "- Định lượng (*), Đơn vị (*): bắt buộc.",
                        "- Tỷ lệ hao hụt: để trống sẽ mặc định là 0."
                },
                {
                        "5. Sau khi tải file lên hệ thống",
                        "- Dữ liệu được phân tích thành bản nháp để bạn kiểm tra/sửa trực tiếp trên hệ thống trước khi gửi duyệt.",
                        "- Dữ liệu (mặt hàng + BOM) chỉ được ghi chính thức vào hệ thống sau khi quản lý duyệt."
                },
                {
                        "6. Lưu ý",
                        "- Không đổi tên các sheet, không đổi tên cột header ở dòng đầu tiên của sheet \"Mặt hàng\" và \"BOM\".",
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
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        if (headerStyle instanceof XSSFCellStyle xssfHeaderStyle) {
            xssfHeaderStyle.setFillForegroundColor(new XSSFColor(ExcelExportHelper.BRAND_GREEN_RGB, new DefaultIndexedColorMap()));
        }
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return headerStyle;
    }

    private void buildImportVariantSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Mặt hàng");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Ref", "Mã sản phẩm", "Định lượng", "Đơn vị", "Giá bán", "Giá vốn", "Chính sách tồn kho", "Trạng thái"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("MH1");
        row1.createCell(1).setCellValue("PRD00001");
        row1.createCell(2).setCellValue(100.0);
        row1.createCell(3).setCellValue("Kg");
        row1.createCell(4).setCellValue(150000.0);
        row1.createCell(5).setCellValue(100000.0);
        row1.createCell(6).setCellValue("Theo dõi tồn kho");
        row1.createCell(7).setCellValue("Đang hoạt động");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("MH2");
        row2.createCell(1).setCellValue("PRD00001");
        row2.createCell(2).setCellValue(250.0);
        row2.createCell(3).setCellValue("Kg");
        row2.createCell(4).setCellValue(350000.0);
        row2.createCell(5).setCellValue(230000.0);
        row2.createCell(6).setCellValue("Theo dõi tồn kho");
        row2.createCell(7).setCellValue("Đang hoạt động");

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 2, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildImportBomSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("BOM");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Ref mặt hàng", "Mã nguyên liệu", "Định lượng", "Đơn vị", "Tỷ lệ hao hụt (%)"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("MH1");
        row1.createCell(1).setCellValue("ING00001");
        row1.createCell(2).setCellValue(0.05);
        row1.createCell(3).setCellValue("Kg");
        row1.createCell(4).setCellValue(0.0);

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 1, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildImportReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Tham chiếu");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        // Mỗi thực thể tham chiếu gồm 2 cột: Mã và Tên
        String[] headers = {
                "Mã sản phẩm", "Tên sản phẩm",
                "Mã nguyên liệu", "Tên nguyên liệu",
                "Ký hiệu đơn vị", "Tên đơn vị"
        };
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        record CodeName(String code, String name) {}

        List<CodeName> products = productRepository.findAll().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .filter(p -> p.getProductCode() != null && !p.getProductCode().isBlank())
                .sorted((a, b) -> a.getProductCode().compareToIgnoreCase(b.getProductCode()))
                .map(p -> new CodeName(p.getProductCode(), p.getName() != null ? p.getName() : ""))
                .toList();

        List<CodeName> ingredients = ingredientRepository.findAll().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getIsDeleted()))
                .filter(i -> i.getIngredientCode() != null && !i.getIngredientCode().isBlank())
                .sorted((a, b) -> a.getIngredientCode().compareToIgnoreCase(b.getIngredientCode()))
                .map(i -> new CodeName(i.getIngredientCode(), i.getName() != null ? i.getName() : ""))
                .toList();

        List<CodeName> units = unitRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> u.getSymbol() != null && !u.getSymbol().isBlank())
                .sorted((a, b) -> a.getSymbol().compareToIgnoreCase(b.getSymbol()))
                .map(u -> new CodeName(u.getSymbol(), u.getName() != null ? u.getName() : ""))
                .toList();

        int maxRows = Math.max(products.size(), Math.max(ingredients.size(), units.size()));
        for (int i = 0; i < maxRows; i++) {
            Row row = sheet.createRow(i + 1);
            if (i < products.size()) {
                row.createCell(0).setCellValue(products.get(i).code());
                row.createCell(1).setCellValue(products.get(i).name());
            }
            if (i < ingredients.size()) {
                row.createCell(2).setCellValue(ingredients.get(i).code());
                row.createCell(3).setCellValue(ingredients.get(i).name());
            }
            if (i < units.size()) {
                row.createCell(4).setCellValue(units.get(i).code());
                row.createCell(5).setCellValue(units.get(i).name());
            }
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
            } catch (Exception ignored) {}
        }
    }

    @Override
    public List<ProductResponse.VariantResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllByIdWithDetails(ids).stream()
                .map(mapper::toVariantResponse)
                .toList();
    }

    @Override
    public List<VariantCostResponse> findCostPricesByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findCostPricesByIds(ids);
    }

    @Override
    public List<VariantInventorySummaryResponse> findInventorySummariesByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findInventorySummariesByIds(ids);
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
            predicates.add(cb.isFalse(root.get("customBundle")));

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
        requireNotCustomBundle(variant);
        variant.setIsDeleted(true);
        repository.save(variant);
        if (catalogOutboxPublisher != null) {
            catalogOutboxPublisher.publishVariantDeleted(variant);
        }
    }
}
