package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.client.InventoryClient;
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
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.SellableAdjustment;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.SellableAdjustmentRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductVariantServiceImpl Unit Tests")
class ProductVariantServiceImplTest {

    @Mock
    private ProductVariantRepository repository;

    @Mock
    private SellableAdjustmentRepository adjustmentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper mapper;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private ProductVariantServiceImpl service;

    private UUID variantId;
    private UUID productId;
    private UUID unitId;
    private Product product;
    private Unit unit;
    private ProductVariant variant;
    private ProductResponse.VariantResponse variantResponse;
    private ProductResponse.VariantSummaryResponse variantSummaryResponse;

    @BeforeEach
    void setUp() {
        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        Category category = new Category();
        category.setName("Trà");
        category.setDefaultReorderPoint(15);

        product = new Product();
        product.setId(productId);
        product.setName("Trà Ô Long Đặc Biệt");
        product.setProductCode("TRA001");
        product.setStatus(Status.ACTIVE);
        product.setCategory(category);

        unit = new Unit();
        unit.setId(unitId);
        unit.setName("Hộp");
        unit.setSymbol("hop");

        variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setUnit(unit);
        variant.setSku("TRA-HOP-00001");
        variant.setVariantName("Trà Ô Long Đặc Biệt 100 hop");
        variant.setBarcode("8930000000018");
        variant.setQuantity(new BigDecimal("100"));
        variant.setCostPrice(new BigDecimal("50000"));
        variant.setSellingPrice(new BigDecimal("80000"));
        variant.setSellableQuantity(new BigDecimal("20"));
        variant.setProductionThreshold(new BigDecimal("5"));
        variant.setProductionBatchQuantity(new BigDecimal("10"));
        variant.setStatus(Status.ACTIVE);
        variant.setIsDeleted(false);
        variant.setCustomBundle(false);

        variantResponse = new ProductResponse.VariantResponse(
                variantId,
                productId,
                product.getName(),
                product.getProductCode(),
                variant.getSku(),
                variant.getVariantName(),
                variant.getQuantity(),
                "Trà",
                variant.getSellingPrice(),
                variant.getCostPrice(),
                variant.getSellableQuantity(),
                variant.getProductionThreshold(),
                variant.getProductionBatchQuantity(),
                null,
                unitId,
                unit.getName(),
                unit.getSymbol(),
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                Status.ACTIVE,
                true,
                variant.getBarcode()
        );

        variantSummaryResponse = new ProductResponse.VariantSummaryResponse(
                variantId,
                productId,
                product.getName(),
                product.getProductCode(),
                variant.getSku(),
                variant.getVariantName(),
                variant.getQuantity(),
                "Trà",
                variant.getSellingPrice(),
                variant.getCostPrice(),
                variant.getSellableQuantity(),
                variant.getProductionThreshold(),
                variant.getProductionBatchQuantity(),
                null,
                unitId,
                unit.getName(),
                unit.getSymbol(),
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                Status.ACTIVE,
                true,
                variant.getBarcode()
        );
    }

    @Test
    void decrementSellableQuantity_rejectsWhenInsufficient() {
        ProductVariant v = variantWithSellable(
                variantId,
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                new BigDecimal("10.00")
        );

        when(repository.findById(variantId)).thenReturn(Optional.of(v));
        when(repository.decrementSellableIfEnough(eq(variantId), eq(new BigDecimal("6.00")))).thenReturn(0);

        SellableQuantityAdjustmentRequest request = new SellableQuantityAdjustmentRequest(
                new BigDecimal("6.00"),
                UUID.randomUUID(),
                "test"
        );

        assertThrows(IllegalArgumentException.class, () -> service.decrementSellableQuantity(variantId, request));
    }

    @Test
    void decrementSellableQuantity_isAtomicUnderConcurrentRequests() {
        ProductVariant before = variantWithSellable(
                variantId,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE
        );
        ProductVariant after = variantWithSellable(
                variantId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE
        );

        when(repository.findById(variantId)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.decrementSellableIfEnough(eq(variantId), eq(BigDecimal.ONE))).thenReturn(1);

        SellableQuantityAdjustmentResponse response = service.decrementSellableQuantity(
                variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, UUID.randomUUID(), "test")
        );

