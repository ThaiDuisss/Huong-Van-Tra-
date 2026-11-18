package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.LoyaltyAdjustmentRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.model.entity.Customer;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface LoyaltyService {

    PageResponse<LoyaltyTransactionResponse> getTransactions(UUID customerId, Pageable pageable);

    /** Quản lý cộng / trừ điểm thủ công. Không cho trừ quá số dư. */
    LoyaltyTransactionResponse adjust(UUID customerId, LoyaltyAdjustmentRequest request);

    /**
     * Tích điểm cho một đơn hoàn tất theo hạng hiện tại của khách. Mỗi đơn chỉ tích một lần.
     * Customer phải đang được khóa trong transaction của người gọi.
     */
    void earnForOrder(Customer customer, UUID orderId, String orderCode, BigDecimal paidAmount);

    /**
     * Trừ lại điểm đã tích của đơn theo tỷ lệ tiền hoàn; hoàn toàn bộ thì trừ hết phần còn lại.
     * Số dư không xuống dưới 0. Mỗi phiếu hoàn chỉ xử lý một lần.
     * Customer phải đang được khóa trong transaction của người gọi.
     */
    void reverseForRefund(
            Customer customer,
            UUID orderId,
            UUID refundId,
            String refundCode,
            BigDecimal refundAmount,
            BigDecimal orderAmount,
            boolean fullRefund
    );
}
