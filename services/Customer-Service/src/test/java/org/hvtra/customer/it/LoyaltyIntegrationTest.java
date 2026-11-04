package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.infrastructure.client.OrderClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sheet Loyalty_Management – ITC_LOY_xxx.
 * Luồng cộng điểm: Order-Service gọi PUT /api/customer/purchases/{orderId}; Customer-Service gọi ngược
 * GET /api/orders/{orderId} (OrderClient – được mock) rồi ghi nhận mua hàng và cộng điểm EARN.
 */
@DisplayName("IT – Tích điểm (Loyalty_Management)")
class LoyaltyIntegrationTest extends CustomerIntegrationTestSupport {

    private static final String PURCHASES = BASE + "/purchases/";

    // ------------------------------------------------------------ Function 1: point rules

    @Test
    @DisplayName("ITC_LOY_001 Tạo quy tắc tích điểm theo hạng SILVER: áp giá trị mặc định")
    void createRule_withDefaults() throws Exception {
        JsonNode rule = json(doPost(RULES, rule("RULE-SILVER", silverId, 10_000, "1.5")).andExpect(status().isCreated()));

        assertThat(rule.get("id").asText()).matches("^[0-9a-fA-F-]{36}$");
        assertThat(rule.get("tierCode").asText()).isEqualTo("SILVER");
        assertThat(rule.get("minOrderAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(rule.get("earnAfterDiscount").asBoolean()).isTrue();
        assertThat(rule.get("allowCouponUsage").asBoolean()).isTrue();
        assertThat(rule.get("priority").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("ITC_LOY_002 Tạo quy tắc trùng mã → 409; hạng không tồn tại → 404")
    void createRule_duplicateCode_and_unknownTier() throws Exception {
        doPost(RULES, rule("RULE-SILVER", silverId, 10_000, "1.5")).andExpect(status().isCreated());

        JsonNode duplicate = json(doPost(RULES, rule("RULE-SILVER", null, 10_000, "1")).andExpect(status().isConflict()));
        JsonNode unknownTier = json(doPost(RULES, rule("RULE-X", UUID.fromString(NOT_FOUND_ID), 10_000, "1"))
                .andExpect(status().isNotFound()));

        assertThat(duplicate.get("message").asText()).isEqualTo(Message.LOYALTY_POINT_RULE_CODE_EXISTS);
        assertThat(unknownTier.get("message").asText()).isEqualTo(Message.CUSTOMER_TIER_NOT_FOUND);
        doGet(RULES).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("ITC_LOY_003 Xóa mềm quy tắc: 204, không còn trong danh sách, đơn mới không được cộng điểm")
    void deleteRule_noLongerApplied() throws Exception {
        String ruleId = json(doPost(RULES, rule("RULE-ALL", null, 10_000, "1")).andExpect(status().isCreated()))
                .get("id").asText();

        doDelete(RULES + "/" + ruleId).andExpect(status().isNoContent());
        doGet(RULES).andExpect(jsonPath("$.length()").value(0));

        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID orderId = givenOrder(customerId, 500_000);
        doPut(PURCHASES + orderId, null).andExpect(status().isOk());
        assertThat(balanceOf(customerId)).isZero();
    }

    // ------------------------------------------------------------ Function 2: purchase update

    @Test
    @DisplayName("ITC_LOY_004 Thanh toán đơn 500.000đ: ghi nhận mua hàng và cộng 75 điểm EARN (10.000đ = 1 điểm × 1.5)")
    void purchaseUpdate_earnsPoints() throws Exception {
        doPost(RULES, rule("RULE-ALL", null, 10_000, "1.5")).andExpect(status().isCreated());
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID orderId = givenOrder(customerId, 500_000);

        doPut(PURCHASES + orderId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(500_000))
                .andExpect(jsonPath("$.totalOrderCount").value(1));

        JsonNode account = json(doGet(BASE + "/loyalty-accounts/customer/" + customerId));
        assertThat(account.get("pointBalance").asInt()).isEqualTo(75);
        JsonNode transactions = json(doGet(BASE + "/loyalty-transactions?loyaltyAccountId=" + account.get("id").asText()));
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).get("type").asText()).isEqualTo("EARN");
        assertThat(transactions.get(0).get("referenceType").asText()).isEqualTo("ORDER");
        assertThat(transactions.get(0).get("referenceId").asText()).isEqualTo(orderId.toString());
    }

    @Test
    @DisplayName("ITC_LOY_005 Gọi cập nhật mua hàng 2 lần cho cùng đơn → không cộng điểm/doanh số lần 2")
    void purchaseUpdate_twice_idempotent() throws Exception {
        doPost(RULES, rule("RULE-ALL", null, 10_000, "1.5")).andExpect(status().isCreated());
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID orderId = givenOrder(customerId, 500_000);

        doPut(PURCHASES + orderId, null).andExpect(status().isOk());
        doPut(PURCHASES + orderId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(500_000))
                .andExpect(jsonPath("$.totalOrderCount").value(1));

        assertThat(balanceOf(customerId)).isEqualTo(75);
    }

    @Test
    @DisplayName("ITC_LOY_006 Đơn khách vãng lai (customerId=null) → 400 'Thông tin đơn hàng không hợp lệ'")
    void purchaseUpdate_walkInOrder_badRequest() throws Exception {
        UUID orderId = givenOrder(null, 500_000);

        JsonNode error = json(doPut(PURCHASES + orderId, null).andExpect(status().isBadRequest()));

        assertThat(error.get("message").asText()).isEqualTo("Thông tin đơn hàng không hợp lệ");
    }

    @Test
    @DisplayName("ITC_LOY_007 Order-Service không phản hồi → 503 ORDER_SERVICE_UNAVAILABLE, không cộng điểm")
    void purchaseUpdate_orderServiceDown_serviceUnavailable() throws Exception {
        doPost(RULES, rule("RULE-ALL", null, 10_000, "1")).andExpect(status().isCreated());
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID orderId = UUID.randomUUID();
        when(orderClient.getOrderById(orderId))
                .thenThrow(new IllegalStateException("order-service down", new ConnectException("Connection refused")));

        JsonNode error = json(doPut(PURCHASES + orderId, null).andExpect(status().isServiceUnavailable()));

        assertThat(error.get("code").asText()).isEqualTo("ORDER_SERVICE_UNAVAILABLE");
        assertThat(error.get("message").asText()).isEqualTo("Không thể kiểm tra đơn hàng lúc này. Vui lòng thử lại.");
        assertThat(balanceOf(customerId)).isZero();
        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.totalOrderCount").value(0));
    }

    // ------------------------------------------------------------ Function 3: account & transactions

    @Test
    @DisplayName("ITC_LOY_008 Số dư tài khoản điểm bằng tổng điểm các giao dịch")
    void account_balanceEqualsSumOfTransactions() throws Exception {
        doPost(RULES, rule("RULE-ALL", null, 10_000, "1")).andExpect(status().isCreated());
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doPut(PURCHASES + givenOrder(customerId, 200_000), null).andExpect(status().isOk());
        doPut(PURCHASES + givenOrder(customerId, 130_000), null).andExpect(status().isOk());

        JsonNode account = json(doGet(BASE + "/loyalty-accounts/customer/" + customerId).andExpect(status().isOk()));
        int sum = 0;
        for (JsonNode tx : json(doGet(BASE + "/loyalty-transactions?loyaltyAccountId=" + account.get("id").asText()))) {
            sum += tx.get("points").asInt();
        }

        assertThat(account.get("pointBalance").asInt()).isEqualTo(33).isEqualTo(sum);
    }

    @Test
    @DisplayName("ITC_LOY_009 Tài khoản điểm của khách không tồn tại → 404")
    void account_unknownCustomer_notFound() throws Exception {
        JsonNode error = json(doGet(BASE + "/loyalty-accounts/customer/" + NOT_FOUND_ID).andExpect(status().isNotFound()));

        assertThat(error.get("message").asText()).isEqualTo(Message.LOYALTY_ACCOUNT_NOT_FOUND);
    }

    @Test
    @Disabled("DEFECT: CustomerServiceImpl.updatePurchase dùng BigDecimal.divide không làm tròn → ArithmeticException "
            + "'Non-terminating decimal expansion' (HTTP 500) khi số tiền không chia hết cho spendAmountPerBlock.")
    @DisplayName("ITC_LOY_010 Quy tắc 30.000đ = 1 điểm, đơn 100.000đ → cộng 3 điểm (làm tròn xuống)")
    void purchaseUpdate_nonTerminatingDivision_roundsDown() throws Exception {
        doPost(RULES, rule("RULE-30K", null, 30_000, "1")).andExpect(status().isCreated());
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");

        doPut(PURCHASES + givenOrder(customerId, 100_000), null).andExpect(status().isOk());

        assertThat(balanceOf(customerId)).isEqualTo(3);
    }

    // ------------------------------------------------------------ helpers

    private UUID givenOrder(UUID customerId, long finalAmount) {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(finalAmount);
        when(orderClient.getOrderById(orderId)).thenReturn(new OrderClient.OrderResponse(
                orderId, "DH-" + orderId.toString().substring(0, 6), customerId, amount, amount, LocalDateTime.now()));
        return orderId;
    }

    private int balanceOf(UUID customerId) throws Exception {
        return json(doGet(BASE + "/loyalty-accounts/customer/" + customerId)).get("pointBalance").asInt();
    }

    private static Map<String, Object> rule(String code, UUID tierId, long spendPerBlock, String multiplier) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Quy tắc " + code);
        body.put("tierId", tierId);
        body.put("spendAmountPerBlock", spendPerBlock);
        body.put("pointsPerBlock", 1);
        body.put("pointMultiplier", new BigDecimal(multiplier));
        body.put("effectiveFrom", "2026-01-01T00:00:00Z");
        body.put("status", "ACTIVE");
        return body;
    }
}