        assertEquals(new BigDecimal("1"), response.oldSellableQuantity());
        assertEquals(BigDecimal.ZERO, response.newSellableQuantity());
        assertEquals(true, response.thresholdCrossed());
    }

    @Test
    void decrementSellableQuantity_crossedProductionThreshold_flagsEvent() {
        ProductVariant before = variantWithSellable(
                variantId,
                new BigDecimal("31.00"),
                new BigDecimal("30.00"),
                new BigDecimal("50.00")
        );
        ProductVariant after = variantWithSellable(
                variantId,
                new BigDecimal("30.00"),
                new BigDecimal("30.00"),
                new BigDecimal("50.00")
        );

        when(repository.findById(variantId)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.decrementSellableIfEnough(eq(variantId), eq(BigDecimal.ONE))).thenReturn(1);

        SellableQuantityAdjustmentResponse response = service.decrementSellableQuantity(
                variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, UUID.randomUUID(), "payment-success")
        );

        assertEquals(true, response.thresholdCrossed());
        assertEquals(new BigDecimal("30.00"), response.productionThreshold());
        assertEquals(new BigDecimal("50.00"), response.productionBatchQuantity());
    }

    @Test
    void decrementSellableQuantity_duplicateAdjustmentKeyDoesNotDecrementTwice() {
        ProductVariant v = variantWithSellable(variantId, new BigDecimal("9"), BigDecimal.ZERO, BigDecimal.ONE);
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.of(v));
        SellableAdjustment prior = new SellableAdjustment();
        prior.setQuantity(BigDecimal.ONE);
        prior.setOldQuantity(new BigDecimal("10"));
        prior.setNewQuantity(new BigDecimal("9"));
        when(adjustmentRepository.findByVariantIdAndOperationKey(variantId, "SALE:order:1"))
                .thenReturn(Optional.of(prior));

        SellableQuantityAdjustmentResponse response = service.decrementSellableQuantity(variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "sale", "SALE:order:1"));

        assertEquals(new BigDecimal("10"), response.oldSellableQuantity());
        verify(repository, never()).decrementSellableIfEnough(any(), any());
    }

    @Test
    void decrementSellableQuantity_WhenCustomBundle_ShouldNotDecrementSellable() {
        variant.setCustomBundle(true);
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        SellableQuantityAdjustmentResponse response = service.decrementSellableQuantity(variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "bundle-sale"));

        assertNotNull(response);
        verify(repository, never()).decrementSellableIfEnough(any(), any());
    }

    @Test
    void restoreSellableQuantity_WhenExists_ShouldRestore() {
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));
        when(repository.restoreSellable(eq(variantId), eq(BigDecimal.ONE))).thenReturn(1);

        SellableQuantityAdjustmentResponse response = service.restoreSellableQuantity(variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "cancel-order"));

        assertNotNull(response);
        verify(repository).restoreSellable(variantId, BigDecimal.ONE);
    }

    @Test
    void restoreSellableQuantity_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));
        when(repository.restoreSellable(eq(variantId), eq(BigDecimal.ONE))).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> service.restoreSellableQuantity(variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "cancel-order")));
    }

    @Test
    void restoreSellableQuantity_WhenCustomBundle_ShouldReturnWithoutRestore() {
        variant.setCustomBundle(true);
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        SellableQuantityAdjustmentResponse response = service.restoreSellableQuantity(variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "bundle-cancel"));

        assertNotNull(response);
        verify(repository, never()).restoreSellable(any(), any());
    }

    @Test
    void updateSellableConfig_locksRowAndOverwritesAllThreeValues() {
        ProductVariant v = variantWithSellable(
                variantId, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ONE);
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.of(v));
        when(repository.save(v)).thenReturn(v);

        service.updateSellableConfig(variantId, new SellableConfigRequest(
                new BigDecimal("120.00"), new BigDecimal("30.00"), new BigDecimal("50.00")));

        assertEquals(new BigDecimal("120.00"), v.getSellableQuantity());
        assertEquals(new BigDecimal("30.00"), v.getProductionThreshold());
        assertEquals(new BigDecimal("50.00"), v.getProductionBatchQuantity());
        verify(repository).findByIdForUpdate(variantId);
        verify(repository).save(v);
    }

    @Test
    void updateSellableConfig_rejectsUnknownVariant() {
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateSellableConfig(variantId,
                new SellableConfigRequest(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE)));
        verify(repository, never()).save(any());
    }

    @Test
    void updateSellableConfig_WhenCustomBundle_ShouldThrowBadRequestException() {
        variant.setCustomBundle(true);
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.of(variant));

        assertThrows(BadRequestException.class, () -> service.updateSellableConfig(variantId,
                new SellableConfigRequest(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE)));
    }

    @Test
    void findAll_ShouldReturnPagedResponse() {
        Page<ProductVariant> page = new PageImpl<>(List.of(variant), PageRequest.of(0, 10), 1);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toVariantSummary(variant)).thenReturn(variantSummaryResponse);

        PageResponse<ProductResponse.VariantSummaryResponse> result = service.findAll(
                "Trà", "TRA", "100 hop", "893", productId, UUID.randomUUID(), Status.ACTIVE, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
    }

    @Test
    void lookup_ShouldReturnPagedLookupResponse() {
        Page<ProductVariant> page = new PageImpl<>(List.of(variant), PageRequest.of(0, 10), 1);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<LookupResponse> result = service.lookup("Trà", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals(variant.getVariantName(), result.content().get(0).name());
    }

    @Test
    void findById_WhenExists_ShouldReturnResponse() {
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));
        when(mapper.toVariantResponse(variant)).thenReturn(variantResponse);

        ProductResponse.VariantResponse result = service.findById(variantId);

        assertNotNull(result);
        assertEquals(variantId, result.id());
    }

    @Test
    void findById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(repository.findById(variantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(variantId));
    }

    @Test
    void findByBarcode_WhenExists_ShouldReturnResponse() {
        when(repository.findByBarcodeAndIsDeletedFalse("8930000000018")).thenReturn(Optional.of(variant));
        when(mapper.toVariantResponse(variant)).thenReturn(variantResponse);

        ProductResponse.VariantResponse result = service.findByBarcode("8930000000018");

        assertNotNull(result);
        assertEquals("8930000000018", result.barcode());
    }

    @Test
    void findByBarcode_WhenBlank_ShouldThrowBadRequestException() {
        assertThrows(BadRequestException.class, () -> service.findByBarcode("   "));
    }

    @Test
    void findByBarcode_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(repository.findByBarcodeAndIsDeletedFalse("8939999999999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findByBarcode("8939999999999"));
    }

    @Test
    void findCustomBundleVariant_WhenExists_ShouldReturnResponse() {
        when(repository.findFirstByCustomBundleTrueAndIsDeletedFalse()).thenReturn(Optional.of(variant));
        when(mapper.toVariantResponse(variant)).thenReturn(variantResponse);

        ProductResponse.VariantResponse result = service.findCustomBundleVariant();

        assertNotNull(result);
        assertEquals(variantId, result.id());
    }

    @Test
    void findCustomBundleVariant_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(repository.findFirstByCustomBundleTrueAndIsDeletedFalse()).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findCustomBundleVariant());
    }

    @Test
    void generateBarcode_ShouldReturnBarcodeString() {
        BusinessSequence seq = new BusinessSequence();
        seq.setCurrentValue(1L);
        when(businessSequenceRepository.findBySequenceKeyWithLock("BARCODE")).thenReturn(Optional.of(seq));
        when(repository.existsByBarcode(any())).thenReturn(false);

        String barcode = service.generateBarcode();

        assertNotNull(barcode);
        assertTrue(barcode.startsWith("893"));
    }

    @Test
    void create_ShouldSaveAndReturnResponse() {
        ProductVariantRequest request = new ProductVariantRequest(
                new BigDecimal("50"),
                new BigDecimal("100000"),
                new BigDecimal("60000"),
                unitId,
                10,
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                null
        );

        BusinessSequence skuSeq = new BusinessSequence();
        skuSeq.setCurrentValue(5L);
        BusinessSequence barcodeSeq = new BusinessSequence();
        barcodeSeq.setCurrentValue(5L);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(businessSequenceRepository.findBySequenceKeyWithLock("SKU")).thenReturn(Optional.of(skuSeq));
        when(businessSequenceRepository.findBySequenceKeyWithLock("BARCODE")).thenReturn(Optional.of(barcodeSeq));
        when(repository.existsBySku(any())).thenReturn(false);
        when(repository.existsByBarcode(any())).thenReturn(false);
        when(repository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);
        when(mapper.toVariantResponseForNewlyCreated(variant)).thenReturn(variantResponse);

        ProductResponse.VariantResponse result = service.create(productId, request);

        assertNotNull(result);
        verify(repository).saveAndFlush(any(ProductVariant.class));
    }

    @Test
    void update_WhenExists_ShouldUpdateAndReturnResponse() {
        ProductVariantRequest request = new ProductVariantRequest(
                new BigDecimal("100"),
                new BigDecimal("90000"),
                new BigDecimal("55000"),
                unitId,
                15,
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                "8930000000018"
        );

        when(repository.findById(variantId)).thenReturn(Optional.of(variant));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(repository.existsByBarcodeAndIdNot(eq("8930000000018"), eq(variantId))).thenReturn(false);
        when(repository.save(variant)).thenReturn(variant);
        when(mapper.toVariantResponse(variant)).thenReturn(variantResponse);

        ProductResponse.VariantResponse result = service.update(variantId, request);

        assertNotNull(result);
        verify(repository).save(variant);
    }

    @Test
    void update_WhenCustomBundle_ShouldThrowBadRequestException() {
        variant.setCustomBundle(true);
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        ProductVariantRequest request = new ProductVariantRequest(
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, unitId, 5,
                InventoryPolicy.TRACK_STOCK, Status.ACTIVE, null
        );

        assertThrows(BadRequestException.class, () -> service.update(variantId, request));
    }

    @Test
    void updateStatus_WhenExists_ShouldUpdateStatus() {
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));
        when(repository.save(variant)).thenReturn(variant);
        when(mapper.toVariantResponse(variant)).thenReturn(variantResponse);

        ProductResponse.VariantResponse result = service.updateStatus(variantId, Status.INACTIVE);

        assertNotNull(result);
        assertEquals(Status.INACTIVE, variant.getStatus());
        verify(repository).save(variant);
    }

    @Test
    void updateStatus_WhenCustomBundle_ShouldThrowBadRequestException() {
        variant.setCustomBundle(true);
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThrows(BadRequestException.class, () -> service.updateStatus(variantId, Status.INACTIVE));
    }

    @Test
    void exportToExcel_ShouldReturnByteArray() {
        when(repository.findAllForExport(productId)).thenReturn(List.of(variant));

        byte[] result = service.exportToExcel(productId);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void getImportTemplate_ShouldReturnByteArray() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(ingredientRepository.findAll()).thenReturn(List.of());
        when(unitRepository.findAll()).thenReturn(List.of(unit));

        byte[] result = service.getImportTemplate();

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void findAllByIds_ShouldReturnList() {
        when(repository.findAllByIdWithDetails(List.of(variantId))).thenReturn(List.of(variant));
        when(mapper.toVariantResponse(variant)).thenReturn(variantResponse);

        List<ProductResponse.VariantResponse> result = service.findAllByIds(List.of(variantId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void searchVariantIds_ShouldReturnIdList() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(variant));

        List<UUID> result = service.searchVariantIds(
                "Trà", "TRA", "TRA001", "Trà", "CAT01", UUID.randomUUID(), unitId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(variantId, result.get(0));
    }

    @Test
    void findRefsByProductIds_ShouldReturnRefList() {
        when(repository.findByProductIds(List.of(productId))).thenReturn(List.of(variant));

        List<ProductVariantProductRefResponse> result = service.findRefsByProductIds(List.of(productId));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(variantId, result.get(0).id());
        assertEquals(productId, result.get(0).productId());
    }

    @Test
    void findCostPricesByIds_ShouldReturnList() {
        VariantCostResponse costResponse = new VariantCostResponse(variantId, new BigDecimal("50000"));
        when(repository.findCostPricesByIds(List.of(variantId))).thenReturn(List.of(costResponse));

        List<VariantCostResponse> result = service.findCostPricesByIds(List.of(variantId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void findInventorySummariesByIds_ShouldReturnList() {
        VariantInventorySummaryResponse summaryResponse = new VariantInventorySummaryResponse(
                variantId,
                variant.getSku(),
                variant.getVariantName(),
                productId,
                product.getProductCode(),
                product.getName(),
                UUID.randomUUID(),
                "CAT01",
                "Trà",
                unitId,
                unit.getName(),
                unit.getSymbol(),
                variant.getCostPrice(),
                variant.getSellingPrice(),
                null
        );
        when(repository.findInventorySummariesByIds(List.of(variantId))).thenReturn(List.of(summaryResponse));

        List<VariantInventorySummaryResponse> result = service.findInventorySummariesByIds(List.of(variantId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void delete_WhenExists_ShouldSetDeletedTrue() {
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        service.delete(variantId);

        assertTrue(variant.getIsDeleted());
        verify(repository).save(variant);
    }

    @Test
    void delete_WhenAlreadyDeleted_ShouldThrowResourceNotFoundException() {
        variant.setIsDeleted(true);
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThrows(ResourceNotFoundException.class, () -> service.delete(variantId));
    }

    @Test
    void delete_WhenCustomBundle_ShouldThrowBadRequestException() {
        variant.setCustomBundle(true);
        when(repository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThrows(BadRequestException.class, () -> service.delete(variantId));
    }

    private ProductVariant variantWithSellable(
            UUID id,
            BigDecimal sellableQuantity,
            BigDecimal productionThreshold,
            BigDecimal productionBatchQuantity
    ) {
        Product p = new Product();
        p.setStatus(Status.ACTIVE);

        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setProduct(p);
        v.setStatus(Status.ACTIVE);
        v.setSellableQuantity(sellableQuantity);
        v.setProductionThreshold(productionThreshold);
        v.setProductionBatchQuantity(productionBatchQuantity);
        return v;
    }
}
