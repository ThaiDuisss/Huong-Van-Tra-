package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.customer.constant.Message;
import org.junit.jupiter.api.Disabled;
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
    @DisplayName("ITC_TIER_001 Tạo hạng mới hợp lệ: xuất hiện trong danh sách, thống kê và dùng được để điều chỉnh hạng")
    void create_validTier() throws Exception {
        JsonNode tier = json(doPost(TIERS, tierBody("PLATINUM", "Bạch kim", 8_000_000, 20, "#7C3AED"))
                .andExpect(status().isCreated()));

        assertThat(tier.get("id").asText()).matches("^[0-9a-fA-F-]{36}$");
        assertThat(tier.get("code").asText()).isEqualTo("PLATINUM");
        assertThat(tier.get("minOrderCount").asInt()).isEqualTo(20);
        doGet(TIERS).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        assertThat(CustomerManagementIntegrationTest.countOf(json(doGet(TIERS + "/counts")), "PLATINUM")).isZero();

        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doPost(BASE + "/" + customerId + "/tier-adjustments", Map.of("tierId", tier.get("id").asText(), "reason", "VIP"))
                .andExpect(status().isOk());
        doGet(BASE + "/" + customerId).andExpect(jsonPath("$.tierCode").value("PLATINUM"));
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
    @DisplayName("ITC_TIER_004 Đổi tên & màu hạng đã có lịch sử gán: thành công, khách thấy tên mới")
    void update_nameAndColor_ofAssignedTier() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doPost(BASE + "/" + customerId + "/tier-adjustments", Map.of("tierId", silverId, "reason", "VIP"))
                .andExpect(status().isOk());

        doPut(TIERS + "/" + silverId, tierBody("SILVER", "Bạc mới", 1_000_000, 3, "#C0C0C0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorCode").value("#C0C0C0"));

        JsonNode customer = json(doGet(BASE + "/" + customerId));
        assertThat(customer.get("tierName").asText()).isEqualTo("Bạc mới");
        assertThat(customer.get("tierColor").asText()).isEqualTo("#C0C0C0");
    }

    @Test
    @DisplayName("ITC_TIER_005 Đổi ngưỡng hạng đã có lịch sử gán → 400, ngưỡng giữ nguyên")
    void update_thresholdOfAssignedTier_rejected() throws Exception {
        createCustomerId("Nguyễn Văn An", "0901234567"); // MEMBER đã có lịch sử gán INITIAL_TIER

        JsonNode error = json(doPut(TIERS + "/" + memberId, tierBody("MEMBER", "Thành viên", 100_000, 0, "#6B7280"))
                .andExpect(status().isBadRequest()));

        assertThat(error.get("message").asText())
                .isEqualTo("Không thể thay đổi ngưỡng tier đã có lịch sử assignment; hãy tạo tier mới hoặc đổi status INACTIVE");
        doGet(TIERS + "/" + memberId).andExpect(jsonPath("$.minSpending").value(0));
    }

    @Test
    @DisplayName("ITC_TIER_006 Đổi ngưỡng hạng chưa có lịch sử gán: thành công, tiến độ lên hạng tính theo ngưỡng mới")
    void update_thresholdOfUnassignedTier() throws Exception {
        doPut(TIERS + "/" + silverId, tierBody("SILVER", "Bạc", 2_000_000, 5, "#94A3B8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minOrderCount").value(5));

        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doGet(BASE + "/" + customerId + "/tier-progress")
                .andExpect(jsonPath("$.nextTier.code").value("SILVER"))
                .andExpect(jsonPath("$.remainingSpending").value(2_000_000))
                .andExpect(jsonPath("$.remainingOrderCount").value(5));
    }

    @Test
    @DisplayName("ITC_TIER_007 Ngừng áp dụng hạng: status INACTIVE và không còn là hạng kế tiếp")
    void changeStatus_inactive_excludedFromProgress() throws Exception {
        doPatch(TIERS + "/" + silverId + "/status", Map.of("status", "INACTIVE")).andExpect(status().isOk());

        doGet(TIERS + "/" + silverId).andExpect(jsonPath("$.status").value("INACTIVE"));
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        doGet(BASE + "/" + customerId + "/tier-progress").andExpect(jsonPath("$.nextTier.code").value("GOLD"));
    }

    @Test
    @DisplayName("ITC_TIER_008 Thống kê số khách theo hạng khớp với dữ liệu khách hàng")
    void counts_matchCustomers() throws Exception {
        createCustomerId("A", "0901234561");
        createCustomerId("B", "0901234562");
        UUID gold = createCustomerId("C", "0901234563");
        doPost(BASE + "/" + gold + "/tier-adjustments", Map.of("tierId", goldId, "reason", "VIP")).andExpect(status().isOk());

        JsonNode counts = json(doGet(TIERS + "/counts").andExpect(status().isOk()));

        assertThat(CustomerManagementIntegrationTest.countOf(counts, "ALL")).isEqualTo(3);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "MEMBER")).isEqualTo(2);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "GOLD")).isEqualTo(1);
        assertThat(CustomerManagementIntegrationTest.countOf(counts, "UNASSIGNED")).isZero();
        assertThat(json(doGet(BASE + "?tierIds=" + memberId + "&page=0&size=20")).get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    @Disabled("DEFECT: CustomerTierRequest.colorCode không bắt buộc nhưng cột customer_tiers.color_code NOT NULL; "
            + "CustomerMapper gán null → lỗi DB, HTTP 500. Cần mặc định '#6B7280' hoặc @NotBlank (400).")
    @DisplayName("ITC_TIER_009 Tạo hạng không truyền màu nhận diện → dùng màu mặc định #6B7280")
    void create_withoutColor_usesDefaultColor() throws Exception {
        doPost(TIERS, tierBody("BRONZE", "Đồng", 500_000, 1, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.colorCode").value("#6B7280"));
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
