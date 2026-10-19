package org.hvtra.orderservice.service.support;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CustomBundleCatalogClient;
import org.hvtra.orderservice.client.CustomBundleInventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderItemComponent;
import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.dto.request.CustomBundleLineRequest;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phần dùng chung của dòng "Trà đóng theo yêu cầu" (CUSTOM_BUNDLE): định giá theo nguyên liệu, kiểm tra giới hạn
 * bundle, gửi/đồng bộ yêu cầu đóng gói bên Inventory. Dòng bundle không đi qua các bước trừ tồn theo
 * variant/BOM của đơn thường; nguyên liệu được giữ chỗ và trừ khi kho xác nhận đóng gói.
 */
@Component
@Slf4j
public class OrderCustomBundleSupport {

    private static final String ACTIVE = "ACTIVE";
    private static final int RECIPE_INGREDIENT = 1;

    private final CustomBundleCatalogClient catalogClient;
    private final CustomBundleInventoryClient inventoryClient;
    private final BigDecimal minTotalQuantity;
    private final BigDecimal maxTotalQuantity;
    private final BigDecimal minLineQuantity;
    private final int maxIngredients;
    private final BigDecimal packagingFee;

    public OrderCustomBundleSupport(
            CustomBundleCatalogClient catalogClient,
            CustomBundleInventoryClient inventoryClient,
            @Value("${order.custom-bundle.min-total-quantity:50}") BigDecimal minTotalQuantity,
            @Value("${order.custom-bundle.max-total-quantity:1000}") BigDecimal maxTotalQuantity,
            @Value("${order.custom-bundle.min-line-quantity:5}") BigDecimal minLineQuantity,
            @Value("${order.custom-bundle.max-ingredients:10}") int maxIngredients,
            @Value("${order.custom-bundle.packaging-fee:0}") BigDecimal packagingFee) {
        this.catalogClient = catalogClient;
        this.inventoryClient = inventoryClient;
        this.minTotalQuantity = minTotalQuantity;
        this.maxTotalQuantity = maxTotalQuantity;
        this.minLineQuantity = minLineQuantity;
        this.maxIngredients = maxIngredients;
        this.packagingFee = packagingFee;
    }

    public boolean hasBundle(Order order) {
        return order.getItems() != null && order.getItems().stream().anyMatch(OrderItem::isCustomBundle);
    }

    public List<OrderItem> bundleItems(Order order) {
        return order.getItems() == null
                ? List.of()
                : order.getItems().stream().filter(OrderItem::isCustomBundle).toList();
    }

    public CatalogVariant customBundleVariant() {
        var variant = catalogClient.getCustomBundleVariant();
        if (variant == null || variant.variantId() == null) {
            throw new InvalidOrderStatusException("Chưa cấu hình mặt hàng hệ thống \"Trà đóng theo yêu cầu\"");
        }
        return new CatalogVariant(variant.variantId(), variant.sku(), variant.productName(), variant.variantName());
    }

