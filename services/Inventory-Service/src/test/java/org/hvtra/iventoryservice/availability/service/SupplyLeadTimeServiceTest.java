package org.hvtra.iventoryservice.availability.service;

import org.hvtra.iventoryservice.availability.AvailabilityProperties;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.document.GoodsReceipt;
import org.hvtra.iventoryservice.domain.entity.document.ShelfReplenishment;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyLeadTimeServiceTest {

    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");

    @Mock
    private AvailabilityQueryRepository queries;
    @Mock
    private InventoryItemRepository itemRepository;

    private SupplyLeadTimeService service;

    @BeforeEach
    void setUp() {
        service = new SupplyLeadTimeService(queries, itemRepository, new AvailabilityProperties());
    }

    @Test
    void productionCompleted_storesAverageDurationOfRecentOrdersOnTheProductItem() {
        UUID variantId = UUID.randomUUID();
        InventoryItem product = new InventoryItem();
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(product));
        when(queries.findRecentCompletedByVariant(eq(variantId), any())).thenReturn(List.of(
                production(variantId, Duration.ofDays(2)), production(variantId, Duration.ofDays(4)),
                production(variantId, null)));

        service.onProductionCompleted(production(variantId, Duration.ofDays(2)));

        assertThat(product.getAvgSupplyLeadSeconds()).isEqualTo(Duration.ofDays(3).getSeconds());
        assertThat(product.getSupplyLeadSamples()).isEqualTo(2);
        verify(itemRepository).save(product);
    }

    @Test
    void receiptPosted_storesAverageLeadTimeOnEachMaterialOnce() {
        InventoryItem tea = new InventoryItem();
        tea.setId(UUID.randomUUID());
        InventoryDocument posted = receipt(Duration.ofDays(1), tea, tea);
        when(queries.findRecentPostedReceiptsByItem(eq(tea.getId()), any())).thenReturn(List.of(
                posted, receipt(Duration.ofDays(5), tea)));

        service.onReceiptPosted(posted);

        assertThat(tea.getAvgSupplyLeadSeconds()).isEqualTo(Duration.ofDays(3).getSeconds());
        assertThat(tea.getSupplyLeadSamples()).isEqualTo(2);
        verify(itemRepository).save(tea);
    }

    @Test
    void otherDocumentTypesAreIgnored() {
        InventoryDocument transfer = withLines(new ShelfReplenishment(), Duration.ofDays(1), new InventoryItem());

        service.onReceiptPosted(transfer);

        verifyNoInteractions(queries, itemRepository);
    }

    @Test
    void failureNeverBreaksTheCallingOperation() {
        UUID variantId = UUID.randomUUID();
        when(itemRepository.findByReferenceIdAndItemType(any(), any())).thenThrow(new IllegalStateException("db"));

        assertThatCode(() -> service.onProductionCompleted(production(variantId, Duration.ofDays(1))))
                .doesNotThrowAnyException();
    }

    private ProductionOrder production(UUID variantId, Duration took) {
        ProductionOrder order = new ProductionOrder();
        InventoryItem variant = new InventoryItem();
        variant.setReferenceId(variantId);
        order.setProductVariant(variant);
        order.setCreatedAt(START);
        order.setApprovedAt(took == null ? null : START.plus(took));
        return order;
    }

    private InventoryDocument receipt(Duration took, InventoryItem... items) {
        return withLines(new GoodsReceipt(), took, items);
    }

    private InventoryDocument withLines(InventoryDocument document, Duration took, InventoryItem... items) {
        document.setCreatedAt(START);
        document.setPostedAt(START.plus(took));
        for (InventoryItem item : items) {
            InventoryDocumentLine line = new InventoryDocumentLine();
            line.setDocument(document);
            line.setInventoryItem(item);
            document.getLines().add(line);
        }
        return document;
    }
}
