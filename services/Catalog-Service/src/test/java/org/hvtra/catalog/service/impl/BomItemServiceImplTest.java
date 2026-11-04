package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.BomItemRequest;
import org.hvtra.catalog.dto.response.BomItemResponse;
import org.hvtra.catalog.entity.BomItem;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.BomItemMapper;
import org.hvtra.catalog.repository.BomItemRepository;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BomItemServiceImpl Unit Tests")
class BomItemServiceImplTest {

    @Mock
    private BomItemRepository repository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private BomItemMapper mapper;

    @InjectMocks
    private BomItemServiceImpl bomItemService;

    private UUID variantId;
    private UUID ingredientId;
    private UUID unitId;
    private Ingredient ingredient;
    private Unit unit;
    private BomItem bomItem;
    private BomItemRequest bomItemRequest;
    private BomItemResponse bomItemResponse;

    @BeforeEach
    void setUp() {
        variantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        ingredient = new Ingredient();
        ingredient.setId(ingredientId);
        ingredient.setName("Búp Trà Tươi");
        ingredient.setIsDeleted(false);

        unit = new Unit();
        unit.setId(unitId);
        unit.setName("Gam");
        unit.setIsDeleted(false);

        bomItem = new BomItem();
        bomItem.setId(UUID.randomUUID());
        bomItem.setProductVariantId(variantId);
        bomItem.setIngredient(ingredient);
        bomItem.setUnit(unit);
        bomItem.setQuantity(new BigDecimal("10.00"));
        bomItem.setWasteRate(BigDecimal.ZERO);

        bomItemRequest = new BomItemRequest(
                ingredientId,
                new BigDecimal("10.00"),
                unitId,
                BigDecimal.ZERO
        );

        bomItemResponse = new BomItemResponse(
                bomItem.getId(),
                null,
                new BigDecimal("10.00"),
                null,
                BigDecimal.ZERO
        );
    }

    @Test
    @DisplayName("findByVariant - Lấy danh sách định mức nguyên liệu theo biến thể")
    void findByVariant_ShouldReturnList() {
        when(repository.findByProductVariantId(variantId)).thenReturn(List.of(bomItem));
        when(mapper.toResponse(bomItem)).thenReturn(bomItemResponse);

        List<BomItemResponse> result = bomItemService.findByVariant(variantId);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("replaceForVariant - Cập nhật định mức nguyên liệu thành công")
    void replaceForVariant_WhenValid_ShouldSaveAndReturnList() {
        when(productVariantRepository.existsById(variantId)).thenReturn(true);
        when(ingredientRepository.findAllById(List.of(ingredientId))).thenReturn(List.of(ingredient));
        when(unitRepository.findAllById(List.of(unitId))).thenReturn(List.of(unit));
        when(repository.saveAll(any())).thenReturn(List.of(bomItem));
        when(mapper.toResponse(bomItem)).thenReturn(bomItemResponse);

        List<BomItemResponse> result = bomItemService.replaceForVariant(variantId, List.of(bomItemRequest));

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(repository).deleteByProductVariantId(variantId);
        verify(repository).saveAll(any());
    }

    @Test
    @DisplayName("replaceForVariant - Biến thể không tồn tại thì ném ResourceNotFoundException")
    void replaceForVariant_WhenVariantNotFound_ShouldThrowResourceNotFoundException() {
        when(productVariantRepository.existsById(variantId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> bomItemService.replaceForVariant(variantId, List.of(bomItemRequest)));
    }

    @Test
    @DisplayName("replaceForVariant - Nguyên liệu không tồn tại thì ném ResourceNotFoundException")
    void replaceForVariant_WhenIngredientNotFound_ShouldThrowResourceNotFoundException() {
        when(productVariantRepository.existsById(variantId)).thenReturn(true);
        when(ingredientRepository.findAllById(List.of(ingredientId))).thenReturn(Collections.emptyList());
        when(unitRepository.findAllById(List.of(unitId))).thenReturn(List.of(unit));

        assertThrows(ResourceNotFoundException.class, () -> bomItemService.replaceForVariant(variantId, List.of(bomItemRequest)));
    }

    @Test
    @DisplayName("replaceForVariant - Nguyên liệu đã bị xóa thì ném BadRequestException")
    void replaceForVariant_WhenIngredientDeleted_ShouldThrowBadRequestException() {
        ingredient.setIsDeleted(true);
        when(productVariantRepository.existsById(variantId)).thenReturn(true);
        when(ingredientRepository.findAllById(List.of(ingredientId))).thenReturn(List.of(ingredient));
        when(unitRepository.findAllById(List.of(unitId))).thenReturn(List.of(unit));

        assertThrows(BadRequestException.class, () -> bomItemService.replaceForVariant(variantId, List.of(bomItemRequest)));
    }

    @Test
    @DisplayName("replaceForVariant - Đơn vị tính không tồn tại thì ném ResourceNotFoundException")
    void replaceForVariant_WhenUnitNotFound_ShouldThrowResourceNotFoundException() {
        when(productVariantRepository.existsById(variantId)).thenReturn(true);
        when(ingredientRepository.findAllById(List.of(ingredientId))).thenReturn(List.of(ingredient));
        when(unitRepository.findAllById(List.of(unitId))).thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () -> bomItemService.replaceForVariant(variantId, List.of(bomItemRequest)));
    }
}

