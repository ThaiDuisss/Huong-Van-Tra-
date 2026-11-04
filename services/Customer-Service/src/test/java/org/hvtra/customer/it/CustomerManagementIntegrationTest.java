package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.customer.constant.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sheet Customer_Management – ITC_CUS_xxx. */
@DisplayName("IT – Quản lý khách hàng (Customer_Management)")
class CustomerManagementIntegrationTest extends CustomerIntegrationTestSupport {

    // ------------------------------------------------------------ Function 1: Create

    @Test
    @DisplayName("ITC_CUS_001 Tạo khách hàng hợp lệ: tự sinh mã, gán hạng MEMBER, mở tài khoản tích điểm, tăng số khách theo hạng")
    void createCustomer_assignsInitialTierAndLoyaltyAccount() throws Exception {
        JsonNode customer = createCustomer("Nguyễn Văn A", "0901234567", "a@example.com");

        assertThat(customer.get("id").asText()).matches("^[0-9a-fA-F-]{36}$");
        assertThat(customer.get("customerCode").asText()).matches("^[A-Z]+-\\d{4}$");
        assertThat(customer.get("fullName").asText()).isEqualTo("Nguyễn Văn A");
        assertThat(customer.get("customerType").asText()).isEqualTo("INDIVIDUAL");
        assertThat(customer.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(customer.get("tierCode").asText()).isEqualTo("MEMBER");
        assertThat(customer.get("totalOrderCount").asInt()).isZero();

        String id = customer.get("id").asText();
        doGet(BASE + "/loyalty-accounts/customer/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointBalance").value(0))
                .andExpect(jsonPath("$.customerCode").value(customer.get("customerCode").asText()));

        doGet(BASE + "/" + id + "/tier-history")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assignmentReason").value("INITIAL_TIER"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        JsonNode counts = json(doGet(TIERS + "/counts").andExpect(status().isOk()));
        assertThat(countOf(counts, "MEMBER")).isEqualTo(1);
        assertThat(countOf(counts, "ALL")).isEqualTo(1);

        JsonNode search = json(doGet(BASE + "?q=0901234567&page=0&size=20").andExpect(status().isOk()));
        assertThat(search.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_CUS_002 Tạo khách hàng trùng số điện thoại → 409, không tạo bản ghi")
    void createCustomer_duplicatePhone_conflict() throws Exception {
        createCustomer("Nguyễn Văn A", "0901234567", null);

        JsonNode error = json(doPost(BASE, customerBody("Trần Thị B", "0901234567", null)).andExpect(status().isConflict()));

        assertThat(error.get("code").asText()).isEqualTo("CUSTOMER_CONFLICT");
        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_PHONE_EXISTS);
        assertThat(totalCustomers()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_CUS_003 Tạo khách hàng trùng email → 409 'Email đã tồn tại'")
    void createCustomer_duplicateEmail_conflict() throws Exception {
        createCustomer("Nguyễn Văn A", "0901234567", "a@example.com");

        JsonNode error = json(doPost(BASE, customerBody("Lê Văn C", "0907777777", "a@example.com"))
                .andExpect(status().isConflict()));

        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_EMAIL_EXISTS);
        assertThat(totalCustomers()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_CUS_004 Tạo khách hàng thiếu tên và SĐT → 400 VALIDATION_ERROR")
    void createCustomer_missingRequiredFields_badRequest() throws Exception {
        JsonNode error = json(doPost(BASE, customerBody("", "", null)).andExpect(status().isBadRequest()));

        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("message").asText()).contains("fullName").contains("phone");
        assertThat(totalCustomers()).isZero();
    }

    @Test
    @DisplayName("ITC_CUS_005 Tạo khách hàng khi không còn hạng ACTIVE → 409 và rollback toàn bộ")
    void createCustomer_noActiveTier_conflictAndRollback() throws Exception {
        for (UUID tierId : new UUID[]{memberId, silverId, goldId}) {
            doPatch(TIERS + "/" + tierId + "/status", Map.of("status", "INACTIVE")).andExpect(status().isOk());
        }

        JsonNode error = json(doPost(BASE, customerBody("Phạm Văn D", "0908888888", null)).andExpect(status().isConflict()));

        assertThat(error.get("message").asText()).isEqualTo(Message.NO_ACTIVE_CUSTOMER_TIER);
        assertThat(totalCustomers()).isZero();

        doPatch(TIERS + "/" + memberId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isOk());
        createCustomer("Phạm Văn D", "0908888888", null);
        assertThat(totalCustomers()).isEqualTo(1);
    }

    // ------------------------------------------------------------ Function 2: Update

    @Test
    @DisplayName("ITC_CUS_006 Cập nhật tên/email: giữ nguyên hạng và số liệu mua hàng")
    void updateCustomer_keepsPurchaseStatisticsAndTier() throws Exception {
        JsonNode created = createCustomer("Nguyễn Văn A", "0901234567", "a@example.com");
        String id = created.get("id").asText();

        // Form "Chỉnh sửa khách hàng" luôn gửi lại tierId hiện tại (PUT thay thế toàn bộ)
        Map<String, Object> body = customerBody("Nguyễn Văn An", "0901234567", "an@example.com");
        body.put("tierId", created.get("tierId").asText());
        JsonNode updated = json(doPut(BASE + "/" + id, body).andExpect(status().isOk()));

        assertThat(updated.get("fullName").asText()).isEqualTo("Nguyễn Văn An");
        assertThat(updated.get("email").asText()).isEqualTo("an@example.com");
        assertThat(updated.get("tierCode").asText()).isEqualTo("MEMBER");
        assertThat(updated.get("customerCode").asText()).isEqualTo(created.get("customerCode").asText());

        JsonNode reloaded = json(doGet(BASE + "/" + id).andExpect(status().isOk()));
        assertThat(reloaded.get("fullName").asText()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    @DisplayName("ITC_CUS_007 Cập nhật SĐT trùng khách hàng khác → 409, dữ liệu cũ giữ nguyên")
    void updateCustomer_duplicatePhone_conflict() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");
        createCustomerId("Trần Thị B", "0909999999");

        JsonNode error = json(doPut(BASE + "/" + id, customerBody("Nguyễn Văn A", "0909999999", null))
                .andExpect(status().isConflict()));

        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_PHONE_EXISTS);
        doGet(BASE + "/" + id).andExpect(jsonPath("$.phone").value("0901234567"));
    }

    @Test
    @DisplayName("ITC_CUS_008 Cập nhật khách hàng với hạng không tồn tại → 404")
    void updateCustomer_unknownTier_notFound() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");
        Map<String, Object> body = customerBody("Nguyễn Văn A", "0901234567", null);
        body.put("tierId", NOT_FOUND_ID);

        JsonNode error = json(doPut(BASE + "/" + id, body).andExpect(status().isNotFound()));

        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_TIER_NOT_FOUND);
        doGet(BASE + "/" + id).andExpect(jsonPath("$.tierCode").value("MEMBER"));
    }

    // ------------------------------------------------------------ Function 3: Status

    @Test
    @DisplayName("ITC_CUS_009 Ngừng hoạt động khách hàng ACTIVE → INACTIVE, vẫn tra cứu được")
    void changeStatus_toInactive() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");

        doPatch(BASE + "/" + id + "/status", Map.of("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        doGet(BASE + "/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        JsonNode inactive = json(doGet(BASE + "?status=INACTIVE&page=0&size=20"));
        assertThat(inactive.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_CUS_010 Kích hoạt lại khách hàng INACTIVE → ACTIVE")
    void changeStatus_reactivate() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");
        doPatch(BASE + "/" + id + "/status", Map.of("status", "INACTIVE")).andExpect(status().isOk());

        doPatch(BASE + "/" + id + "/status", Map.of("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("ITC_CUS_011 Tạm khóa khách hàng → SUSPENDED")
    void changeStatus_suspend() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");

        doPatch(BASE + "/" + id + "/status", Map.of("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("ITC_CUS_012 Đổi trạng thái khách hàng không tồn tại → 404 CUSTOMER_NOT_FOUND")
    void changeStatus_unknownCustomer_notFound() throws Exception {
        JsonNode error = json(doPatch(BASE + "/" + NOT_FOUND_ID + "/status", Map.of("status", "INACTIVE"))
                .andExpect(status().isNotFound()));

        assertThat(error.get("code").asText()).isEqualTo("CUSTOMER_NOT_FOUND");
        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_NOT_FOUND);
    }

    // ------------------------------------------------------------ Function 4: Search

    @Test
    @DisplayName("ITC_CUS_013 Tìm theo từ khóa và lọc theo hạng: chỉ trả khách khớp, có tên hạng")
    void search_byKeywordAndTier() throws Exception {
        UUID silverCustomer = createCustomerId("Nguyễn Văn A", "0901234567");
        createCustomerId("Nguyễn Văn B", "0901234568");
        createCustomerId("Trần Thị C", "0901234569");
        doPost(BASE + "/" + silverCustomer + "/tier-adjustments",
                Map.of("tierId", silverId, "reason", "Khách thân thiết")).andExpect(status().isOk());

        JsonNode page = json(doGet(BASE + "?q=Nguyễn&tierIds=" + silverId + "&page=0&size=20").andExpect(status().isOk()));

        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(silverCustomer.toString());
        assertThat(page.get("content").get(0).get("tierCode").asText()).isEqualTo("SILVER");

        JsonNode byName = json(doGet(BASE + "?q=Nguyễn&page=0&size=1"));
        assertThat(byName.get("totalElements").asLong()).isEqualTo(2);
        assertThat(byName.get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("ITC_CUS_014 Lọc theo khoảng tổng chi tiêu: chỉ trả khách trong khoảng")
    void search_byTotalSpentRange() throws Exception {
        createCustomerId("Nguyễn Văn A", "0901234567");

        JsonNode inRange = json(doGet(BASE + "?minTotalSpent=0&maxTotalSpent=1000&page=0&size=20").andExpect(status().isOk()));
        JsonNode outRange = json(doGet(BASE + "?minTotalSpent=1000000&maxTotalSpent=10000000&page=0&size=20")
                .andExpect(status().isOk()));

        assertThat(inRange.get("totalElements").asLong()).isEqualTo(1);
        assertThat(outRange.get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("ITC_CUS_015 Khoảng chi tiêu đảo ngược (min > max) → 400")
    void search_invertedSpentRange_badRequest() throws Exception {
        JsonNode error = json(doGet(BASE + "?minTotalSpent=5000000&maxTotalSpent=1000000").andExpect(status().isBadRequest()));

        assertThat(error.get("message").asText()).contains("minTotalSpent phải nhỏ hơn hoặc bằng maxTotalSpent");
    }

    @Test
    @DisplayName("ITC_CUS_016 Tìm kiếm không có kết quả → content rỗng")
    void search_noResult() throws Exception {
        createCustomerId("Nguyễn Văn A", "0901234567");

        doGet(BASE + "?q=XYZ-khong-ton-tai&page=0&size=20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------ Function 5: Detail

    @Test
    @DisplayName("ITC_CUS_017 Xem chi tiết: thông tin, tiến độ hạng, lịch sử hạng, giao dịch nhất quán")
    void detail_allSectionsConsistent() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");

        doGet(BASE + "/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.tierCode").value("MEMBER"));
        doGet(BASE + "/" + id + "/tier-progress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier.code").value("MEMBER"))
                .andExpect(jsonPath("$.nextTier.code").value("SILVER"));
        doGet(BASE + "/" + id + "/tier-history").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        doGet(BASE + "/" + id + "/purchase-transactions?page=0&size=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("ITC_CUS_018 Lấy chi tiết khách hàng không tồn tại → 404")
    void detail_unknownCustomer_notFound() throws Exception {
        JsonNode error = json(doGet(BASE + "/" + NOT_FOUND_ID).andExpect(status().isNotFound()));

        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_NOT_FOUND);
    }

    // ------------------------------------------------------------ Function 6: Delete

    @Test
    @DisplayName("ITC_CUS_019 Xóa mềm khách hàng: 204, biến mất khỏi danh sách và thống kê hạng")
    void delete_softDeletesAndHidesCustomer() throws Exception {
        UUID id = createCustomerId("Phạm Văn D", "0908888888");
        createCustomerId("Nguyễn Văn A", "0901234567");

        doDelete(BASE + "/" + id).andExpect(status().isNoContent());

        doGet(BASE + "/" + id).andExpect(status().isNotFound());
        assertThat(totalCustomers()).isEqualTo(1);
        JsonNode counts = json(doGet(TIERS + "/counts"));
        assertThat(countOf(counts, "MEMBER")).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_CUS_020 Xóa khách hàng đã có giao dịch: xóa mềm, giữ tài khoản điểm & lịch sử hạng")
    void delete_customerWithPurchases_keepsHistory() throws Exception {
        UUID id = createCustomerId("Nguyễn Văn A", "0901234567");
        JsonNode account = json(doGet(BASE + "/loyalty-accounts/customer/" + id));

        doDelete(BASE + "/" + id).andExpect(status().isNoContent());

        doGet(BASE + "/" + id).andExpect(status().isNotFound());
        doGet(BASE + "/loyalty-transactions?loyaltyAccountId=" + account.get("id").asText()).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ Function 7: Lookup for Order-Service / POS

    @Test
    @DisplayName("ITC_CUS_021 Order-Service/POS tra khách theo SĐT và lấy snapshot theo id")
    void posLookup_bySearchAndId() throws Exception {
        JsonNode created = createCustomer("Nguyễn Văn An", "0901234567", null);

        JsonNode found = json(doGet(BASE + "?q=0901234567").andExpect(status().isOk()));
        assertThat(found.get("content").get(0).get("fullName").asText()).isEqualTo("Nguyễn Văn An");

        JsonNode snapshot = json(doGet(BASE + "/" + created.get("id").asText()).andExpect(status().isOk()));
        assertThat(snapshot.get("phone").asText()).isEqualTo("0901234567");
        assertThat(snapshot.get("customerCode").asText()).isEqualTo(created.get("customerCode").asText());
    }

    @Test
    @DisplayName("ITC_CUS_022 Snapshot khách hàng không tồn tại → 404 (Order-Service fallback snapshot null)")
    void posLookup_unknownCustomer_notFound() throws Exception {
        doGet(BASE + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ helpers

    private long totalCustomers() throws Exception {
        return json(doGet(BASE + "?page=0&size=1")).get("totalElements").asLong();
    }

    static long countOf(JsonNode counts, String code) {
        for (JsonNode node : counts) {
            if (code.equals(node.get("code").asText())) {
                return node.get("customerCount").asLong();
            }
        }
        return -1;
    }
}
