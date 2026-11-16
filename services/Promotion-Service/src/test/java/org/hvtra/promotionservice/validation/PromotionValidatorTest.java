package org.hvtra.promotionservice.validation;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.validation.validator.PromotionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PromotionValidator")
class PromotionValidatorTest {

    final PromotionValidator validator = new PromotionValidator();

    final LocalDateTime now = LocalDateTime.now();

    Event event(EventStatus status) {
        return Event.builder().name("Event").status(status)
                .startAt(now.minusDays(10)).endAt(now.plusDays(30)).build();
    }

    Promotion promotion(PromotionStatus status, LocalDateTime startAt, LocalDateTime endAt, Event event) {
        return Promotion.builder().name("Promotion").status(status).startAt(startAt).endAt(endAt).event(event).build();
    }

    /** A promotion that is inside its time window and belongs to an ACTIVE event. */
    Promotion running(PromotionStatus status) {
        return promotion(status, now.minusDays(1), now.plusDays(5), event(EventStatus.ACTIVE));
    }

    /** A promotion that starts in the future and belongs to an ACTIVE event. */
    Promotion upcoming(PromotionStatus status) {
        return promotion(status, now.plusDays(1), now.plusDays(5), event(EventStatus.ACTIVE));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("validateCreateRequest needs an event id")
        void createRequest_needsEventId() {
            assertThatThrownBy(() -> validator.validateCreateRequest(new PromotionCreateRequest()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Event ID is required");
            assertThatCode(() -> validator.validateCreateRequest(
                    PromotionCreateRequest.builder().eventId(UUID.randomUUID()).build())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a missing event or an event without status is rejected")
        void eventCanCreatePromotion_missingEvent() {
            assertThatThrownBy(() -> validator.validateEventCanCreatePromotion(null))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is required when creating promotion");
            assertThatThrownBy(() -> validator.validateEventCanCreatePromotion(event(null)))
                    .isInstanceOf(BusinessException.class).hasMessage("Event status is required");
        }

        @ParameterizedTest(name = "{0} event cannot receive new promotions")
        @CsvSource({"CANCELLED, cancelled", "ENDED, ended", "EXPIRED, expired"})
        void eventCanCreatePromotion_closedEvent(EventStatus status, String word) {
            assertThatThrownBy(() -> validator.validateEventCanCreatePromotion(event(status)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Cannot create promotion under " + word + " event");
        }

        @ParameterizedTest(name = "{0} event can receive new promotions")
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "SCHEDULED", "ACTIVE", "PAUSED"})
        void eventCanCreatePromotion_openEvent(EventStatus status) {
            assertThatCode(() -> validator.validateEventCanCreatePromotion(event(status))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("promotion time window inside the event")
    class TimeWithinEvent {

        final LocalDateTime eventStart = LocalDateTime.of(2027, 1, 1, 0, 0);
        final LocalDateTime eventEnd = LocalDateTime.of(2027, 1, 31, 23, 59);
        final Event event = Event.builder().startAt(eventStart).endAt(eventEnd).build();

        PromotionCreateRequest request(LocalDateTime startAt, LocalDateTime endAt) {
            return PromotionCreateRequest.builder().startAt(startAt).endAt(endAt).build();
        }

        @Test
        @DisplayName("start must be strictly before end")
        void startBeforeEnd() {
            LocalDateTime sameInstant = eventStart.plusDays(1);

            assertThatThrownBy(() -> validator.validatePromotionTimeWithinEvent(request(sameInstant, sameInstant), event))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion start time must be before promotion end time");
            assertThatThrownBy(() -> validator.validatePromotionTimeWithinEvent(
                    request(eventStart.plusDays(5), eventStart.plusDays(4)), event))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion start time must be before promotion end time");
        }

        @Test
        @DisplayName("start before the event start is rejected")
        void startBeforeEventStart() {
            assertThatThrownBy(() -> validator.validatePromotionTimeWithinEvent(
                    request(eventStart.minusSeconds(1), eventEnd), event))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion start time must not be before event start time");
        }

        @Test
        @DisplayName("end after the event end is rejected")
        void endAfterEventEnd() {
            assertThatThrownBy(() -> validator.validatePromotionTimeWithinEvent(
                    request(eventStart, eventEnd.plusSeconds(1)), event))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion end time must not be after event end time");
        }

        @Test
        @DisplayName("a window equal to the event window is accepted")
        void sameWindowAsEvent() {
            assertThatCode(() -> validator.validatePromotionTimeWithinEvent(request(eventStart, eventEnd), event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("missing promotion or event bounds are not compared")
        void missingBoundsAreSkipped() {
            Event openEvent = Event.builder().build();

            assertThatCode(() -> validator.validatePromotionTimeWithinEvent(request(null, null), event))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validatePromotionTimeWithinEvent(
                    request(eventStart.minusYears(1), eventEnd.plusYears(1)), openEvent)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("update, delete and duplicate guards")
    class EditGuards {

        @ParameterizedTest(name = "{0} promotion cannot be updated")
        @CsvSource({"CANCELLED, Cancelled", "ENDED, Ended", "EXPIRED, Expired", "ACTIVE, Active"})
        void update_rejected(PromotionStatus status, String word) {
            assertThatThrownBy(() -> validator.validateBeforeUpdate(running(status)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageStartingWith(word + " promotion cannot be updated");
        }

        @ParameterizedTest(name = "{0} promotion can be updated")
        @EnumSource(value = PromotionStatus.class, names = {"DRAFT", "SCHEDULED", "PAUSED"})
        void update_allowed(PromotionStatus status) {
            assertThatCode(() -> validator.validateBeforeUpdate(running(status))).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} promotion cannot be deleted")
        @CsvSource({"ACTIVE, Active", "PAUSED, Paused", "EXPIRED, Expired", "ENDED, Ended", "CANCELLED, Cancelled"})
        void delete_rejected(PromotionStatus status, String word) {
            assertThatThrownBy(() -> validator.validateBeforeDelete(running(status)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageStartingWith(word + " promotion cannot be deleted");
        }

        @ParameterizedTest(name = "{0} promotion can be deleted")
        @EnumSource(value = PromotionStatus.class, names = {"DRAFT", "SCHEDULED"})
        void delete_allowed(PromotionStatus status) {
            assertThatCode(() -> validator.validateBeforeDelete(running(status))).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} promotion cannot be duplicated")
        @CsvSource({"CANCELLED, Cancelled", "ENDED, Ended"})
        void duplicate_rejected(PromotionStatus status, String word) {
            assertThatThrownBy(() -> validator.validateBeforeDuplicate(running(status)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(word + " promotion cannot be duplicated");
        }

        @ParameterizedTest(name = "{0} promotion can be duplicated")
        @EnumSource(value = PromotionStatus.class, names = {"CANCELLED", "ENDED"}, mode = EnumSource.Mode.EXCLUDE)
        void duplicate_allowed(PromotionStatus status) {
            assertThatCode(() -> validator.validateBeforeDuplicate(running(status))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("resolveResumeTargetStatus")
    class Resume {

        @ParameterizedTest(name = "{0} promotion cannot be resumed")
        @EnumSource(value = PromotionStatus.class, names = "PAUSED", mode = EnumSource.Mode.EXCLUDE)
        void onlyPausedCanResume(PromotionStatus status) {
            assertThatThrownBy(() -> validator.resolveResumeTargetStatus(running(status)))
                    .isInstanceOf(BusinessException.class).hasMessage("Only paused promotion can be resumed");
        }

        @Test
        @DisplayName("a paused promotion past its end time must be extended first")
        void expiredByTime_rejected() {
            Promotion promotion = promotion(PromotionStatus.PAUSED, now.minusDays(5), now.minusSeconds(1),
                    event(EventStatus.ACTIVE));

            assertThatThrownBy(() -> validator.resolveResumeTargetStatus(promotion))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("extend promotion first");
        }

        @Test
        @DisplayName("resumes to SCHEDULED before the start time and to ACTIVE inside the window")
        void resolvesByStartTime() {
            assertThat(validator.resolveResumeTargetStatus(upcoming(PromotionStatus.PAUSED)))
                    .isEqualTo(PromotionStatus.SCHEDULED);
            assertThat(validator.resolveResumeTargetStatus(running(PromotionStatus.PAUSED)))
                    .isEqualTo(PromotionStatus.ACTIVE);
        }

        @Test
        @DisplayName("a paused promotion without time bounds resumes to ACTIVE")
        void noBounds_active() {
            assertThat(validator.resolveResumeTargetStatus(promotion(PromotionStatus.PAUSED, null, null, null)))
                    .isEqualTo(PromotionStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("validateStatusTransition")
    class StatusTransition {

        @Test
        @DisplayName("promotion, current status and target status are all required")
        void requiredArguments() {
            assertThatThrownBy(() -> validator.validateStatusTransition(null, PromotionStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Promotion is required");
            assertThatThrownBy(() -> validator.validateStatusTransition(running(null), PromotionStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Current promotion status is required");
            assertThatThrownBy(() -> validator.validateStatusTransition(running(PromotionStatus.DRAFT), null))
                    .isInstanceOf(BusinessException.class).hasMessage("Target promotion status is required");
        }

        @ParameterizedTest(name = "{0} -> {0} is rejected as a no-op")
        @EnumSource(PromotionStatus.class)
        void sameStatus_rejected(PromotionStatus status) {
            assertThatThrownBy(() -> validator.validateStatusTransition(running(status), status))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion is already in status: " + status);
        }

        @ParameterizedTest(name = "{0} may only move to [{1}]")
        @CsvSource({
                "DRAFT, SCHEDULED|ACTIVE|CANCELLED",
                "SCHEDULED, ACTIVE|PAUSED|EXPIRED|CANCELLED|ENDED",
                "ACTIVE, PAUSED|EXPIRED|CANCELLED|ENDED",
                "PAUSED, ACTIVE|SCHEDULED|EXPIRED|CANCELLED|ENDED",
                "EXPIRED, ACTIVE|ENDED"
        })
        void transitionMatrix(PromotionStatus current, String allowedTargets) {
            List<PromotionStatus> allowed = Arrays.stream(allowedTargets.split("\\|"))
                    .map(PromotionStatus::valueOf).toList();

            for (PromotionStatus target : PromotionStatus.values()) {
                if (target == current) {
                    continue;
                }
                // Time window and event always satisfy the target, so only the transition table decides.
                Promotion promotion = target == PromotionStatus.SCHEDULED ? upcoming(current) : running(current);

                if (allowed.contains(target)) {
                    assertThatCode(() -> validator.validateStatusTransition(promotion, target))
                            .as("%s -> %s should be allowed", current, target).doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> validator.validateStatusTransition(promotion, target))
                            .as("%s -> %s should be rejected", current, target)
                            .isInstanceOf(BusinessException.class)
                            .hasMessage("Invalid promotion status transition: " + current + " -> " + target);
                }
            }
        }

        @ParameterizedTest(name = "{0} promotion can never change status")
        @CsvSource({"ENDED, Ended", "CANCELLED, Cancelled"})
        void closedPromotion_cannotChange(PromotionStatus current, String word) {
            for (PromotionStatus target : PromotionStatus.values()) {
                if (target == current) {
                    continue;
                }
                assertThatThrownBy(() -> validator.validateStatusTransition(running(current), target))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage(word + " promotion cannot change status");
            }
        }
    }

    @Nested
    @DisplayName("scheduling preconditions")
    class Schedule {

        @Test
        @DisplayName("a start time is required")
        void startRequired() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, null, now.plusDays(5), event(EventStatus.ACTIVE));

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class).hasMessage("Promotion start time is required to schedule");
        }

        @Test
        @DisplayName("a start time that already passed cannot be scheduled")
        void startInPast_rejected() {
            assertThatThrownBy(() -> validator.validateStatusTransition(running(PromotionStatus.DRAFT),
                    PromotionStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion start time must be in the future to schedule");
        }

        @Test
        @DisplayName("an end time that already passed cannot be scheduled")
        void endInPast_rejected() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, now.plusDays(1), now.minusDays(1),
                    event(EventStatus.ACTIVE));

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class).hasMessage("Expired promotion cannot be scheduled");
        }

        @Test
        @DisplayName("the promotion must belong to an event")
        void eventRequired() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, now.plusDays(1), now.plusDays(5), null);

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class).hasMessage("Promotion event is required");
        }

        @ParameterizedTest(name = "cannot schedule under a {0} event")
        @CsvSource({
                "CANCELLED, Promotion cannot be scheduled under cancelled event",
                "ENDED, Promotion cannot be scheduled under ended event",
                "EXPIRED, Promotion cannot be scheduled under expired event",
                "DRAFT, Promotion can only be scheduled when its event is scheduled or active",
                "PAUSED, Promotion can only be scheduled when its event is scheduled or active"
        })
        void eventStatus_rejected(EventStatus eventStatus, String message) {
            Promotion promotion = promotion(PromotionStatus.DRAFT, now.plusDays(1), now.plusDays(5), event(eventStatus));

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class).hasMessage(message);
        }

        @ParameterizedTest(name = "can schedule under a {0} event")
        @EnumSource(value = EventStatus.class, names = {"SCHEDULED", "ACTIVE"})
        void eventStatus_allowed(EventStatus eventStatus) {
            Promotion draft = promotion(PromotionStatus.DRAFT, now.plusDays(1), null, event(eventStatus));
            Promotion paused = promotion(PromotionStatus.PAUSED, now.plusDays(1), now.plusDays(5), event(eventStatus));

            assertThatCode(() -> validator.validateStatusTransition(draft, PromotionStatus.SCHEDULED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validateStatusTransition(paused, PromotionStatus.SCHEDULED))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("activation preconditions")
    class Activate {

        @ParameterizedTest(name = "{0} promotion that has not started yet must be scheduled instead")
        @EnumSource(value = PromotionStatus.class, names = {"DRAFT", "SCHEDULED", "PAUSED", "EXPIRED"})
        void startInFuture_rejected(PromotionStatus current) {
            assertThatThrownBy(() -> validator.validateStatusTransition(upcoming(current), PromotionStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion start time has not arrived yet. Please schedule it instead");
        }

        @ParameterizedTest(name = "{0} promotion past its end time cannot be activated")
        @EnumSource(value = PromotionStatus.class, names = {"DRAFT", "SCHEDULED", "PAUSED", "EXPIRED"})
        void endInPast_rejected(PromotionStatus current) {
            Promotion promotion = promotion(current, now.minusDays(5), now.minusSeconds(1), event(EventStatus.ACTIVE));

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Expired promotion cannot be activated");
        }

        @Test
        @DisplayName("the promotion must belong to an event")
        void eventRequired() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, now.minusDays(1), now.plusDays(5), null);

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Promotion event is required");
        }

        @ParameterizedTest(name = "cannot activate under a {0} event")
        @EnumSource(value = EventStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void eventNotActive_rejected(EventStatus eventStatus) {
            Promotion promotion = promotion(PromotionStatus.SCHEDULED, now.minusDays(1), now.plusDays(5),
                    event(eventStatus));

            assertThatThrownBy(() -> validator.validateStatusTransition(promotion, PromotionStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Promotion can only be activated when its event is active");
        }

        @Test
        @DisplayName("an open-ended promotion under an ACTIVE event can be activated")
        void openEnded_allowed() {
            Promotion promotion = promotion(PromotionStatus.DRAFT, null, null, event(EventStatus.ACTIVE));

            assertThatCode(() -> validator.validateStatusTransition(promotion, PromotionStatus.ACTIVE))
                    .doesNotThrowAnyException();
        }
    }
}
