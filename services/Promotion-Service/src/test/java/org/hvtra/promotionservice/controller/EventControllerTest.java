package org.hvtra.promotionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.event.EventExtendRequest;
import org.hvtra.promotionservice.dto.request.event.EventUpdateRequest;
import org.hvtra.promotionservice.dto.response.EventResponse;
import org.hvtra.promotionservice.dto.response.PageResponse;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.service.EventService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventController endpoint contract")
class EventControllerTest {

    private static final String BASE_PATH = "/api/promotions/events";
    private static final UUID EVENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime START_AT = LocalDateTime.of(2027, 2, 1, 0, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2027, 2, 15, 23, 59, 59);

    @Mock
    EventService eventService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventController(eventService))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Nested
    @DisplayName("create event")
    class CreateEvent {

        @Test
        @DisplayName("POST /events creates event with valid request")
        void createEvent_returnsCreatedBusinessCode() throws Exception {
            when(eventService.createEvent(any(EventCreateRequest.class)))
                    .thenReturn(response(EventStatus.DRAFT));

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1001))
                    .andExpect(jsonPath("$.data.id").value(EVENT_ID.toString()))
                    .andExpect(jsonPath("$.data.name").value("Tet Holiday Campaign 2027"))
                    .andExpect(jsonPath("$.data.eventType").value("SEASONAL_CAMPAIGN"))
                    .andExpect(jsonPath("$.data.audience").value("ALL_CUSTOMERS"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));

            verify(eventService).createEvent(any(EventCreateRequest.class));
        }

        @Test
        @DisplayName("POST /events rejects missing required fields")
        void createEvent_rejectsMissingRequiredFields() throws Exception {
            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /events rejects invalid enum value")
        void createEvent_rejectsInvalidEnum() throws Exception {
            String body = """
                    {
                      "name": "Tet Holiday Campaign 2027",
                      "description": "Campaign for all customers",
                      "startAt": "2027-02-01T00:00:00",
                      "endAt": "2027-02-15T23:59:59",
                      "eventType": "SEASONAL",
                      "audience": "ALL_CUSTOMERS"
                    }
                    """;

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /events rejects invalid time range")
        void createEvent_rejectsEndBeforeStart() throws Exception {
            EventCreateRequest request = EventCreateRequest.builder()
                    .name("Tet Holiday Campaign 2027")
                    .description("Campaign for all customers")
                    .startAt(END_AT)
                    .endAt(START_AT)
                    .eventType(EventType.SEASONAL_CAMPAIGN)
                    .audience(EventAudience.ALL_CUSTOMERS)
                    .build();

            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("read event")
    class ReadEvent {

        @Test
        @DisplayName("GET /events/{eventId} returns event detail")
        void getEventById_returnsDetail() throws Exception {
            when(eventService.getEventById(EVENT_ID)).thenReturn(response(EventStatus.ACTIVE));

            mockMvc.perform(get(BASE_PATH + "/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.data.id").value(EVENT_ID.toString()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(eventService).getEventById(EVENT_ID);
        }

        @Test
        @DisplayName("GET /events rejects invalid UUID path variable")
        void getEventById_rejectsInvalidUuid() throws Exception {
            mockMvc.perform(get(BASE_PATH + "/{eventId}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("list and filter events")
    class ListEvents {

        @Test
        @DisplayName("GET /events returns paged events with all supported filters")
        void getAllEvents_supportsFilters() throws Exception {
            PageResponse<EventResponse> page = PageResponse.from(new PageImpl<>(List.of(response(EventStatus.ACTIVE))));
            when(eventService.getEvents(any(), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get(BASE_PATH)
                            .param("keyword", "tet")
                            .param("status", "ACTIVE")
                            .param("eventType", "SEASONAL_CAMPAIGN")
                            .param("audience", "ALL_CUSTOMERS")
                            .param("startFrom", "2027-02-01T00:00:00")
                            .param("startTo", "2027-02-28T23:59:59")
                            .param("endFrom", "2027-02-01T00:00:00")
                            .param("endTo", "2027-02-28T23:59:59")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.data.content[0].id").value(EVENT_ID.toString()))
                    .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));

            verify(eventService).getEvents(any(), eq(1), eq(20));
        }

        @Test
        @DisplayName("GET /events rejects invalid filter enum value")
        void getAllEvents_rejectsInvalidFilterEnum() throws Exception {
            mockMvc.perform(get(BASE_PATH)
                            .param("status", "RUNNING"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /events/active returns only active events")
        void getActiveEvents_returnsPagedActiveEvents() throws Exception {
            PageResponse<EventResponse> page = PageResponse.from(new PageImpl<>(List.of(response(EventStatus.ACTIVE))));
            when(eventService.getActiveEvents(0, 10)).thenReturn(page);

            mockMvc.perform(get(BASE_PATH + "/active")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));

            verify(eventService).getActiveEvents(0, 10);
        }
    }

    @Nested
    @DisplayName("update and delete event")
    class UpdateAndDeleteEvent {

        @Test
        @DisplayName("PUT /events/{eventId} updates event")
        void updateEvent_returnsUpdatedBusinessCode() throws Exception {
            when(eventService.updateEvent(eq(EVENT_ID), any(EventUpdateRequest.class)))
                    .thenReturn(response(EventStatus.DRAFT));

            mockMvc.perform(put(BASE_PATH + "/{eventId}", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1002))
                    .andExpect(jsonPath("$.data.id").value(EVENT_ID.toString()));

            verify(eventService).updateEvent(eq(EVENT_ID), any(EventUpdateRequest.class));
        }

        @Test
        @DisplayName("PUT /events/{eventId} rejects blank name")
        void updateEvent_rejectsBlankName() throws Exception {
            EventUpdateRequest request = EventUpdateRequest.builder()
                    .name(" ")
                    .description("Updated campaign")
                    .startAt(START_AT)
                    .endAt(END_AT)
                    .eventType(EventType.FLASH_SALE)
                    .audience(EventAudience.CUSTOMER_SEGMENT)
                    .build();

            mockMvc.perform(put(BASE_PATH + "/{eventId}", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /events/{eventId} soft deletes event")
        void deleteEvent_returnsDeletedBusinessCode() throws Exception {
            mockMvc.perform(delete(BASE_PATH + "/{eventId}", EVENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1003));

            verify(eventService).deleteEvent(EVENT_ID);
        }
    }

    @Nested
    @DisplayName("lifecycle actions")
    class LifecycleActions {

        @Test
        @DisplayName("POST /events/{eventId}/schedule schedules event")
        void scheduleEvent_returnsUpdatedEvent() throws Exception {
            assertLifecycleEndpoint("/schedule", EventStatus.SCHEDULED);
        }

        @Test
        @DisplayName("POST /events/{eventId}/activate activates event")
        void activateEvent_returnsUpdatedEvent() throws Exception {
            assertLifecycleEndpoint("/activate", EventStatus.ACTIVE);
        }

        @Test
        @DisplayName("POST /events/{eventId}/pause pauses event")
        void pauseEvent_returnsUpdatedEvent() throws Exception {
            assertLifecycleEndpoint("/pause", EventStatus.PAUSED);
        }

        @Test
        @DisplayName("POST /events/{eventId}/resume resumes event")
        void resumeEvent_returnsUpdatedEvent() throws Exception {
            assertLifecycleEndpoint("/resume", EventStatus.ACTIVE);
        }

        @Test
        @DisplayName("POST /events/{eventId}/cancel cancels event")
        void cancelEvent_returnsUpdatedEvent() throws Exception {
            assertLifecycleEndpoint("/cancel", EventStatus.CANCELLED);
        }

        @Test
        @DisplayName("POST /events/{eventId}/end ends event")
        void endEvent_returnsUpdatedEvent() throws Exception {
            assertLifecycleEndpoint("/end", EventStatus.ENDED);
        }

        @Test
        @DisplayName("PATCH /events/{eventId}/extend extends event")
        void extendEvent_returnsUpdatedEvent() throws Exception {
            when(eventService.extendEvent(eq(EVENT_ID), any(EventExtendRequest.class)))
                    .thenReturn(response(EventStatus.ACTIVE));

            mockMvc.perform(patch(BASE_PATH + "/{eventId}/extend", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(EventExtendRequest.builder()
                                    .endAt(END_AT.plusDays(5))
                                    .build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.code").value(1002))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(eventService).extendEvent(eq(EVENT_ID), any(EventExtendRequest.class));
        }

        @Test
        @DisplayName("PATCH /events/{eventId}/extend rejects missing endAt")
        void extendEvent_rejectsMissingEndAt() throws Exception {
            mockMvc.perform(patch(BASE_PATH + "/{eventId}/extend", EVENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    private void assertLifecycleEndpoint(String suffix, EventStatus expectedStatus) throws Exception {
        switch (suffix) {
            case "/schedule" -> when(eventService.scheduleEvent(EVENT_ID)).thenReturn(response(expectedStatus));
            case "/activate" -> when(eventService.activateEvent(EVENT_ID)).thenReturn(response(expectedStatus));
            case "/pause" -> when(eventService.pauseEvent(EVENT_ID)).thenReturn(response(expectedStatus));
            case "/resume" -> when(eventService.resumeEvent(EVENT_ID)).thenReturn(response(expectedStatus));
            case "/cancel" -> when(eventService.cancelEvent(EVENT_ID)).thenReturn(response(expectedStatus));
            case "/end" -> when(eventService.endEvent(EVENT_ID)).thenReturn(response(expectedStatus));
            default -> throw new IllegalArgumentException("Unsupported lifecycle suffix: " + suffix);
        }

        mockMvc.perform(post(BASE_PATH + "/{eventId}" + suffix, EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.data.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value(expectedStatus.name()));
    }

    private EventCreateRequest createRequest() {
        return EventCreateRequest.builder()
                .name("Tet Holiday Campaign 2027")
                .description("Campaign for all customers")
                .startAt(START_AT)
                .endAt(END_AT)
                .eventType(EventType.SEASONAL_CAMPAIGN)
                .audience(EventAudience.ALL_CUSTOMERS)
                .build();
    }

    private EventUpdateRequest updateRequest() {
        return EventUpdateRequest.builder()
                .name("Tet Holiday Campaign 2027 Updated")
                .description("Updated campaign")
                .startAt(START_AT)
                .endAt(END_AT)
                .eventType(EventType.FLASH_SALE)
                .audience(EventAudience.CUSTOMER_SEGMENT)
                .build();
    }

    private EventResponse response(EventStatus status) {
        return EventResponse.builder()
                .id(EVENT_ID)
                .name("Tet Holiday Campaign 2027")
                .description("Campaign for all customers")
                .startAt(START_AT)
                .endAt(END_AT)
                .eventType(EventType.SEASONAL_CAMPAIGN)
                .audience(EventAudience.ALL_CUSTOMERS)
                .status(status)
                .isDeleted(false)
                .build();
    }
}
