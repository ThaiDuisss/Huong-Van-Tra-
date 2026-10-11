package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phần dùng chung của luồng đơn chờ hàng (bán trước, trừ tồn sau): hỏi Inventory ngày có hàng, đưa đơn đã thu
 * tiền vào trạng thái chờ hàng và yêu cầu Inventory chuẩn bị hàng (sản xuất theo đơn / nhập nguyên liệu).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderBackorderSupport {

    private final InventoryClient inventoryClient;

    /** Đơn chờ hàng chưa được trừ tồn: khi thu tiền xong không xử lý tồn kho ngay. */
    public boolean isAwaitingStock(Order order) {
        return order != null && order.isBackorder()
                && order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.CONSUMED
                && order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.RESERVED;
    }

    public InventoryClient.AvailabilityEstimateResponse estimate(Order order) {
        return inventoryClient.estimateAvailability(toRequest(order));
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
        return inventoryClient.prepareBackorder(toRequest(order));
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
