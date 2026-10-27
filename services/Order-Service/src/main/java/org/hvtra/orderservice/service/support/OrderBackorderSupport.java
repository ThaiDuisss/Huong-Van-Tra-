package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phần dùng chung của luồng đơn chờ hàng (bán trước, trừ tồn sau): hỏi Inventory ngày có hàng, đưa đơn đã thu
 * tiền vào trạng thái chờ hàng và yêu cầu Inventory chuẩn bị hàng (sản xuất theo đơn / nhập nguyên liệu).
 * Dòng "Trà đóng theo yêu cầu" (CUSTOM_BUNDLE) không đi qua ước tính/chuẩn bị theo variant mà qua yêu cầu đóng gói
 * bên Inventory ({@link OrderCustomBundleSupport}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderBackorderSupport {

    private final InventoryClient inventoryClient;
    private final OrderCustomBundleSupport customBundleSupport;

    /** Đơn chờ hàng chưa được trừ tồn: khi thu tiền xong không xử lý tồn kho ngay. */
    public boolean isAwaitingStock(Order order) {
        return order != null && order.isBackorder()
                && order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.CONSUMED
                && order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.RESERVED;
    }

    /**
     * Ước tính hàng thường theo variant; dòng bundle chưa gửi yêu cầu đóng gói thì tính thêm tồn nguyên liệu.
     * Bundle đã gửi yêu cầu thì không tính lại (nguyên liệu đã giữ/đã trừ), việc giao chờ trạng thái PACKED.
     */
    public InventoryClient.AvailabilityEstimateResponse estimate(Order order) {
        InventoryClient.AvailabilityEstimateResponse productEstimate = bundleOnly(order)
                ? new InventoryClient.AvailabilityEstimateResponse(false, null, List.of())
                : inventoryClient.estimateAvailability(toRequest(order));
        boolean unrequestedBundle = order.getItems() != null && order.getItems().stream()
                .anyMatch(item -> item.isCustomBundle() && item.getPackingReferenceId() == null);
        if (!unrequestedBundle) {
            return productEstimate;
        }
        OrderCustomBundleSupport.BundleAvailability bundles = customBundleSupport.availability(order);
        boolean productBackorder = productEstimate != null && productEstimate.backorder();
        Instant productDate = productEstimate == null ? null : productEstimate.estimatedAvailableAt();
        Instant date = productDate;
        if (!bundles.enough() && bundles.estimatedAvailableAt() != null
                && (date == null || bundles.estimatedAvailableAt().isAfter(date))) {
            date = bundles.estimatedAvailableAt();
        }
        return new InventoryClient.AvailabilityEstimateResponse(
                productBackorder || !bundles.enough(),
                date,
                productEstimate == null || productEstimate.lines() == null ? List.of() : productEstimate.lines());
    }

    /**
     * Đưa đơn vào trạng thái chờ hàng rồi yêu cầu Inventory chuẩn bị hàng sau khi transaction của đơn commit,
     * để không tạo lệnh sản xuất cho một đơn bị rollback. Lỗi ở bước chuẩn bị không làm hỏng việc thu tiền:
     * màn hình "Đơn chờ hàng" gọi lại bước này mỗi lần kiểm tra hàng.
     */
    public void enterWaitingStock(Order order) {
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.WAITING_STOCK);
        order.setInventoryFailureReason(null);
        order.setExpiresAt(null);
        // Dòng bundle đã thu tiền (đủ hoặc cọc): Inventory giữ nguyên liệu nếu đủ, thiếu thì mở cảnh báo nhập.
        requestBundlePacking(order);
        if (bundleOnly(order)) {
            return;
        }

        InventoryClient.AvailabilityEstimateRequest request = toRequest(order);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    prepareQuietly(request);
                }
            });
        } else {
            prepareQuietly(request);
        }
    }

    /** Gọi lại được nhiều lần: Inventory không tạo lệnh sản xuất thứ hai cho cùng một đơn. */
    public InventoryClient.AvailabilityEstimateResponse prepare(Order order) {
        if (bundleOnly(order)) {
            return new InventoryClient.AvailabilityEstimateResponse(false, null, List.of());
        }
        return inventoryClient.prepareBackorder(toRequest(order));
    }

    public boolean hasBundle(Order order) {
        return order != null && order.getItems() != null && order.getItems().stream().anyMatch(OrderItem::isCustomBundle);
    }

    /** Sau khi dòng bundle đã thu tiền: tạo yêu cầu đóng gói còn thiếu (idempotent). */
    public void requestBundlePacking(Order order) {
        if (hasBundle(order)) {
            customBundleSupport.requestPacking(order);
        }
    }

    /** Tạo yêu cầu đóng gói còn thiếu và lấy lại trạng thái đóng gói từ Inventory. */
    public void refreshBundles(Order order) {
        if (hasBundle(order)) {
            customBundleSupport.refreshPacking(order);
        }
    }

    /** Huỷ đơn: huỷ yêu cầu đóng gói chưa đóng, trả lại nguyên liệu đã giữ. */
    public void cancelBundlePacking(Order order, String reason) {
        if (hasBundle(order)) {
            customBundleSupport.cancelPacking(order, reason);
        }
    }

    /** Còn dòng bundle chưa đóng gói xong (đơn chưa giao được). */
    public boolean hasUnpackedBundle(Order order) {
        return hasBundle(order) && !customBundleSupport.allPacked(order);
    }

    public void requireBundlesPacked(Order order) {
        if (hasBundle(order)) {
            customBundleSupport.requireAllPacked(order);
        }
    }

    private boolean bundleOnly(Order order) {
        return hasBundle(order) && order.getItems().stream().allMatch(OrderItem::isCustomBundle);
    }

    private void prepareQuietly(InventoryClient.AvailabilityEstimateRequest request) {
        try {
            inventoryClient.prepareBackorder(request);
        } catch (RuntimeException exception) {
            log.warn("Cannot prepare stock for backorder. orderId={}, reason={}",
                    request.orderId(), exception.getMessage());
        }
    }

    private InventoryClient.AvailabilityEstimateRequest toRequest(Order order) {
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        if (order.getItems() != null) {
            for (var item : order.getItems()) {
                if (item.isCustomBundle()) {
                    continue;
                }
                if (item.getProductVariantId() != null && item.getQuantity() != null) {
                    quantities.merge(item.getProductVariantId(), item.getQuantity(), BigDecimal::add);
                }
            }
        }
        List<InventoryClient.AvailabilityEstimateLine> lines = quantities.entrySet().stream()
                .map(entry -> new InventoryClient.AvailabilityEstimateLine(entry.getKey(), entry.getValue()))
                .toList();
        return new InventoryClient.AvailabilityEstimateRequest(
                order.getBranchId(), order.getId(), order.getOrderCode(), lines);
    }
}
