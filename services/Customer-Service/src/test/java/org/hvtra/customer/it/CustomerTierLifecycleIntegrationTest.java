package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.customer.integration.consumer.OrderCompletedConsumer;
import org.hvtra.customer.integration.consumer.OrderRefundedConsumer;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.service.LoyaltyTierService;
import org.hvtra.customer.service.YearlyTierReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sheet TierLifecycle_Management – ITC_LTR_xxx.
 * Sự kiện Kafka từ Order-Service được đưa vào đúng consumer thật của service (Kafka broker không
 * chạy trong test), sau đó kiểm tra kết quả qua REST API như màn hình chi tiết khách hàng.
 */
@DisplayName("IT – Vòng đời hạng thành viên (TierLifecycle_Management)")
class CustomerTierLifecycleIntegrationTest extends CustomerIntegrationTestSupport {

    @Autowired
    private LoyaltyTierService loyaltyTierService;

    @Autowired
    private YearlyTierReviewService yearlyTierReviewService;

    private OrderCompletedConsumer orderCompletedConsumer;
    private OrderRefundedConsumer orderRefundedConsumer;

    @BeforeEach
    void setUpConsumers() {
        orderCompletedConsumer = new OrderCompletedConsumer(loyaltyTierService);
        orderRefundedConsumer = new OrderRefundedConsumer(loyaltyTierService);
    }

    // ------------------------------------------------------------ Function 1: order.completed

    @Test
    @DisplayName("ITC_LTR_001 3 đơn hoàn tất tổng 1.200.000đ → lên SILVER, MEMBER cũ chuyển REPLACED")
    void orderCompleted_qualifiesSilver() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");

        for (int i = 1; i <= 3; i++) {
            orderCompletedConsumer.handle(completed(UUID.randomUUID(), "DH-" + i, customerId, 400_000));
        }

        JsonNode customer = json(doGet(BASE + "/" + customerId).andExpect(status().isOk()));
        assertThat(customer.get("tierCode").asText()).isEqualTo("SILVER");
        assertThat(customer.get("totalSpent").decimalValue()).isEqualByComparingTo("1200000");
        assertThat(customer.get("totalOrderCount").asInt()).isEqualTo(3);
        assertThat(customer.get("lastPurchaseDate").isNull()).isFalse();

