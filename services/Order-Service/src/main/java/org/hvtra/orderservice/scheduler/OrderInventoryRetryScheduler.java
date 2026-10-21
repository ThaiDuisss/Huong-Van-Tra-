package org.hvtra.orderservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đơn đã thanh toán (tiền mặt hoặc chuyển khoản) nhưng Inventory chưa trừ được tồn (kệ/kho thiếu, dịch vụ lỗi)
 * nằm ở PAYMENT_CONFIRMED với trạng thái kho FAILED. Tiền mặt không có event nào gọi lại nên cần scheduler
 * thử lại cho tới khi trừ được, lúc đó đơn mới chuyển COMPLETED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderInventoryRetryScheduler {

    private final OrderPaymentEventService orderPaymentEventService;

    @Scheduled(fixedDelayString = "${order.inventory-retry.delay-ms:120000}",
            initialDelayString = "${order.inventory-retry.initial-delay-ms:60000}")
    public void retryFailedInventory() {
        int completed = orderPaymentEventService.retryFailedInventory();
        if (completed > 0) {
            log.info("Đã trừ kho lại thành công cho {} đơn đã thanh toán", completed);
        }
    }
}
