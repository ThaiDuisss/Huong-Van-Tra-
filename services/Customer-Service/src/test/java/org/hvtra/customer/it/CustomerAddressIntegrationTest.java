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

/** Sheet CustomerAddress_Management – ITC_ADR_xxx. */
@DisplayName("IT – Địa chỉ khách hàng (CustomerAddress_Management)")
class CustomerAddressIntegrationTest extends CustomerIntegrationTestSupport {

    @Test
    @DisplayName("ITC_ADR_001 Tạo địa chỉ mặc định mới: địa chỉ mặc định cũ bị bỏ cờ")
    void create_defaultAddress_unsetsPreviousDefault() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        String first = createAddress(customerId, "1 Lê Lợi", true);

        JsonNode second = json(doPost(ADDRESSES, address(customerId, "12 Nguyễn Huệ", true)).andExpect(status().isCreated()));

        assertThat(second.get("id").asText()).matches("^[0-9a-fA-F-]{36}$");
        assertThat(second.get("isDefault").asBoolean()).isTrue();
        doGet(ADDRESSES + "/" + first).andExpect(jsonPath("$.isDefault").value(false));
        assertThat(defaultCount(customerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_ADR_002 Tạo địa chỉ cho khách hàng không tồn tại → 404")
    void create_unknownCustomer_notFound() throws Exception {
        JsonNode error = json(doPost(ADDRESSES, address(UUID.fromString(NOT_FOUND_ID), "1 Lê Lợi", false))
                .andExpect(status().isNotFound()));

        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_NOT_FOUND);
    }

    @Test
    @DisplayName("ITC_ADR_003 Cập nhật địa chỉ thành mặc định: chỉ còn 1 địa chỉ mặc định")
    void update_toDefault() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        String first = createAddress(customerId, "12 Nguyễn Huệ", true);
        String second = createAddress(customerId, "5 Hai Bà Trưng", false);

        doPut(ADDRESSES + "/" + second, address(customerId, "1 Lê Lợi", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        doGet(ADDRESSES + "/" + first).andExpect(jsonPath("$.isDefault").value(false));
        assertThat(defaultCount(customerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ITC_ADR_004 Lấy danh sách địa chỉ theo khách hàng: chỉ của khách đó")
    void list_byCustomer() throws Exception {
        UUID customerA = createCustomerId("Nguyễn Văn An", "0901234567");
        UUID customerB = createCustomerId("Trần Thị B", "0901234568");
        createAddress(customerA, "1 Lê Lợi", true);
        createAddress(customerA, "2 Lê Lợi", false);
        createAddress(customerB, "3 Lê Lợi", true);

        doGet(ADDRESSES + "?customerId=" + customerA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("ITC_ADR_005 Xóa mềm địa chỉ: 204, GET sau đó 404, không còn trong danh sách")
    void delete_softDeletes() throws Exception {
        UUID customerId = createCustomerId("Nguyễn Văn An", "0901234567");
        createAddress(customerId, "1 Lê Lợi", true);
        String second = createAddress(customerId, "2 Lê Lợi", false);

        doDelete(ADDRESSES + "/" + second).andExpect(status().isNoContent());

        JsonNode error = json(doGet(ADDRESSES + "/" + second).andExpect(status().isNotFound()));
        assertThat(error.get("message").asText()).isEqualTo(Message.CUSTOMER_ADDRESS_NOT_FOUND);
        doGet(ADDRESSES + "?customerId=" + customerId).andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------ helpers

    private String createAddress(UUID customerId, String line, boolean isDefault) throws Exception {
        return json(doPost(ADDRESSES, address(customerId, line, isDefault)).andExpect(status().isCreated()))
                .get("id").asText();
    }

    private static Map<String, Object> address(UUID customerId, String line, boolean isDefault) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("recipientName", "Nguyễn Văn An");
        body.put("recipientPhone", "0901234567");
        body.put("addressLine", line);
        body.put("province", "Hồ Chí Minh");
        body.put("district", "Quận 1");
        body.put("ward", "Bến Nghé");
        body.put("isDefault", isDefault);
        return body;
    }

    private long defaultCount(UUID customerId) throws Exception {
        long count = 0;
        for (JsonNode node : json(doGet(ADDRESSES + "?customerId=" + customerId))) {
            if (node.get("isDefault").asBoolean()) {
                count++;
            }
        }
        return count;
    }
}
