package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @Mock
    private ProductMapper mapper;

    @Mock
    private FileClient fileClient;

    @InjectMocks
    private ProductServiceImpl productService;

    private UUID productId;
    private UUID categoryId;
    private Category category;
    private Product product;
    private ProductRequest productRequest;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        category = new Category();
        category.setId(categoryId);
        category.setName("Trà Thượng Hạng");
        category.setCategoryCode("CAT-TRA");

        product = new Product();
        product.setId(productId);
        product.setCategory(category);
        product.setProductCode("SP-01");
        product.setName("Trà Shan Tuyết Cổ Thụ");
        product.setStatus(Status.ACTIVE);
        product.setIsDeleted(false);

        productRequest = new ProductRequest(
                categoryId,
                null,
                "Trà Shan Tuyết Cổ Thụ",
                "Mô tả trà shan tuyết",
                "http://image.url",
                Status.ACTIVE,
                null
        );

        productResponse = new ProductResponse(
                productId,
                null,
                "SP-01",
                "Trà Shan Tuyết Cổ Thụ",
                "Mô tả trà shan tuyết",
                "http://image.url",
                Status.ACTIVE,
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("create - Tạo sản phẩm hợp lệ với mã tự sinh thành công")
    void create_WhenValidRequest_ShouldReturnResponse() {
        BusinessSequence seq = new BusinessSequence();
        seq.setCurrentValue(0L);

        when(businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")).thenReturn(Optional.of(seq));
        when(productRepository.existsByProductCode(any(String.class))).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(mapper.toResponse(any(Product.class))).thenReturn(productResponse);

        ProductResponse result = productService.create(productRequest);

        assertNotNull(result);
        assertEquals("SP-01", result.productCode());
        assertEquals("Trà Shan Tuyết Cổ Thụ", result.name());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("create - Danh mục không tồn tại thì ném ResourceNotFoundException")
    void create_WhenCategoryNotFound_ShouldThrowResourceNotFoundException() {
        BusinessSequence seq = new BusinessSequence();
        seq.setCurrentValue(0L);

        when(businessSequenceRepository.findBySequenceKeyWithLock("PRODUCT")).thenReturn(Optional.of(seq));
        when(productRepository.existsByProductCode(any(String.class))).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.create(productRequest));
    }

    @Test
    @DisplayName("create - Mã sản phẩm thủ công trùng lặp thì ném BadRequestException")
    void create_WhenExplicitCodeExists_ShouldThrowBadRequestException() {
        ProductRequest requestWithCode = new ProductRequest(
                categoryId,
                "DUPLICATE-CODE",
                "Trà Shan Tuyết",
                null,
                null,
                Status.ACTIVE,
                null
        );
        when(productRepository.existsByProductCode("DUPLICATE-CODE")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> productService.create(requestWithCode));
    }

    @Test
    @DisplayName("update - Cập nhật sản phẩm thành công")
    void update_WhenValid_ShouldUpdateAndReturnResponse() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(mapper.toResponse(any(Product.class))).thenReturn(productResponse);

        ProductResponse result = productService.update(productId, productRequest);

        assertNotNull(result);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("updateStatus - Cập nhật trạng thái sản phẩm thành công")
    void updateStatus_WhenExists_ShouldUpdateStatus() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(mapper.toResponse(any(Product.class))).thenReturn(productResponse);

        ProductResponse result = productService.updateStatus(productId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, product.getStatus());
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("findById - Tìm sản phẩm theo ID thành công")
    void findById_WhenExists_ShouldReturnResponse() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductIdsWithDetails(any())).thenReturn(Collections.emptyList());
        when(mapper.toResponse(product, Collections.emptyList())).thenReturn(productResponse);

        ProductResponse result = productService.findById(productId);

        assertNotNull(result);
        assertEquals(productId, result.id());
    }

    @Test
    @DisplayName("findById - Không tìm thấy sản phẩm thì ném ResourceNotFoundException")
    void findById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.findById(nonExistentId));
    }

    @Test
    @DisplayName("findAll - Tìm kiếm phân trang danh sách sản phẩm")
    void findAll_ShouldReturnPageResponse() {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(productVariantRepository.findByProductIdsWithDetails(any())).thenReturn(Collections.emptyList());
        when(mapper.toResponse(any(Product.class), any())).thenReturn(productResponse);

        PageResponse<ProductResponse> result = productService.findAll("Trà", categoryId, Status.ACTIVE, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    @DisplayName("lookup - Tìm kiếm dropdown sản phẩm theo từ khóa")
    void lookup_ShouldReturnLookupPage() {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByNameContainingIgnoreCaseAndIsDeletedFalse(any(), any())).thenReturn(page);

        PageResponse<LookupResponse> result = productService.lookup("Trà", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("Trà Shan Tuyết Cổ Thụ", result.content().get(0).name());
    }

    @Test
    @DisplayName("findAllByIds - Lấy danh sách sản phẩm theo danh sách IDs")
    void findAllByIds_ShouldReturnList() {
        when(productRepository.findAllByIdWithCategory(List.of(productId))).thenReturn(List.of(product));
        when(productVariantRepository.findByProductIdsWithDetails(any())).thenReturn(Collections.emptyList());
        when(mapper.toResponse(product, Collections.emptyList())).thenReturn(productResponse);

        List<ProductResponse> result = productService.findAllByIds(List.of(productId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("delete - Xóa mềm sản phẩm thành công")
    void delete_WhenExists_ShouldSetDeletedTrue() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.delete(productId);

        assertTrue(product.getIsDeleted());
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("exportToExcel - Xuất danh sách sản phẩm ra byte[]")
    void exportToExcel_ShouldReturnByteArray() {
        when(productRepository.findAllWithCategory()).thenReturn(List.of(product));

        byte[] result = productService.exportToExcel();

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("getImportTemplate - Lấy file Excel mẫu import sản phẩm")
    void getImportTemplate_ShouldReturnByteArray() {
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        byte[] result = productService.getImportTemplate();

        assertNotNull(result);
        assertTrue(result.length > 0);
    }
}
