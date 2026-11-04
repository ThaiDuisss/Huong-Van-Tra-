package org.hvtra.customer.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.domain.constants.HeaderConstants;
import org.hvtra.customer.infrastructure.client.OrderClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền cho integration test Customer-Service: khởi động toàn bộ Spring context
 * (controller → service → repository → H2), gọi API qua MockMvc. Chỉ Order-Service
 * (OpenFeign {@link OrderClient}) được mock vì nằm ngoài service.
 * Mỗi test bắt đầu với DB sạch và 3 hạng MEMBER (0đ/0 đơn), SILVER (1.000.000đ/3 đơn), GOLD (5.000.000đ/10 đơn).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public abstract class CustomerIntegrationTestSupport {

    protected static final String BASE = "/api/customer";
    protected static final String TIERS = BASE + "/tiers";
    protected static final String ADDRESSES = BASE + "/addresses";
    protected static final String RULES = BASE + "/loyalty-point-rules";
    protected static final String NOT_FOUND_ID = "00000000-0000-0000-0000-000000000000";

    private static final List<String> TABLES = List.of(
            "loyalty_transactions", "loyalty_accounts", "loyalty_point_rules",
            "customer_purchase_transactions", "customer_tier_yearly_metrics", "customer_tier_assignments",
            "customer_addresses", "customers", "customer_tiers", "business_sequences");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected OrderClient orderClient;

    @Value("${security.internal.header-secret}")
    protected String headerSecret;

    /** Người thao tác mặc định: quản lý (ROLE_MANAGER), request đi qua Gateway nên có header ký hợp lệ. */
    protected String actorId = "11111111-1111-1111-1111-111111111111";
    protected String actorRoles = "MANAGER";
    protected boolean signRequests = true;

    protected UUID memberId;
    protected UUID silverId;
    protected UUID goldId;

    @BeforeEach
    void resetDatabase() throws Exception {
        signRequests = true;
        actorRoles = "MANAGER";
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : TABLES) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        memberId = createTier("MEMBER", "Thành viên", 0, 0);
        silverId = createTier("SILVER", "Bạc", 1_000_000, 3);
        goldId = createTier("GOLD", "Vàng", 5_000_000, 10);
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        if (signRequests) {
            signAsGateway(builder, actorId, actorRoles, headerSecret);
        }
        return mockMvc.perform(builder);
    }

    /** Gắn header nội bộ giống InternalUserContextRelayFilter của Api-Gateway (HMAC-SHA256). */
    protected static void signAsGateway(MockHttpServletRequestBuilder builder, String userId, String roles, String secret)
            throws Exception {
        signAsGateway(builder, userId, roles, roles, secret);
    }

    /** Ký với {@code signedRoles} nhưng gửi header roles là {@code sentRoles} (mô phỏng sửa header sau khi ký). */
    protected static void signAsGateway(MockHttpServletRequestBuilder builder, String userId, String signedRoles,
                                        String sentRoles, String secret) throws Exception {
        String correlationId = UUID.randomUUID().toString();
        String authorization = "Bearer it-token";
        String raw = String.join("\n", userId, signedRoles, correlationId, authorization);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        builder.header(HeaderConstants.HEADER_USER_ID, userId)
                .header(HeaderConstants.HEADER_USER_ROLES, sentRoles)
                .header(HeaderConstants.HEADER_CORRELATION_ID, correlationId)
                .header(HeaderConstants.HEADER_AUTHOR, authorization)
                .header(HeaderConstants.HEADER_SIGNATURE, signature);
    }

    protected ResultActions doGet(String url) throws Exception {
        return perform(get(url), null);
    }

    protected ResultActions doPost(String url, Object body) throws Exception {
        return perform(post(url), body);
    }

    protected ResultActions doPut(String url, Object body) throws Exception {
        return perform(put(url), body);
    }

    protected ResultActions doPatch(String url, Object body) throws Exception {
        return perform(patch(url), body);
    }

    protected ResultActions doDelete(String url) throws Exception {
        return perform(delete(url), null);
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    // ------------------------------------------------------------------ Fixtures

    protected UUID createTier(String code, String name, long minSpending, int minOrderCount) throws Exception {
        JsonNode tier = json(doPost(TIERS, Map.of(
                "code", code,
                "name", name,
                "minSpending", minSpending,
                "minOrderCount", minOrderCount,
                "status", "ACTIVE",
                "colorCode", "#6B7280"))
                .andExpect(status().isCreated()));
        return UUID.fromString(tier.get("id").asText());
    }

    protected Map<String, Object> customerBody(String fullName, String phone, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", fullName);
        body.put("phone", phone);
        if (email != null) {
            body.put("email", email);
        }
        return body;
    }

    protected JsonNode createCustomer(String fullName, String phone, String email) throws Exception {
        return json(doPost(BASE, customerBody(fullName, phone, email)).andExpect(status().isCreated()));
    }

    protected UUID createCustomerId(String fullName, String phone) throws Exception {
        return UUID.fromString(createCustomer(fullName, phone, null).get("id").asText());
    }
}