        JsonNode history = json(doGet(BASE + "/" + customerId + "/tier-history"));
        assertThat(history.get(0).get("tier").get("code").asText()).isEqualTo("SILVER");
        assertThat(history.get(0).get("assignmentReason").asText()).isEqualTo("YEARLY_QUALIFICATION");
        assertThat(history.get(0).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(history.get(1).get("tier").get("code").asText()).isEqualTo("MEMBER");
        assertThat(history.get(1).get("status").asText()).isEqualTo("REPLACED");

        doGet(BASE + "/" + customerId + "/purchase-transactions?page=0&size=10")
                .andExpect(jsonPath("$.totalElements").value(3));
        JsonNode counts = json(doGet(TIERS + "/counts"));
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "SILVER")).isEqualTo(1);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "MEMBER")).isZero();
    }

    @Test
    @DisplayName("ITC_LTR_002 Sự kiện order.completed gửi lặp → chỉ ghi nhận 1 lần")
    void orderCompleted_duplicateEvent_idempotent() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        OrderCompletedEvent event = completed(UUID.randomUUID(), "DH-1", customerId, 300_000);

        orderCompletedConsumer.handle(event);
        orderCompletedConsumer.handle(event);

        doGet(BASE + "/" + customerId)
                .andExpect(jsonPath("$.totalSpent").value(300_000))
                .andExpect(jsonPath("$.totalOrderCount").value(1));
        doGet(BASE + "/" + customerId + "/purchase-transactions?page=0&size=10")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("ITC_LTR_003 Đơn khách vãng lai (customerId=null) → consumer bỏ qua, không thay đổi dữ liệu")
    void orderCompleted_walkInCustomer_ignored() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");

        orderCompletedConsumer.handle(completed(UUID.randomUUID(), "DH-1", null, 300_000));

        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.totalOrderCount").value(0));
    }

    // ------------------------------------------------------------ Function 2: refunds

    @Test
    @DisplayName("ITC_LTR_004 Hoàn tiền một phần 120.000đ → totalSpent giảm, số đơn giữ nguyên")
    void partialRefund_reducesSpentOnly() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID orderId = UUID.randomUUID();
        orderCompletedConsumer.handle(completed(orderId, "DH-1", customerId, 250_000));
        orderCompletedConsumer.handle(completed(UUID.randomUUID(), "DH-2", customerId, 250_000));

        orderRefundedConsumer.handlePartialRefund(new OrderPartiallyRefundedEvent(
                orderId, UUID.randomUUID(), customerId, new BigDecimal("120000"), LocalDateTime.now(), "Trả 1 món"));

        doGet(BASE + "/" + customerId)
                .andExpect(jsonPath("$.totalSpent").value(380_000))
                .andExpect(jsonPath("$.totalOrderCount").value(2));
        doGet(BASE + "/" + customerId + "/tier-metrics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(380_000));
    }

    @Test
    @DisplayName("ITC_LTR_005 Hoàn tiền toàn bộ → giảm chi tiêu và số đơn; refundId lặp bị bỏ qua")
    void fullRefund_reducesSpentAndOrders_idempotent() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID orderId = UUID.randomUUID();
        orderCompletedConsumer.handle(completed(orderId, "DH-1", customerId, 200_000));
        orderCompletedConsumer.handle(completed(UUID.randomUUID(), "DH-2", customerId, 300_000));
        OrderFullyRefundedEvent refund = new OrderFullyRefundedEvent(
                orderId, UUID.randomUUID(), customerId, new BigDecimal("200000"), LocalDateTime.now(), "Hủy đơn");

        orderRefundedConsumer.handleFullRefund(refund);
        orderRefundedConsumer.handleFullRefund(refund);

        doGet(BASE + "/" + customerId)
                .andExpect(jsonPath("$.totalSpent").value(300_000))
                .andExpect(jsonPath("$.totalOrderCount").value(1));
    }

    // ------------------------------------------------------------ Function 3: progress, history, adjustment

    @Test
    @DisplayName("ITC_LTR_006 Tiến độ hạng: MEMBER 400.000đ/1 đơn → còn thiếu 600.000đ/2 đơn để lên SILVER")
    void progress_remainingToNextTier() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        orderCompletedConsumer.handle(completed(UUID.randomUUID(), "DH-1", customerId, 400_000));

        doGet(BASE + "/" + customerId + "/tier-progress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier.code").value("MEMBER"))
                .andExpect(jsonPath("$.nextTier.code").value("SILVER"))
                .andExpect(jsonPath("$.remainingSpending").value(600_000))
                .andExpect(jsonPath("$.remainingOrderCount").value(2));
    }

    @Test
    @DisplayName("ITC_LTR_007 Điều chỉnh hạng thủ công lên GOLD: lịch sử MANUAL_ADJUSTMENT, cập nhật thống kê")
    void manualAdjustment_toGold() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");

        JsonNode assignment = json(doPost(BASE + "/" + customerId + "/tier-adjustments",
                Map.of("tierId", goldId, "reason", "Khách VIP doanh nghiệp")).andExpect(status().isOk()));

        assertThat(assignment.get("assignmentReason").asText()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(assignment.get("note").asText()).isEqualTo("Khách VIP doanh nghiệp");
        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.tierCode").value("GOLD"));
        doGet(BASE + "/" + customerId + "/tier-history")
                .andExpect(jsonPath("$[0].tier.code").value("GOLD"))
                .andExpect(jsonPath("$[1].status").value("REPLACED"));
        assertThat(CustomerManagementIntegrationTest.countOf(json(doGet(TIERS + "/counts")), "GOLD")).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_LTR_008 Điều chỉnh hạng không nhập lý do → 400, lịch sử không đổi")
    void manualAdjustment_withoutReason_badRequest() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        Map<String, Object> body = new HashMap<>();
        body.put("tierId", goldId);
        body.put("reason", "");

        doPost(BASE + "/" + customerId + "/tier-adjustments", body).andExpect(status().isBadRequest());

        doGet(BASE + "/" + customerId + "/tier-history").andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------ Function 4: yearly review

    @Test
    @DisplayName("ITC_LTR_009 Xét hạng định kỳ: khách GOLD không có số liệu năm → hạ về MEMBER (YEARLY_REVIEW)")
    void yearlyReview_downgradesCustomerWithoutQualification() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doPost(BASE + "/" + customerId + "/tier-adjustments", Map.of("tierId", goldId, "reason", "Tạm thời"))
                .andExpect(status().isOk());

        LocalDateTime now = LocalDateTime.now();
        yearlyTierReviewService.reviewCustomerTier(customerId, now.getYear(), now);

        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.tierCode").value("MEMBER"));
        doGet(BASE + "/" + customerId + "/tier-history")
                .andExpect(jsonPath("$[0].assignmentReason").value("YEARLY_REVIEW"))
                .andExpect(jsonPath("$[0].tier.code").value("MEMBER"));
    }

    private static OrderCompletedEvent completed(UUID orderId, String code, UUID customerId, long amount) {
        return new OrderCompletedEvent(orderId, code, customerId, BigDecimal.valueOf(amount), LocalDateTime.now());
    }
}
