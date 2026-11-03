package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientBundleLookupResponse;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.IngredientMapper;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.UnitRepository;
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

import java.math.BigDecimal;
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
@DisplayName("IngredientServiceImpl Unit Tests")
class IngredientServiceImplTest {

    @Mock
    private IngredientRepository repository;

    @Mock
    private IngredientMapper mapper;

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private IngredientServiceImpl ingredientService;

    private UUID ingredientId;
    private UUID unitId;
    private Unit unit;
    private Ingredient ingredient;
    private IngredientRequest ingredientRequest;
    private IngredientResponse ingredientResponse;

    @BeforeEach
    void setUp() {
        ingredientId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        unit = new Unit();
        unit.setId(unitId);
        unit.setName("Gam");
        unit.setSymbol("G");
        unit.setIsDeleted(false);

        ingredient = new Ingredient();
        ingredient.setId(ingredientId);
        ingredient.setIngredientCode("NL-01");
        ingredient.setName("Búp Trà Tươi Tân Cương");
        ingredient.setUnit(unit);
        ingredient.setStatus(Status.ACTIVE);
        ingredient.setIsDeleted(false);

        ingredientRequest = new IngredientRequest(
                "Búp Trà Tươi Tân Cương",
                "http://image.url",
                unitId,
                10,
                1,
                Status.ACTIVE,
                new BigDecimal("500.00")
        );

        ingredientResponse = new IngredientResponse(
                ingredientId,
                "NL-01",
                "Búp Trà Tươi Tân Cương",
                "http://image.url",
                unitId,
                "Gam",
                "G",
                10,
                1,
                Status.ACTIVE,
                new BigDecimal("500.00")
        );
    }

    @Test
    @DisplayName("create - Tạo nguyên liệu tự sinh mã thành công")
    void create_WhenValid_ShouldGenerateCodeAndReturnResponse() {
        BusinessSequence seq = new BusinessSequence();
        seq.setCurrentValue(0L);

        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(businessSequenceRepository.findBySequenceKeyWithLock("INGREDIENT")).thenReturn(Optional.of(seq));
        when(repository.existsByIngredientCode(any(String.class))).thenReturn(false);
        when(repository.saveAndFlush(any(Ingredient.class))).thenReturn(ingredient);
        when(mapper.toResponse(any(Ingredient.class))).thenReturn(ingredientResponse);

        IngredientResponse result = ingredientService.create(ingredientRequest);

        assertNotNull(result);
        assertEquals("NL-01", result.ingredientCode());
    }

    @Test
    @DisplayName("createWithCode - Tạo nguyên liệu với mã rõ ràng thành công")
    void createWithCode_WhenValid_ShouldReturnResponse() {
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(repository.existsByIngredientCode("NL-01")).thenReturn(false);
        when(repository.saveAndFlush(any(Ingredient.class))).thenReturn(ingredient);
        when(mapper.toResponse(any(Ingredient.class))).thenReturn(ingredientResponse);

        IngredientResponse result = ingredientService.createWithCode(ingredientRequest, "NL-01");

        assertNotNull(result);
        assertEquals("NL-01", result.ingredientCode());
        assertEquals("Búp Trà Tươi Tân Cương", result.name());
        verify(repository).saveAndFlush(any(Ingredient.class));
        verify(inventoryClient).createProductVariantItem(any());
    }

