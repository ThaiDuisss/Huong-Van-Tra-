package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.request.SellableQuantityAdjustmentRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.SellableConfigRequest;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.SellableQuantityAdjustmentResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.SellableAdjustmentRepository;
import org.hvtra.catalog.entity.SellableAdjustment;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private ProductVariantServiceImpl service;

    @Test
    void decrementSellableQuantity_rejectsWhenInsufficient() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variantWithSellable(
                variantId,
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                new BigDecimal("10.00")
        );

        when(repository.findById(variantId)).thenReturn(Optional.of(variant));
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
        UUID variantId = UUID.randomUUID();
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
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "payment-success")
        );

        assertEquals(BigDecimal.ONE, response.oldSellableQuantity());
        assertEquals(BigDecimal.ZERO, response.newSellableQuantity());
        verify(repository).decrementSellableIfEnough(variantId, BigDecimal.ONE);
    }

    @Test
    void restoreSellableQuantity_addsBackExactlyOnceWhenCalledByCallerState() {
        UUID variantId = UUID.randomUUID();
        ProductVariant before = variantWithSellable(
                variantId,
                new BigDecimal("50.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00")
        );
        ProductVariant after = variantWithSellable(
                variantId,
                new BigDecimal("60.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00")
        );

        when(repository.findById(variantId)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.restoreSellable(eq(variantId), eq(new BigDecimal("10.00")))).thenReturn(1);

        SellableQuantityAdjustmentResponse response = service.restoreSellableQuantity(
                variantId,
                new SellableQuantityAdjustmentRequest(new BigDecimal("10.00"), UUID.randomUUID(), "inventory-compensation")
        );

        assertEquals(new BigDecimal("50.00"), response.oldSellableQuantity());
        assertEquals(new BigDecimal("60.00"), response.newSellableQuantity());
        verify(repository).restoreSellable(variantId, new BigDecimal("10.00"));
    }

    @Test
    void decrementSellableQuantity_flagsThresholdCrossingOnlyWhenOldAboveAndNewAtOrBelow() {
        UUID variantId = UUID.randomUUID();
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
    void duplicateAdjustmentKeyDoesNotDecrementTwice() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variantWithSellable(variantId, new BigDecimal("9"), BigDecimal.ZERO, BigDecimal.ONE);
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.of(variant));
        SellableAdjustment prior = new SellableAdjustment();
        prior.setQuantity(BigDecimal.ONE);
        prior.setOldQuantity(new BigDecimal("10"));
        prior.setNewQuantity(new BigDecimal("9"));
        when(adjustmentRepository.findByVariantIdAndOperationKey(variantId, "SALE:order:1"))
                .thenReturn(Optional.of(prior));

        SellableQuantityAdjustmentResponse response = service.decrementSellableQuantity(variantId,
                new SellableQuantityAdjustmentRequest(BigDecimal.ONE, null, "sale", "SALE:order:1"));

        assertEquals(new BigDecimal("10"), response.oldSellableQuantity());
        verify(repository, org.mockito.Mockito.never()).decrementSellableIfEnough(any(), any());
    }

    @Test
    void updateSellableConfig_locksRowAndOverwritesAllThreeValues() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variantWithSellable(
                variantId, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ONE);
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.of(variant));
        when(repository.save(variant)).thenReturn(variant);

        service.updateSellableConfig(variantId, new SellableConfigRequest(
                new BigDecimal("120.00"), new BigDecimal("30.00"), new BigDecimal("50.00")));

        assertEquals(new BigDecimal("120.00"), variant.getSellableQuantity());
        assertEquals(new BigDecimal("30.00"), variant.getProductionThreshold());
        assertEquals(new BigDecimal("50.00"), variant.getProductionBatchQuantity());
        verify(repository).findByIdForUpdate(variantId);
        verify(repository).save(variant);
    }

    @Test
    void updateSellableConfig_rejectsUnknownVariant() {
        UUID variantId = UUID.randomUUID();
        when(repository.findByIdForUpdate(variantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateSellableConfig(variantId,
                new SellableConfigRequest(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE)));
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    private ProductVariant variantWithSellable(
            UUID variantId,
            BigDecimal sellableQuantity,
            BigDecimal productionThreshold,
            BigDecimal productionBatchQuantity
    ) {
        Product product = new Product();
        product.setStatus(Status.ACTIVE);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setStatus(Status.ACTIVE);
        variant.setSellableQuantity(sellableQuantity);
        variant.setProductionThreshold(productionThreshold);
        variant.setProductionBatchQuantity(productionBatchQuantity);
        return variant;
    }
}
