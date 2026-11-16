package org.hvtra.promotionservice.validation;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.validation.validator.EventValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventValidator")
class EventValidatorTest {

    final EventValidator validator = new EventValidator();

    final LocalDateTime now = LocalDateTime.now();

    Event event(EventStatus status, LocalDateTime startAt, LocalDateTime endAt) {
        return Event.builder().name("Event").status(status).startAt(startAt).endAt(endAt).build();
    }

    /** Started yesterday, ends in five days. */
    Event running(EventStatus status) {
        return event(status, now.minusDays(1), now.plusDays(5));
    }

    /** Starts tomorrow, ends in five days. */
    Event upcoming(EventStatus status) {
        return event(status, now.plusDays(1), now.plusDays(5));
    }

    /** Ended yesterday. */
    Event finished(EventStatus status) {
        return event(status, now.minusDays(5), now.minusDays(1));
    }

    /** An event whose time window satisfies every time precondition of the given target status. */
    Event eventFitting(EventStatus current, EventStatus target) {
        return switch (target) {
            case SCHEDULED -> upcoming(current);
            case EXPIRED -> finished(current);
            default -> running(current);
        };
    }

    @Nested
    @DisplayName("validateStatusTransition")
    class StatusTransition {

        @Test
        @DisplayName("event, target status and current status are all required")
        void requiredArguments() {
            assertThatThrownBy(() -> validator.validateStatusTransition(null, EventStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is required");
            assertThatThrownBy(() -> validator.validateStatusTransition(running(EventStatus.DRAFT), null))
                    .isInstanceOf(BusinessException.class).hasMessage("Target event status is required");
            assertThatThrownBy(() -> validator.validateStatusTransition(running(null), EventStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Current event status is required");
        }

        @ParameterizedTest(name = "{0} -> {0} is rejected as a no-op")
        @EnumSource(EventStatus.class)
        void sameStatus_rejected(EventStatus status) {
            assertThatThrownBy(() -> validator.validateStatusTransition(running(status), status))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is already " + status);
        }

        @ParameterizedTest(name = "{0} event can never change status")
        @CsvSource({"ENDED, Ended", "CANCELLED, Cancelled"})
        void closedEvent_cannotChange(EventStatus current, String word) {
            for (EventStatus target : EventStatus.values()) {
                if (target == current) {
                    continue;
                }
                assertThatThrownBy(() -> validator.validateStatusTransition(eventFitting(current, target), target))
                        .isInstanceOf(BusinessException.class).hasMessage(word + " event cannot change status");
            }
        }

        @ParameterizedTest(name = "{0} cannot go back to DRAFT")
        @EnumSource(value = EventStatus.class, names = {"SCHEDULED", "ACTIVE", "PAUSED", "EXPIRED"})
        void backToDraft_rejected(EventStatus current) {
            assertThatThrownBy(() -> validator.validateStatusTransition(running(current), EventStatus.DRAFT))
                    .isInstanceOf(BusinessException.class).hasMessage("Event cannot be moved back to DRAFT");
        }

        @ParameterizedTest(name = "{0} may only be reached from [{1}]")
        @CsvSource({
                "SCHEDULED, DRAFT|PAUSED, Only DRAFT or PAUSED event can be scheduled",
                "ACTIVE, DRAFT|SCHEDULED|PAUSED|EXPIRED, 'Only DRAFT, SCHEDULED, PAUSED or EXPIRED event can be activated'",
                "PAUSED, ACTIVE, Only ACTIVE event can be paused",
                "EXPIRED, SCHEDULED|ACTIVE|PAUSED, 'Only SCHEDULED, ACTIVE or PAUSED event can be expired'",
                "ENDED, ACTIVE|PAUSED|EXPIRED, 'Only ACTIVE, PAUSED or EXPIRED event can be ended'",
                "CANCELLED, DRAFT|SCHEDULED|ACTIVE|PAUSED, 'Only DRAFT, SCHEDULED, ACTIVE or PAUSED event can be cancelled'"
        })
        void transitionMatrix(EventStatus target, String allowedSources, String rejectionMessage) {
            List<EventStatus> allowed = Arrays.stream(allowedSources.split("\\|")).map(EventStatus::valueOf).toList();
            List<EventStatus> openStatuses = List.of(EventStatus.DRAFT, EventStatus.SCHEDULED, EventStatus.ACTIVE,
                    EventStatus.PAUSED, EventStatus.EXPIRED);

            for (EventStatus current : openStatuses) {
                if (current == target) {
                    continue;
                }
                Event event = eventFitting(current, target);

                if (allowed.contains(current)) {
                    assertThatCode(() -> validator.validateStatusTransition(event, target))
                            .as("%s -> %s should be allowed", current, target).doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> validator.validateStatusTransition(event, target))
                            .as("%s -> %s should be rejected", current, target)
                            .isInstanceOf(BusinessException.class).hasMessage(rejectionMessage);
                }
            }
        }
    }

    @Nested
    @DisplayName("time preconditions of a transition")
    class TimePreconditions {

        @Test
        @DisplayName("scheduling needs a start time")
        void schedule_startRequired() {
            assertThatThrownBy(() -> validator.validateStatusTransition(
                    event(EventStatus.DRAFT, null, now.plusDays(5)), EventStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class).hasMessage("Start time is required to schedule event");
        }

        @Test
        @DisplayName("scheduling needs start before end")
        void schedule_startBeforeEnd() {
            LocalDateTime sameInstant = now.plusDays(2);

            assertThatThrownBy(() -> validator.validateStatusTransition(
                    event(EventStatus.DRAFT, sameInstant, sameInstant), EventStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class).hasMessage("Start time must be before end time");
        }

        @Test
        @DisplayName("scheduling needs a start time in the future, an end time is optional")
        void schedule_startInFuture() {
            assertThatThrownBy(() -> validator.validateStatusTransition(running(EventStatus.DRAFT), EventStatus.SCHEDULED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Start time must be in the future to schedule event");
            assertThatCode(() -> validator.validateStatusTransition(
                    event(EventStatus.DRAFT, now.plusDays(1), null), EventStatus.SCHEDULED)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("activation needs start before end")
        void activate_startBeforeEnd() {
            assertThatThrownBy(() -> validator.validateStatusTransition(
                    event(EventStatus.DRAFT, now.plusDays(3), now.plusDays(2)), EventStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Start time must be before end time");
        }

        @ParameterizedTest(name = "{0} event past its end time must be extended before activation")
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "SCHEDULED", "PAUSED", "EXPIRED"})
        void activate_endInPast(EventStatus current) {
            assertThatThrownBy(() -> validator.validateStatusTransition(finished(current), EventStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Expired event cannot be activated. Please extend event first");
        }

        @Test
        @DisplayName("activation before the start time is rejected")
        void activate_notStarted() {
            assertThatThrownBy(() -> validator.validateStatusTransition(upcoming(EventStatus.SCHEDULED), EventStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class).hasMessage("Event has not started yet");
        }

        @Test
        @DisplayName("an event without time bounds can be activated")
        void activate_noBounds() {
            assertThatCode(() -> validator.validateStatusTransition(
                    event(EventStatus.DRAFT, null, null), EventStatus.ACTIVE)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("expiring needs an end time that has been reached")
        void expire_endReached() {
            assertThatThrownBy(() -> validator.validateStatusTransition(
                    event(EventStatus.ACTIVE, now.minusDays(1), null), EventStatus.EXPIRED))
                    .isInstanceOf(BusinessException.class).hasMessage("End time is required to expire event");
            assertThatThrownBy(() -> validator.validateStatusTransition(running(EventStatus.ACTIVE), EventStatus.EXPIRED))
                    .isInstanceOf(BusinessException.class).hasMessage("Event has not reached end time yet");
        }

        @Test
        @DisplayName("expiring an event whose start is not before its end is rejected")
        void expire_startBeforeEnd() {
            assertThatThrownBy(() -> validator.validateStatusTransition(
                    event(EventStatus.ACTIVE, now.minusDays(1), now.minusDays(2)), EventStatus.EXPIRED))
                    .isInstanceOf(BusinessException.class).hasMessage("Start time must be before end time");
        }
    }

    @Nested
    @DisplayName("resolveResumeTargetStatus")
    class Resume {

        @Test
        @DisplayName("the event is required")
        void eventRequired() {
            assertThatThrownBy(() -> validator.resolveResumeTargetStatus(null))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is required");
        }

        @ParameterizedTest(name = "{0} event cannot be resumed")
        @EnumSource(value = EventStatus.class, names = "PAUSED", mode = EnumSource.Mode.EXCLUDE)
        void onlyPausedCanResume(EventStatus status) {
            assertThatThrownBy(() -> validator.resolveResumeTargetStatus(running(status)))
                    .isInstanceOf(BusinessException.class).hasMessage("Only PAUSED event can be resumed");
        }

        @Test
        @DisplayName("an inconsistent time window is rejected")
        void startBeforeEnd() {
            assertThatThrownBy(() -> validator.resolveResumeTargetStatus(
                    event(EventStatus.PAUSED, now.plusDays(3), now.plusDays(2))))
                    .isInstanceOf(BusinessException.class).hasMessage("Start time must be before end time");
        }

        @Test
        @DisplayName("a paused event past its end time must be extended first")
        void expiredByTime() {
            assertThatThrownBy(() -> validator.resolveResumeTargetStatus(finished(EventStatus.PAUSED)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Expired event cannot be resumed. Please extend event first");
        }

        @Test
        @DisplayName("resumes to SCHEDULED before the start time, otherwise to ACTIVE")
        void resolvesByStartTime() {
            assertThat(validator.resolveResumeTargetStatus(upcoming(EventStatus.PAUSED))).isEqualTo(EventStatus.SCHEDULED);
            assertThat(validator.resolveResumeTargetStatus(running(EventStatus.PAUSED))).isEqualTo(EventStatus.ACTIVE);
            assertThat(validator.resolveResumeTargetStatus(event(EventStatus.PAUSED, null, null)))
                    .isEqualTo(EventStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("update and delete guards")
    class EditGuards {

        @Test
        @DisplayName("the event is required")
        void eventRequired() {
            assertThatThrownBy(() -> validator.validateBeforeUpdate(null))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is required");
            assertThatThrownBy(() -> validator.validateBeforeDelete(null))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is required");
        }

        @ParameterizedTest(name = "{0} event cannot be updated")
        @CsvSource({"CANCELLED, Cancelled", "ENDED, Ended", "EXPIRED, Expired", "ACTIVE, Active"})
        void update_rejected(EventStatus status, String word) {
            assertThatThrownBy(() -> validator.validateBeforeUpdate(running(status)))
                    .isInstanceOf(BusinessException.class).hasMessageStartingWith(word + " event cannot be updated");
        }

        @ParameterizedTest(name = "{0} event can be updated")
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "SCHEDULED", "PAUSED"})
        void update_allowed(EventStatus status) {
            assertThatCode(() -> validator.validateBeforeUpdate(running(status))).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} event cannot be deleted")
        @CsvSource({"ACTIVE, Active", "PAUSED, Paused", "EXPIRED, Expired", "ENDED, Ended", "CANCELLED, Cancelled"})
        void delete_rejected(EventStatus status, String word) {
            assertThatThrownBy(() -> validator.validateBeforeDelete(running(status)))
                    .isInstanceOf(BusinessException.class).hasMessageStartingWith(word + " event cannot be deleted");
        }

        @ParameterizedTest(name = "{0} event can be deleted")
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "SCHEDULED"})
        void delete_allowed(EventStatus status) {
            assertThatCode(() -> validator.validateBeforeDelete(running(status))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateCanExtend")
    class Extend {

        @Test
        @DisplayName("the event is required")
        void eventRequired() {
            assertThatThrownBy(() -> validator.validateCanExtend(null, now.plusDays(10)))
                    .isInstanceOf(BusinessException.class).hasMessage("Event is required");
        }

        @ParameterizedTest(name = "{0} event cannot be extended")
        @CsvSource({
                "DRAFT, Draft event does not need to be extended. Please update event time instead",
                "ENDED, Ended event cannot be extended",
                "CANCELLED, Cancelled event cannot be extended"
        })
        void status_rejected(EventStatus status, String message) {
            assertThatThrownBy(() -> validator.validateCanExtend(running(status), now.plusDays(10)))
                    .isInstanceOf(BusinessException.class).hasMessage(message);
        }

        @Test
        @DisplayName("the new end time is required and must be in the future")
        void newEnd_requiredAndFuture() {
            assertThatThrownBy(() -> validator.validateCanExtend(running(EventStatus.ACTIVE), null))
                    .isInstanceOf(BusinessException.class).hasMessage("New end time is required");
            assertThatThrownBy(() -> validator.validateCanExtend(finished(EventStatus.EXPIRED), now.minusHours(1)))
                    .isInstanceOf(BusinessException.class).hasMessage("New end time must be in the future");
        }

        @Test
        @DisplayName("the new end time must be after the start time")
        void newEnd_afterStart() {
            Event event = event(EventStatus.SCHEDULED, now.plusDays(3), null);

            assertThatThrownBy(() -> validator.validateCanExtend(event, now.plusDays(3)))
                    .isInstanceOf(BusinessException.class).hasMessage("New end time must be after start time");
            assertThatThrownBy(() -> validator.validateCanExtend(event, now.plusDays(2)))
                    .isInstanceOf(BusinessException.class).hasMessage("New end time must be after start time");
        }

        @Test
        @DisplayName("the new end time must be after the current end time")
        void newEnd_afterCurrentEnd() {
            Event event = running(EventStatus.ACTIVE);

            assertThatThrownBy(() -> validator.validateCanExtend(event, event.getEndAt()))
                    .isInstanceOf(BusinessException.class).hasMessage("New end time must be after current end time");
            assertThatThrownBy(() -> validator.validateCanExtend(event, event.getEndAt().minusDays(1)))
                    .isInstanceOf(BusinessException.class).hasMessage("New end time must be after current end time");
        }

        @ParameterizedTest(name = "{0} event can be extended to a later end time")
        @EnumSource(value = EventStatus.class, names = {"SCHEDULED", "ACTIVE", "PAUSED", "EXPIRED"})
        void extend_allowed(EventStatus status) {
            Event event = status == EventStatus.EXPIRED ? finished(status) : running(status);

            assertThatCode(() -> validator.validateCanExtend(event, now.plusDays(10))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an event without time bounds can be extended to any future time")
        void extend_noBounds() {
            assertThatCode(() -> validator.validateCanExtend(event(EventStatus.ACTIVE, null, null), now.plusMinutes(1)))
                    .doesNotThrowAnyException();
        }
    }
}
