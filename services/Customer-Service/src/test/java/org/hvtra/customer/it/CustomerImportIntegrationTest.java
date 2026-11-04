package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sheet CustomerImport_Management – ITC_IMP_xxx. */
@DisplayName("IT – Nhập khách hàng từ Excel (CustomerImport_Management)")
class CustomerImportIntegrationTest extends CustomerIntegrationTestSupport {

    private static final String IMPORT = BASE + "/import";

    @Test
    @DisplayName("ITC_IMP_001 Xem trước (dryRun) file hợp lệ: tất cả VALID, không lưu DB")
    void preview_validFile_noPersist() throws Exception {
        JsonNode result = json(doPost(IMPORT, request(true,
                row(2, "Nguyễn Văn A", "0911111111", "a@example.com"),
                row(3, "Trần Thị B", "0911111112", null),
                row(4, "Lê Văn C", "0911111113", null))).andExpect(status().isOk()));

        assertThat(result.get("dryRun").asBoolean()).isTrue();
        assertThat(result.get("totalRows").asInt()).isEqualTo(3);
        assertThat(result.get("successCount").asInt()).isEqualTo(3);
        assertThat(statuses(result)).containsOnly("VALID");
        assertThat(totalCustomers()).isZero();
    }

    @Test
    @DisplayName("ITC_IMP_002 Xem trước file có dòng sai định dạng: INVALID kèm lý do từng dòng")
    void preview_invalidFormats() throws Exception {
        Map<String, Object> badPhone = row(2, "A", "09abc", null);
        Map<String, Object> badEmail = row(3, "B", "0911111112", "not-an-email");
        Map<String, Object> badDob = row(4, "C", "0911111113", null);
        badDob.put("dateOfBirth", "31/02/2000");
        Map<String, Object> badType = row(5, "D", "0911111114", null);
        badType.put("customerType", "VIP");

        JsonNode result = json(doPost(IMPORT, request(true, badPhone, badEmail, badDob, badType)).andExpect(status().isOk()));

        assertThat(result.get("failedCount").asInt()).isEqualTo(4);
        assertThat(statuses(result)).containsOnly("INVALID");
        assertThat(messages(result)).containsExactly(
                "Số điện thoại không đúng định dạng (8-15 chữ số, có thể bắt đầu bằng +)",
                "Email không đúng định dạng",
                "Ngày sinh không đúng định dạng (yyyy-MM-dd hoặc dd/MM/yyyy)",
                "Loại khách hàng không hợp lệ (Cá nhân hoặc Doanh nghiệp)");
    }

    @Test
    @DisplayName("ITC_IMP_003 Trùng trong file (SĐT chuẩn hóa) và trùng với DB")
    void preview_duplicatesInFileAndDatabase() throws Exception {
        createCustomer("Khách cũ", "0901234567", null);

        JsonNode result = json(doPost(IMPORT, request(true,
                row(2, "A", "0911111111", null),
                row(3, "B", "091.111.1111", null),
                row(4, "C", "0901234567", null))).andExpect(status().isOk()));

        assertThat(statuses(result)).containsExactly("VALID", "INVALID", "INVALID");
        assertThat(messages(result).get(1)).isEqualTo("Số điện thoại trùng với dòng 2 trong file");
        assertThat(messages(result).get(2)).isEqualTo("Số điện thoại đã tồn tại");
        assertThat(totalCustomers()).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_IMP_004 Nhập thật: dòng hợp lệ được tạo (kèm hạng MEMBER & tài khoản điểm), dòng lỗi bỏ qua")
    void import_partialSuccess() throws Exception {
        JsonNode result = json(doPost(IMPORT, request(false,
                row(2, "Nguyễn Văn A", "0911111111", null),
                row(3, "Trần Thị B", "0911111112", null),
                row(4, "", "0911111113", null))).andExpect(status().isOk()));

        assertThat(result.get("successCount").asInt()).isEqualTo(2);
        assertThat(result.get("failedCount").asInt()).isEqualTo(1);
        assertThat(statuses(result)).containsExactly("IMPORTED", "IMPORTED", "INVALID");
        assertThat(messages(result).get(2)).isEqualTo("Tên khách hàng là bắt buộc");

        String importedId = result.get("results").get(0).get("customerId").asText();
        doGet(BASE + "/" + importedId).andExpect(status().isOk());
        doGet(BASE + "/loyalty-accounts/customer/" + importedId).andExpect(status().isOk());
        assertThat(totalCustomers()).isEqualTo(2);
        assertThat(CustomerManagementIntegrationTest.countOf(json(doGet(TIERS + "/counts")), "MEMBER")).isEqualTo(2);
    }

    @Test
    @DisplayName("ITC_IMP_005 Nhập danh sách rỗng → 400")
    void import_emptyRows_badRequest() throws Exception {
        JsonNode error = json(doPost(IMPORT, Map.of("dryRun", false, "rows", List.of())).andExpect(status().isBadRequest()));

        assertThat(error.get("message").asText()).contains("Danh sách khách hàng cần nhập đang trống");
    }

    @Test
    @DisplayName("ITC_IMP_006 Nhập vượt 500 dòng → 400, không tạo khách hàng")
    void import_tooManyRows_badRequest() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row(i + 2, "Khách " + i, String.format("09%08d", i), null));
        }

        JsonNode error = json(doPost(IMPORT, Map.of("dryRun", false, "rows", rows)).andExpect(status().isBadRequest()));

        assertThat(error.get("message").asText()).contains("Mỗi lần chỉ được nhập tối đa 500 khách hàng");
        assertThat(totalCustomers()).isZero();
    }

    // ------------------------------------------------------------ helpers

    @SafeVarargs
    private static Map<String, Object> request(boolean dryRun, Map<String, Object>... rows) {
        return Map.of("dryRun", dryRun, "rows", List.of(rows));
    }

    private static Map<String, Object> row(int rowNumber, String fullName, String phone, String email) {
        Map<String, Object> row = new HashMap<>();
        row.put("rowNumber", rowNumber);
        row.put("fullName", fullName);
        row.put("phone", phone);
        if (email != null) {
            row.put("email", email);
        }
        return row;
    }

    private static List<String> statuses(JsonNode result) {
        List<String> values = new ArrayList<>();
        result.get("results").forEach(node -> values.add(node.get("status").asText()));
        return values;
    }

    private static List<String> messages(JsonNode result) {
        List<String> values = new ArrayList<>();
        result.get("results").forEach(node -> values.add(node.get("message").asText()));
        return values;
    }

    private long totalCustomers() throws Exception {
        return json(doGet(BASE + "?page=0&size=1")).get("totalElements").asLong();
    }
}
