package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.CategoryRequest;
import org.hvtra.catalog.dto.response.CategoryResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.CategoryMapper;
import org.hvtra.catalog.repository.CategoryRepository;
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
@DisplayName("CategoryServiceImpl Unit Tests")
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository repository;

    @Mock
    private CategoryMapper mapper;

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private UUID categoryId;
    private Category category;
    private CategoryRequest categoryRequest;
    private CategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        category = new Category();
        category.setId(categoryId);
        category.setCategoryCode("CAT-01");
        category.setName("Trà Búp Truyền Thống");
        category.setStatus(Status.ACTIVE);
        category.setIsDeleted(false);

        categoryRequest = new CategoryRequest(
                null,
                "CAT-01",
                "Trà Búp Truyền Thống",
                Status.ACTIVE,
                10
        );

        categoryResponse = new CategoryResponse(
                categoryId,
                null,
                "Trà Búp Truyền Thống",
                Status.ACTIVE,
                "CAT-01",
                10,
                null
        );
    }

    @Test
    @DisplayName("create - Tạo danh mục với mã chỉ định thành công")
    void create_WhenValid_ShouldReturnResponse() {
        when(repository.existsByCategoryCode("CAT-01")).thenReturn(false);
        when(repository.save(any(Category.class))).thenReturn(category);
        when(mapper.toResponse(any(Category.class))).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.create(categoryRequest);

        assertNotNull(result);
        assertEquals("CAT-01", result.categoryCode());
        assertEquals("Trà Búp Truyền Thống", result.name());
        verify(repository).save(any(Category.class));
    }

    @Test
    @DisplayName("create - Mã danh mục tự sinh khi để trống")
    void create_WhenCodeBlank_ShouldGenerateCode() {
        CategoryRequest reqBlankCode = new CategoryRequest(null, null, "Trà Búp", Status.ACTIVE, 5);
        BusinessSequence seq = new BusinessSequence();
        seq.setCurrentValue(0L);

        when(businessSequenceRepository.findBySequenceKeyWithLock("CATEGORY")).thenReturn(Optional.of(seq));
        when(repository.existsByCategoryCode(any())).thenReturn(false);
        when(repository.save(any(Category.class))).thenReturn(category);
        when(mapper.toResponse(any(Category.class))).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.create(reqBlankCode);

        assertNotNull(result);
    }

    @Test
    @DisplayName("create - Mã danh mục đã tồn tại thì ném BadRequestException")
    void create_WhenDuplicateCode_ShouldThrowBadRequestException() {
        when(repository.existsByCategoryCode("CAT-01")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> categoryService.create(categoryRequest));
    }

    @Test
    @DisplayName("update - Cập nhật danh mục thành công")
    void update_WhenValid_ShouldUpdateAndReturnResponse() {
        when(repository.findById(categoryId)).thenReturn(Optional.of(category));
        when(repository.save(any(Category.class))).thenReturn(category);
        when(mapper.toResponse(any(Category.class))).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.update(categoryId, categoryRequest);

        assertNotNull(result);
        verify(repository).save(category);
    }

    @Test
    @DisplayName("update - Danh mục cha là chính nó thì ném BadRequestException")
    void update_WhenParentIsSelf_ShouldThrowBadRequestException() {
        CategoryRequest reqSelfParent = new CategoryRequest(categoryId, "CAT-01", "Trà", Status.ACTIVE, 5);
        when(repository.findById(categoryId)).thenReturn(Optional.of(category));

        assertThrows(BadRequestException.class, () -> categoryService.update(categoryId, reqSelfParent));
    }

    @Test
    @DisplayName("updateStatus - Cập nhật trạng thái danh mục thành công")
    void updateStatus_WhenExists_ShouldUpdateStatus() {
        when(repository.findById(categoryId)).thenReturn(Optional.of(category));
        when(repository.save(any(Category.class))).thenReturn(category);
        when(mapper.toResponse(any(Category.class))).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.updateStatus(categoryId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, category.getStatus());
        verify(repository).save(category);
    }

    @Test
    @DisplayName("findById - Tìm danh mục theo ID thành công")
    void findById_WhenExists_ShouldReturnResponse() {
        when(repository.findById(categoryId)).thenReturn(Optional.of(category));
        when(mapper.toResponseWithParentName(category)).thenReturn(categoryResponse);

        CategoryResponse result = categoryService.findById(categoryId);

        assertNotNull(result);
        assertEquals(categoryId, result.id());
    }

    @Test
    @DisplayName("findById - Không tìm thấy danh mục thì ném ResourceNotFoundException")
    void findById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.findById(nonExistentId));
    }

    @Test
    @DisplayName("findAll - Tìm kiếm phân trang danh mục")
    void findAll_ShouldReturnPageResponse() {
        Page<Category> page = new PageImpl<>(List.of(category));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponseWithParentName(category)).thenReturn(categoryResponse);

        PageResponse<CategoryResponse> result = categoryService.findAll("Trà", Status.ACTIVE, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    @DisplayName("findAllByIds - Tìm danh mục theo danh sách IDs")
    void findAllByIds_ShouldReturnList() {
        when(repository.findAllById(List.of(categoryId))).thenReturn(List.of(category));
        when(mapper.toResponse(category)).thenReturn(categoryResponse);

        List<CategoryResponse> result = categoryService.findAllByIds(List.of(categoryId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("lookup - Tìm kiếm dropdown danh mục")
    void lookup_ShouldReturnLookupPage() {
        when(repository.findLookup(any(), any())).thenReturn(Page.empty());

        PageResponse<LookupResponse> result = categoryService.lookup("Trà", null, PageRequest.of(0, 10));

        assertNotNull(result);
    }

    @Test
    @DisplayName("delete - Xóa mềm danh mục thành công")
    void delete_WhenExists_ShouldSetDeletedTrue() {
        when(repository.findById(categoryId)).thenReturn(Optional.of(category));

        categoryService.delete(categoryId);

        assertTrue(category.getIsDeleted());
        verify(repository).save(category);
    }
}

