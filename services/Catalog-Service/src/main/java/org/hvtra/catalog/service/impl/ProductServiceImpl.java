package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.commonservice.common_core.util.CodeUtils;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.service.ProductService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** Sản phẩm hệ thống "Trà đóng theo yêu cầu" (migration V261019100000). */
    private static final String CUSTOM_BUNDLE_PRODUCT_CODE = "CUSTOM-BUNDLE";

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessSequenceRepository businessSequenceRepository;
    private final ProductMapper mapper;
    private final FileClient fileClient;

    public ProductServiceImpl(ProductRepository productRepository,
                              ProductVariantRepository productVariantRepository,
                              CategoryRepository categoryRepository,
                              BusinessSequenceRepository businessSequenceRepository,
                              ProductMapper mapper,
                              FileClient fileClient) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.categoryRepository = categoryRepository;
        this.businessSequenceRepository = businessSequenceRepository;
        this.mapper = mapper;
        this.fileClient = fileClient;
    }

    @Override
    public PageResponse<ProductResponse> findAll(
            String name, UUID categoryId, Status status, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            boolean isCountQuery = Long.class.equals(resultType) || long.class.equals(resultType);
            if (!isCountQuery) {
                root.fetch("category", jakarta.persistence.criteria.JoinType.LEFT);
            }

            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.notEqual(root.get("productCode"), CUSTOM_BUNDLE_PRODUCT_CODE));
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        List<Product> products = productPage.getContent();

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<ProductVariant>> variantsByProduct = productIds.isEmpty()
                ? Map.of()
                : productVariantRepository.findByProductIdsWithDetails(productIds).stream()
                        .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        List<ProductResponse> productResponses = products.stream()
                .map(p -> mapper.toResponse(p, variantsByProduct.getOrDefault(p.getId(), List.of())))
                .toList();
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
        Product product = getProduct(id);
        return mapper.toResponse(product, productVariantRepository.findByProductIdsWithDetails(List.of(id)));
    }

    @Transactional
    public String generateProductCode(String productName) {
        String prefix = CodeUtils.getInitials(productName, "SP");
        BusinessSequence seq = businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")
                .orElseGet(() -> {
                    BusinessSequence newSeq = new BusinessSequence();
                    newSeq.setSequenceKey("PRODUCT");
                    newSeq.setCurrentValue(0L);
                    return businessSequenceRepository.saveAndFlush(newSeq);
                });

        String code;
        do {
            long nextVal = seq.getCurrentValue() + 1;
            seq.setCurrentValue(nextVal);
            code = String.format("%s-%02d", prefix, nextVal);
        } while (productRepository.existsByProductCode(code));

        businessSequenceRepository.saveAndFlush(seq);
        return code;
    }

    @Transactional
    public String generateProductCode() {
        return generateProductCode(null);
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        String code = request.productCode();
        if (code == null || code.isBlank()) {
            code = generateProductCode(request.name());
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
        if (request.fieldId() != null) {
            fileClient.attachFile(FileClient.FileAttach.builder()
                    .refId(savedProduct.getId())
                    .fieldId(request.fieldId())
                    .build());
        }
        return mapper.toResponse(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getProduct(id);
        requireNotCustomBundle(product);
        
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
        requireNotCustomBundle(product);
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

    private void requireNotCustomBundle(Product product) {
        if (CUSTOM_BUNDLE_PRODUCT_CODE.equals(product.getProductCode())) {
            throw new BadRequestException("Sản phẩm hệ thống \"Trà đóng theo yêu cầu\" không được chỉnh sửa hoặc xoá");
        }
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Override
    public byte[] exportToExcel() {
        var products = productRepository.findAllWithCategory();
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
    public byte[] getImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildImportInstructionSheet(workbook);
            buildImportDataEntrySheet(workbook);
            buildImportReferenceSheet(workbook);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo file mẫu Excel sản phẩm: " + e.getMessage(), e);
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

        String[] titleLines = {"HƯỚNG DẪN NHẬP SẢN PHẨM TỪ EXCEL"};
        String[][] sections = {
                {
                        "1. Cấu trúc file",
                        "File gồm 3 sheet:",
                        "- \"Hướng dẫn\": sheet này, mô tả cách điền dữ liệu.",
                        "- \"Nhập thông tin sản phẩm\": nhập dữ liệu sản phẩm cần tạo, mỗi dòng là 1 sản phẩm.",
                        "- \"Danh mục tham chiếu\": tra cứu mã của các dữ liệu tham chiếu để điền vào sheet nhập liệu."
                },
                {
                        "2. Cách điền sheet \"Nhập thông tin sản phẩm\"",
                        "- Mã danh mục (*): bắt buộc, lấy từ cột \"Danh mục\" ở sheet \"Danh mục tham chiếu\".",
                        "- Tên sản phẩm (*): bắt buộc.",
                        "- Trạng thái: \"Đang hoạt động\" hoặc \"Ngừng hoạt động\" (để trống sẽ mặc định là Đang hoạt động).",
                        "- Mô tả: không bắt buộc."
                },
                {
                        "3. Sau khi tải file lên hệ thống",
                        "- Dữ liệu được phân tích thành bản nháp để bạn kiểm tra/sửa trực tiếp trên hệ thống trước khi gửi duyệt.",
                        "- Dữ liệu chỉ được ghi chính thức vào hệ thống sau khi quản lý duyệt."
                },
                {
                        "4. Lưu ý",
                        "- Không đổi tên các sheet, không đổi tên cột header ở dòng đầu tiên của sheet \"Nhập thông tin sản phẩm\".",
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

    private void buildImportDataEntrySheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Nhập thông tin sản phẩm");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Mã danh mục", "Tên sản phẩm", "Trạng thái", "Mô tả"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("CAT00001");
        row1.createCell(1).setCellValue("Trà Thiết Quan Âm");
        row1.createCell(2).setCellValue("Đang hoạt động");
        row1.createCell(3).setCellValue("Trà thiết quan âm thượng hạng thơm ngon");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("CAT00001");
        row2.createCell(1).setCellValue("Trà Sen Tây Hồ");
        row2.createCell(2).setCellValue("Đang hoạt động");
        row2.createCell(3).setCellValue("Trà sen Tây Hồ ướp hương sen tự nhiên");

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 2, 0, headers.length - 1));
        widenColumns(sheet, headers.length);
    }

    private void buildImportReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Danh mục tham chiếu");
        CellStyle headerStyle = createGreenHeaderStyle(workbook);

        String[] headers = {"Mã danh mục", "Tên danh mục"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        record CodeName(String code, String name) {}

        List<CodeName> categories = categoryRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .filter(c -> c.getCategoryCode() != null && !c.getCategoryCode().isBlank())
                .sorted((a, b) -> a.getCategoryCode().compareToIgnoreCase(b.getCategoryCode()))
                .map(c -> new CodeName(c.getCategoryCode(), c.getName() != null ? c.getName() : ""))
                .toList();

        for (int i = 0; i < categories.size(); i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(categories.get(i).code());
            row.createCell(1).setCellValue(categories.get(i).name());
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
    public List<ProductResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Product> products = productRepository.findAllByIdWithCategory(ids);
        Map<UUID, List<ProductVariant>> variantsByProduct = products.isEmpty()
                ? Map.of()
                : productVariantRepository.findByProductIdsWithDetails(
                                products.stream().map(Product::getId).toList()).stream()
                        .collect(Collectors.groupingBy(v -> v.getProduct().getId()));
        return products.stream()
                .map(p -> mapper.toResponse(p, variantsByProduct.getOrDefault(p.getId(), List.of())))
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
        requireNotCustomBundle(product);
        product.setIsDeleted(true);
        productRepository.save(product);
    }
}