    /**
     * Kiểm tra giới hạn bundle và chốt giá từng nguyên liệu theo Catalog. Số lượng tính theo đơn vị tồn kho của
     * nguyên liệu (giới hạn mặc định giả định đơn vị là gram).
     */
    public PricedBundle price(List<CustomBundleLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidOrderStatusException("Bundle phải có ít nhất một nguyên liệu");
        }
        Map<UUID, BigDecimal> quantityByIngredient = new LinkedHashMap<>();
        for (CustomBundleLineRequest line : lines) {
            if (line.getIngredientId() == null || line.getQuantity() == null
                    || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidOrderStatusException("Số lượng nguyên liệu phải lớn hơn 0");
            }
            quantityByIngredient.merge(line.getIngredientId(), line.getQuantity(), BigDecimal::add);
        }
        if (quantityByIngredient.size() > maxIngredients) {
            throw new InvalidOrderStatusException("Bundle tối đa " + maxIngredients + " nguyên liệu");
        }
        BigDecimal total = quantityByIngredient.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(minTotalQuantity) < 0 || total.compareTo(maxTotalQuantity) > 0) {
            throw new InvalidOrderStatusException("Tổng khối lượng bundle phải từ " + plain(minTotalQuantity)
                    + " đến " + plain(maxTotalQuantity));
        }

        Map<UUID, CustomBundleCatalogClient.IngredientResponse> ingredients = catalogClient
                .getIngredientsByIds(new ArrayList<>(quantityByIngredient.keySet())).stream()
                .collect(Collectors.toMap(CustomBundleCatalogClient.IngredientResponse::id, Function.identity(),
                        (a, b) -> a));

        List<OrderItemComponent> components = new ArrayList<>();
        BigDecimal ingredientsAmount = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> entry : quantityByIngredient.entrySet()) {
            var ingredient = ingredients.get(entry.getKey());
            if (ingredient == null) {
                throw new InvalidOrderStatusException("Có nguyên liệu đã chọn không còn trong danh mục");
            }
            if (!ACTIVE.equals(ingredient.status()) || ingredient.type() == null
                    || ingredient.type() != RECIPE_INGREDIENT || ingredient.retailPricePerUnit() == null) {
                throw new InvalidOrderStatusException("Nguyên liệu " + ingredient.name() + " hiện không bán lẻ");
            }
            if (entry.getValue().compareTo(minLineQuantity) < 0) {
                throw new InvalidOrderStatusException("Nguyên liệu " + ingredient.name() + " tối thiểu "
                        + plain(minLineQuantity));
            }
            OrderItemComponent component = new OrderItemComponent();
            component.setIngredientId(ingredient.id());
            component.setIngredientCodeSnapshot(ingredient.ingredientCode());
            component.setIngredientNameSnapshot(ingredient.name());
            component.setUnitSnapshot(ingredient.unitSymbol());
            component.setQuantity(entry.getValue());
            component.setUnitPrice(ingredient.retailPricePerUnit());
            BigDecimal lineTotal = entry.getValue().multiply(ingredient.retailPricePerUnit())
                    .setScale(2, RoundingMode.HALF_UP);
            component.setLineTotal(lineTotal);
            components.add(component);
            ingredientsAmount = ingredientsAmount.add(lineTotal);
        }
        BigDecimal unitPrice = ingredientsAmount.add(packagingFee).setScale(0, RoundingMode.HALF_UP);
        return new PricedBundle(components, unitPrice);
    }

    /** Ước tính đủ/thiếu nguyên liệu cho các dòng bundle chưa gửi yêu cầu đóng gói (trước khi thu tiền). */
    public BundleAvailability availability(Order order) {
        boolean enough = true;
        Instant latest = null;
        for (OrderItem item : bundleItems(order)) {
            if (item.getPackingReferenceId() != null) {
                continue;
            }
            var response = inventoryClient.checkAvailability(
                    new CustomBundleInventoryClient.AvailabilityRequest(order.getBranchId(), toLines(item)));
            if (response != null && !response.enough()) {
                enough = false;
                latest = later(latest, response.estimatedAvailableAt());
            }
        }
        return new BundleAvailability(enough, latest);
    }

    /**
     * Sau khi dòng bundle đã thu tiền: tạo yêu cầu đóng gói cho dòng chưa có (Inventory idempotent theo
     * orderItemId). Lỗi không làm hỏng việc thu tiền; nút kiểm tra / giao hàng gọi lại bước này.
     */
    public void requestPacking(Order order) {
        for (OrderItem item : bundleItems(order)) {
            if (item.getPackingReferenceId() != null) {
                continue;
            }
            try {
                var packing = inventoryClient.createPacking(new CustomBundleInventoryClient.CreatePackingRequest(
                        order.getId(),
                        order.getOrderCode(),
                        item.getId(),
                        item.getProductVariantId(),
                        item.getVariantNameSnapshot(),
                        order.getBranchId(),
                        toLines(item)));
                apply(order, item, packing);
            } catch (RuntimeException exception) {
                log.warn("Cannot create custom bundle packing. orderCode={}, orderItemId={}, reason={}",
                        order.getOrderCode(), item.getId(), exception.getMessage());
            }
        }
    }

    /** Tạo yêu cầu còn thiếu rồi lấy lại trạng thái mới nhất từ Inventory. */
    public void refreshPacking(Order order) {
        if (!hasBundle(order)) {
            return;
        }
        requestPacking(order);
        Map<UUID, CustomBundleInventoryClient.PackingResponse> byItem = inventoryClient
                .findPackingsByOrder(order.getId()).stream()
                .collect(Collectors.toMap(CustomBundleInventoryClient.PackingResponse::orderItemId,
                        Function.identity(), (a, b) -> a));
        for (OrderItem item : bundleItems(order)) {
            var packing = byItem.get(item.getId());
            if (packing != null) {
                apply(order, item, packing);
            }
        }
    }

    public boolean allPacked(Order order) {
        return bundleItems(order).stream().allMatch(item -> item.getPackingStatus() == BundlePackingStatus.PACKED);
    }

    public void requireAllPacked(Order order) {
        List<String> pending = bundleItems(order).stream()
                .filter(item -> item.getPackingStatus() != BundlePackingStatus.PACKED)
                .map(item -> item.getPackingCode() == null ? item.getVariantNameSnapshot() : item.getPackingCode())
                .toList();
        if (!pending.isEmpty()) {
            throw new InvalidOrderStatusException("Trà đóng theo yêu cầu chưa đóng gói xong: " + String.join(", ", pending));
        }
    }

    /** Huỷ yêu cầu đóng gói chưa đóng và trả nguyên liệu đã giữ; bundle đã đóng gói thì không huỷ được. */
    public void cancelPacking(Order order, String reason) {
        boolean requested = bundleItems(order).stream().anyMatch(item -> item.getPackingReferenceId() != null);
        if (!requested) {
            return;
        }
        List<CustomBundleInventoryClient.PackingResponse> cancelled;
        try {
            cancelled = inventoryClient.cancelPackingsByOrder(
                    new CustomBundleInventoryClient.CancelPackingsRequest(order.getId(), reason));
        } catch (RuntimeException exception) {
            throw new InvalidOrderStatusException("Không huỷ được yêu cầu đóng gói: " + exception.getMessage());
        }
        Map<UUID, CustomBundleInventoryClient.PackingResponse> byItem = cancelled.stream()
                .collect(Collectors.toMap(CustomBundleInventoryClient.PackingResponse::orderItemId,
                        Function.identity(), (a, b) -> a));
        bundleItems(order).forEach(item -> {
            var packing = byItem.get(item.getId());
            if (packing != null) {
                apply(order, item, packing);
            }
        });
    }

    /** Kafka CUSTOM_BUNDLE_PACKED. */
    public boolean markPacked(OrderItem item, String packingCode) {
        if (!item.isCustomBundle() || item.getPackingStatus() == BundlePackingStatus.PACKED) {
            return false;
        }
        item.setPackingStatus(BundlePackingStatus.PACKED);
        if (packingCode != null && !packingCode.isBlank()) {
            item.setPackingCode(packingCode);
        }
        return true;
    }

    private void apply(Order order, OrderItem item, CustomBundleInventoryClient.PackingResponse packing) {
        if (packing == null) {
            return;
        }
        item.setPackingReferenceId(packing.id());
        item.setPackingCode(packing.packingCode());
        item.setPackingStatus(parseStatus(packing.status()));
        if (item.getPackingStatus() == BundlePackingStatus.WAITING_MATERIAL) {
            order.setExpectedAvailableAt(later(order.getExpectedAvailableAt(), packing.expectedAvailableAt()));
        }
    }

    private List<CustomBundleInventoryClient.BundleLine> toLines(OrderItem item) {
        return item.getComponents().stream()
                .map(component -> new CustomBundleInventoryClient.BundleLine(
                        component.getIngredientId(), component.getIngredientNameSnapshot(), component.getQuantity()))
                .toList();
    }

    private static BundlePackingStatus parseStatus(String status) {
        try {
            return status == null ? null : BundlePackingStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Instant later(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record CatalogVariant(UUID id, String sku, String productName, String variantName) {
    }

    public record PricedBundle(List<OrderItemComponent> components, BigDecimal unitPrice) {
    }

    public record BundleAvailability(boolean enough, Instant estimatedAvailableAt) {
    }
}
