package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.customer.constant.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sheet Tier_Management – ITC_TIER_xxx. */
@DisplayName("IT – Quản lý hạng khách hàng (Tier_Management)")
class CustomerTierIntegrationTest extends CustomerIntegrationTestSupport {

    @Test
    @DisplayName("ITC_TIER_001 Tạo hạng mới hợp lệ: xuất hiện trong danh sách và thống kê, hệ số điểm mặc định 1")
    void create_validTier() throws Exception {
        JsonNode tier = json(doPost(TIERS, tierBody("PLATINUM", "Bạch kim", 8_000_000, 20, "#7C3AED"))
                .andExpect(status().isCreated()));

        assertThat(tier.get("id").asText()).matches("^[0-9a-fA-F-]{36}$");
        assertThat(tier.get("code").asText()).isEqualTo("PLATINUM");
        assertThat(tier.get("minOrderCount").asInt()).isEqualTo(20);
        assertThat(tier.get("pointMultiplier").decimalValue()).isEqualByComparingTo("1");
        doGet(TIERS).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        assertThat(CustomerManagementIntegrationTest.countOf(json(doGet(TIERS + "/counts")), "PLATINUM")).isZero();
    }

    @Test
    @DisplayName("ITC_TIER_002 Tạo hạng trùng mã → 409")
    void create_duplicateCode_conflict() throws Exception {
        JsonNode error = json(doPost(TIERS, tierBody("SILVER", "Bạc 2", 1_000_000, 3, "#C0C0C0"))
                .andExpect(status().isConflict()));

        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_TIER_CODE_EXISTS);
        doGet(TIERS).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("ITC_TIER_003 Tạo hạng thiếu mã/tên, chi tiêu âm → 400")
    void create_invalidInput_badRequest() throws Exception {
        JsonNode error = json(doPost(TIERS, tierBody("", "", -1, 0, null)).andExpect(status().isBadRequest()));

        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message").asText()).contains("code").contains("name").contains("minSpending");
    }

    @Test
    @DisplayName("ITC_TIER_004 Đổi tên & màu hạng đang có khách: thành công, khách thấy tên mới")
    void update_nameAndColor_ofAssignedTier() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        reachSilver(customerId);

        doPut(TIERS + "/" + silverId, tierBody("SILVER", "Bạc mới", 1_000_000, 3, "#C0C0C0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorCode").value("#C0C0C0"));

        JsonNode customer = json(doGet(BASE + "/" + customerId));
        assertThat(customer.get("tierName").asText()).isEqualTo("Bạc mới");
        assertThat(customer.get("tierColor").asText()).isEqualTo("#C0C0C0");
    }

    @Test
    @DisplayName("ITC_TIER_005 Nâng ngưỡng hạng đang có khách: thành công và không hạ hạng khách hiện tại")
    void update_thresholdOfAssignedTier_keepsCustomerTier() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        reachSilver(customerId);

        doPut(TIERS + "/" + silverId, tierBody("SILVER", "Bạc", 2_000_000, 5, "#94A3B8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minSpending").value(2_000_000));

        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.tierCode").value("SILVER"));
    }

    @Test
    @DisplayName("ITC_TIER_006 Đổi ngưỡng hạng: chi tiết khách tính phần còn thiếu theo ngưỡng mới")
    void update_threshold_changesRemainingInDetail() throws Exception {
        doPut(TIERS + "/" + silverId, tierBody("SILVER", "Bạc", 2_000_000, 5, "#94A3B8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minOrderCount").value(5));

        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doGet(BASE + "/" + customerId)
                .andExpect(jsonPath("$.nextTier.code").value("SILVER"))
                .andExpect(jsonPath("$.remainingSpending").value(2_000_000))
                .andExpect(jsonPath("$.remainingOrderCount").value(5));
    }

    @Test
    @DisplayName("ITC_TIER_007 Ngừng áp dụng hạng: status INACTIVE và không còn là hạng kế tiếp")
    void changeStatus_inactive_excludedFromNextTier() throws Exception {
        doPatch(TIERS + "/" + silverId + "/status", Map.of("status", "INACTIVE")).andExpect(status().isOk());

        doGet(TIERS + "/" + silverId).andExpect(jsonPath("$.status").value("INACTIVE"));
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.nextTier.code").value("GOLD"));
    }

    @Test
    @DisplayName("ITC_TIER_008 Thống kê số khách theo hạng khớp với dữ liệu khách hàng")
    void counts_matchCustomers() throws Exception {
        createCustomerId("A", "0901234561");
        createCustomerId("B", "0901234562");
        UUID gold = createCustomerId("C", "0901234563");
        for (int i = 0; i < 10; i++) {
            completeOrder(gold, 500_000);
        }

        JsonNode counts = json(doGet(TIERS + "/counts").andExpect(status().isOk()));

        assertThat(CustomerManagementIntegrationTest.countOf(counts, "ALL")).isEqualTo(3);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "MEMBER")).isEqualTo(2);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "GOLD")).isEqualTo(1);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "UNASSIGNED")).isZero();
        assertThat(json(doGet(BASE + "?tierIds=" + memberId + "&page=0&size=20")).get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("ITC_TIER_009 Tạo hạng không truyền màu nhận diện → dùng màu mặc định #6B7280")
    void create_withoutColor_usesDefaultColor() throws Exception {
        doPost(TIERS, tierBody("BRONZE", "Đồng", 500_000, 1, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.colorCode").value("#6B7280"));
    }

    /** SILVER yêu cầu 1.000.000đ và 3 đơn. */
    private void reachSilver(UUID customerId) throws Exception {
        for (int i = 0; i < 3; i++) {
            completeOrder(customerId, 400_000);
        }
        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.tierCode").value("SILVER"));
    }

    private static Map<String, Object> tierBody(String code, String name, long minSpending, int minOrderCount, String color) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("minSpending", minSpending);
        body.put("minOrderCount", minOrderCount);
        body.put("status", "ACTIVE");
        body.put("colorCode", color);
        return body;
    }
}