    @Test
    @DisplayName("createWithCode - Đơn vị tính không tồn tại thì ném ResourceNotFoundException")
    void createWithCode_WhenUnitNotFound_ShouldThrowResourceNotFoundException() {
        when(unitRepository.findById(unitId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ingredientService.createWithCode(ingredientRequest, "NL-01"));
    }

    @Test
    @DisplayName("createWithCode - Mã nguyên liệu đã tồn tại thì ném BadRequestException")
    void createWithCode_WhenDuplicateCode_ShouldThrowBadRequestException() {
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(repository.existsByIngredientCode("NL-01")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> ingredientService.createWithCode(ingredientRequest, "NL-01"));
    }

    @Test
    @DisplayName("update - Cập nhật thông tin nguyên liệu thành công")
    void update_WhenValid_ShouldUpdateAndReturnResponse() {
        when(repository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(repository.save(any(Ingredient.class))).thenReturn(ingredient);
        when(mapper.toResponse(any(Ingredient.class))).thenReturn(ingredientResponse);

        IngredientResponse result = ingredientService.update(ingredientId, ingredientRequest);

        assertNotNull(result);
        verify(repository).save(ingredient);
    }

    @Test
    @DisplayName("updateStatus - Cập nhật trạng thái nguyên liệu thành công")
    void updateStatus_WhenExists_ShouldUpdateStatus() {
        when(repository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
        when(repository.save(any(Ingredient.class))).thenReturn(ingredient);
        when(mapper.toResponse(any(Ingredient.class))).thenReturn(ingredientResponse);

        IngredientResponse result = ingredientService.updateStatus(ingredientId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, ingredient.getStatus());
        verify(repository).save(ingredient);
    }

    @Test
    @DisplayName("findById - Tìm nguyên liệu theo ID thành công")
    void findById_WhenExists_ShouldReturnResponse() {
        when(repository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
        when(mapper.toResponse(ingredient)).thenReturn(ingredientResponse);

        IngredientResponse result = ingredientService.findById(ingredientId);

        assertNotNull(result);
        assertEquals(ingredientId, result.id());
    }

    @Test
    @DisplayName("findById - Nguyên liệu đã bị xóa thì ném ResourceNotFoundException")
    void findById_WhenDeleted_ShouldThrowResourceNotFoundException() {
        ingredient.setIsDeleted(true);
        when(repository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

        assertThrows(ResourceNotFoundException.class, () -> ingredientService.findById(ingredientId));
    }

    @Test
    @DisplayName("findAll - Tìm kiếm phân trang danh sách nguyên liệu")
    void findAll_ShouldReturnPageResponse() {
        Page<Ingredient> page = new PageImpl<>(List.of(ingredient));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(ingredient)).thenReturn(ingredientResponse);

        PageResponse<IngredientResponse> result = ingredientService.findAll("Trà", Status.ACTIVE, PageRequest.of(0, 10), null);

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    @DisplayName("findByIds - Lấy danh sách nguyên liệu theo IDs")
    void findByIds_ShouldReturnList() {
        when(repository.findAllByIdWithUnit(List.of(ingredientId))).thenReturn(List.of(ingredient));
        when(mapper.toResponse(ingredient)).thenReturn(ingredientResponse);

        List<IngredientResponse> result = ingredientService.findByIds(List.of(ingredientId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("bundleLookup - Tìm kiếm nguyên liệu dùng cho bundle")
    void bundleLookup_ShouldReturnPage() {
        when(repository.findBundleLookup(any(), any())).thenReturn(Page.empty());

        PageResponse<IngredientBundleLookupResponse> result = ingredientService.bundleLookup("Trà", PageRequest.of(0, 10));

        assertNotNull(result);
    }

    @Test
    @DisplayName("lookup - Tìm kiếm dropdown nguyên liệu")
    void lookup_ShouldReturnLookupPage() {
        when(repository.findLookup(any(), any(), any())).thenReturn(Page.empty());

        PageResponse<LookupResponse> result = ingredientService.lookup("Trà", Status.ACTIVE, null, PageRequest.of(0, 10));

        assertNotNull(result);
    }

    @Test
    @DisplayName("searchIngredientIds - Tìm kiếm danh sách IDs nguyên liệu theo bộ lọc")
    void searchIngredientIds_ShouldReturnIdList() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(ingredient));

        List<UUID> result = ingredientService.searchIngredientIds("Trà", "NL", "Trà", unitId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ingredientId, result.get(0));
    }

    @Test
    @DisplayName("delete - Xóa mềm nguyên liệu thành công")
    void delete_WhenExists_ShouldSetDeletedTrue() {
        when(repository.findById(ingredientId)).thenReturn(Optional.of(ingredient));

        ingredientService.delete(ingredientId);

        assertTrue(ingredient.getIsDeleted());
        verify(repository).save(ingredient);
    }

    @Test
    @DisplayName("exportToExcel - Xuất danh sách nguyên liệu ra byte[]")
    void exportToExcel_ShouldReturnByteArray() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(ingredient));

        byte[] result = ingredientService.exportToExcel("Trà", Status.ACTIVE);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("getImportTemplate - Lấy file Excel mẫu import nguyên liệu")
    void getImportTemplate_ShouldReturnByteArray() {
        when(unitRepository.findAll()).thenReturn(List.of(unit));

        byte[] result = ingredientService.getImportTemplate();

        assertNotNull(result);
        assertTrue(result.length > 0);
    }
}
