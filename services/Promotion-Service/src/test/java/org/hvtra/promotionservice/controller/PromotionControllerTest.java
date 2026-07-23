package org.hvtra.promotionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.dto.response.PromotionResponse;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionController endpoint contract")
class PromotionControllerTest {

    private static final String BASE_PATH = "/api/promotions";
    private static final UUID EVENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PROMOTION_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime START_AT = LocalDateTime.of(2027, 2, 1, 0, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2027, 2, 10, 23, 59, 59);

    @Mock
    PromotionService promotionService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PromotionController(promotionService))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Nested
    @DisplayName("create promotion")
    class CreatePromotion {

        @Test
        @DisplayName("POST /promotions creates promotion with valid request")
        void createPromotion_returnsCreatedBusinessCode() throws Exception {
            when(promotionService.createPromotion(any(PromotionCreateRequest.class)))
                    .thenReturn(response(PromotionStatus.DRAFT));

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest(EVENT_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1001))
                    .andExpect(jsonPath("$.data.id").value(PROMOTION_ID.toString()))
                    .andExpect(jsonPath("$.data.eventId").value(EVENT_ID.toString()))
                    .andExpect(jsonPath("$.data.type").value("PERCENT_DISCOUNT"))
                    .andExpect(jsonPath("$.data.applyMethod").value("AUTO"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(promotionService).createPromotion(any(PromotionCreateRequest.class));
        }

        @Test
        @DisplayName("POST /promotions/events/{eventId}/promotions creates promotion using path event")
        void createPromotionByEvent_returnsCreatedBusinessCode() throws Exception {
            when(promotionService.createPromotionByEvent(eq(EVENT_ID), any(PromotionCreateRequest.class)))
                    .thenReturn(response(PromotionStatus.DRAFT));

            mockMvc.perform(post(BASE_PATH + "/events/{eventId}/promotions", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest(null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1001))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(promotionService).createPromotionByEvent(eq(EVENT_ID), any(PromotionCreateRequest.class));
        }

        @Test
        @DisplayName("POST /promotions rejects missing required fields")
        void createPromotion_rejectsMissingRequiredFields() throws Exception {
            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /promotions rejects invalid enum value")
        void createPromotion_rejectsInvalidEnum() throws Exception {
            String body = """
                    {
                      "eventId": "550e8400-e29b-41d4-a716-446655440000",
                      "name": "Tet discount",
                      "type": "PERCENT",
                      "applyMethod": "AUTO",
                      "startAt": "2027-02-01T00:00:00",
                      "endAt": "2027-02-10T23:59:59"
                    }
                    """;

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /promotions rejects invalid time range")
        void createPromotion_rejectsEndBeforeStart() throws Exception {
            PromotionCreateRequest request = PromotionCreateRequest.builder()
                    .eventId(EVENT_ID)
                    .name("Tet discount")
                    .type(PromotionType.PERCENT_DISCOUNT)
                    .applyMethod(PromotionApplyMethod.AUTO)
                    .startAt(END_AT)
                    .endAt(START_AT)
                    .build();

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("read and list promotions")
    class ReadAndListPromotions {

        @Test
        @DisplayName("GET /promotions/{promotionId} returns promotion detail")
        void getPromotionById_returnsDetail() throws Exception {
            when(promotionService.getPromotionById(PROMOTION_ID)).thenReturn(response(PromotionStatus.ACTIVE));

            mockMvc.perform(get(BASE_PATH + "/{promotionId}", PROMOTION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.data.id").value(PROMOTION_ID.toString()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(promotionService).getPromotionById(PROMOTION_ID);
        }

        @Test
        @DisplayName("GET /promotions/{promotionId} rejects invalid UUID")
        void getPromotionById_rejectsInvalidUuid() throws Exception {
            mockMvc.perform(get(BASE_PATH + "/{promotionId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /promotions returns paged promotions with all supported filters")
        void getPromotions_supportsFilters() throws Exception {
            PageResponse<PromotionResponse> page = PageResponse.from(new PageImpl<>(List.of(response(PromotionStatus.ACTIVE))));
            when(promotionService.getPromotions(any(), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get(BASE_PATH)
                            .param("keyword", "tet")
                            .param("eventId", EVENT_ID.toString())
                            .param("type", "PERCENT_DISCOUNT")
                            .param("applyMethod", "AUTO")
                            .param("status", "ACTIVE")
                            .param("startFrom", "2027-02-01T00:00:00")
                            .param("startTo", "2027-02-28T23:59:59")
                            .param("endFrom", "2027-02-01T00:00:00")
                            .param("endTo", "2027-02-28T23:59:59")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.data.content[0].id").value(PROMOTION_ID.toString()))
                    .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));

            verify(promotionService).getPromotions(any(), eq(1), eq(20));
        }

        @Test
        @DisplayName("GET /promotions rejects invalid filter enum value")
        void getPromotions_rejectsInvalidFilterEnum() throws Exception {
            mockMvc.perform(get(BASE_PATH)
                            .param("status", "INACTIVE"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /promotions/events/{eventId}/promotions returns event promotions")
        void getPromotionsByEvent_returnsPagedPromotions() throws Exception {
            PageResponse<PromotionResponse> page = PageResponse.from(new PageImpl<>(List.of(response(PromotionStatus.SCHEDULED))));
            when(promotionService.getPromotionsByEvent(eq(EVENT_ID), any(), eq(0), eq(10))).thenReturn(page);

            mockMvc.perform(get(BASE_PATH + "/events/{eventId}/promotions", EVENT_ID)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.content[0].status").value("SCHEDULED"));

            verify(promotionService).getPromotionsByEvent(eq(EVENT_ID), any(), eq(0), eq(10));
        }
    }

    @Nested
    @DisplayName("update and delete promotion")
    class UpdateAndDeletePromotion {

        @Test
        @DisplayName("PUT /promotions/{promotionId} updates promotion")
        void updatePromotion_returnsUpdatedBusinessCode() throws Exception {
            when(promotionService.updatePromotion(eq(PROMOTION_ID), any(PromotionUpdateRequest.class)))
                    .thenReturn(response(PromotionStatus.DRAFT));

            mockMvc.perform(put(BASE_PATH + "/{promotionId}", PROMOTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1002))
                    .andExpect(jsonPath("$.data.id").value(PROMOTION_ID.toString()));

            verify(promotionService).updatePromotion(eq(PROMOTION_ID), any(PromotionUpdateRequest.class));
        }

        @Test
        @DisplayName("PUT /promotions/{promotionId} rejects blank name")
        void updatePromotion_rejectsBlankName() throws Exception {
            PromotionUpdateRequest request = PromotionUpdateRequest.builder()
                    .name(" ")
                    .type(PromotionType.PERCENT_DISCOUNT)
                    .applyMethod(PromotionApplyMethod.AUTO)
                    .startAt(START_AT)
                    .endAt(END_AT)
                    .build();

            mockMvc.perform(put(BASE_PATH + "/{promotionId}", PROMOTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /promotions/{promotionId} soft deletes promotion")
        void deletePromotion_returnsDeletedBusinessCode() throws Exception {
            mockMvc.perform(delete(BASE_PATH + "/{promotionId}", PROMOTION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1003));

            verify(promotionService).deletePromotion(PROMOTION_ID);
        }
    }

    @Nested
    @DisplayName("lifecycle actions")
    class LifecycleActions {

        @Test
        @DisplayName("POST /promotions/{promotionId}/schedule schedules promotion")
        void schedulePromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/schedule", PromotionStatus.SCHEDULED);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/activate activates promotion")
        void activatePromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/activate", PromotionStatus.ACTIVE);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/pause pauses promotion")
        void pausePromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/pause", PromotionStatus.PAUSED);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/resume resumes promotion")
        void resumePromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/resume", PromotionStatus.ACTIVE);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/cancel cancels promotion")
        void cancelPromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/cancel", PromotionStatus.CANCELLED);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/expire expires promotion")
        void expirePromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/expire", PromotionStatus.EXPIRED);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/end ends promotion")
        void endPromotion_returnsUpdatedPromotion() throws Exception {
            assertLifecycleEndpoint("/end", PromotionStatus.ENDED);
        }

        @Test
        @DisplayName("POST /promotions/{promotionId}/duplicate duplicates promotion")
        void duplicatePromotion_returnsCreatedBusinessCode() throws Exception {
            when(promotionService.duplicatePromotion(PROMOTION_ID)).thenReturn(response(PromotionStatus.DRAFT));

            mockMvc.perform(post(BASE_PATH + "/{promotionId}/duplicate", PROMOTION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1001))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(promotionService).duplicatePromotion(PROMOTION_ID);
        }
    }

    private void assertLifecycleEndpoint(String suffix, PromotionStatus expectedStatus) throws Exception {
        switch (suffix) {
            case "/schedule" -> when(promotionService.schedulePromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            case "/activate" -> when(promotionService.activatePromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            case "/pause" -> when(promotionService.pausePromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            case "/resume" -> when(promotionService.resumePromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            case "/cancel" -> when(promotionService.cancelPromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            case "/expire" -> when(promotionService.expirePromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            case "/end" -> when(promotionService.endPromotion(PROMOTION_ID)).thenReturn(response(expectedStatus));
            default -> throw new IllegalArgumentException("Unsupported lifecycle suffix: " + suffix);
        }

        mockMvc.perform(post(BASE_PATH + "/{promotionId}" + suffix, PROMOTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.data.id").value(PROMOTION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value(expectedStatus.name()));
    }

    private PromotionCreateRequest createRequest(UUID eventId) {
        return PromotionCreateRequest.builder()
                .eventId(eventId)
                .name("Tet 20 percent discount")
                .description("Discount for Tet campaign")
                .objective("Increase POS revenue")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.AUTO)
                .startAt(START_AT)
                .endAt(END_AT)
                .stackable(false)
                .priority(5)
                .build();
    }

    private PromotionUpdateRequest updateRequest() {
        return PromotionUpdateRequest.builder()
                .name("Updated promotion")
                .description("Updated description")
                .objective("Increase loyalty")
                .type(PromotionType.FIXED_DISCOUNT)
                .applyMethod(PromotionApplyMethod.COUPON_CODE)
                .startAt(START_AT)
                .endAt(END_AT)
                .stackable(true)
                .priority(10)
                .build();
    }

    private PromotionResponse response(PromotionStatus status) {
        return PromotionResponse.builder()
                .id(PROMOTION_ID)
                .eventId(EVENT_ID)
                .eventName("Tet Holiday Campaign 2027")
                .name("Tet 20 percent discount")
                .description("Discount for Tet campaign")
                .objective("Increase POS revenue")
                .type(PromotionType.PERCENT_DISCOUNT)
                .applyMethod(PromotionApplyMethod.AUTO)
                .startAt(START_AT)
                .endAt(END_AT)
                .stackable(false)
                .priority(5)
                .status(status)
                .isDeleted(false)
                .build();
    }
}
