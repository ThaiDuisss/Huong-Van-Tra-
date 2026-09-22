package org.hvtra.promotionservice.validation.validator;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EventValidator {

    public void validateStatusTransition(Event event, EventStatus targetStatus) {
        EventStatus currentStatus = validateAndGetCurrentStatus(event, targetStatus);

        switch (targetStatus) {
            case DRAFT -> validateCanMoveToDraft();
            case SCHEDULED -> validateCanSchedule(event, currentStatus);
            case ACTIVE -> validateCanActivate(event, currentStatus);
            case PAUSED -> validateCanPause(currentStatus);
            case EXPIRED -> validateCanExpire(event, currentStatus);
            case ENDED -> validateCanEnd(currentStatus);
            case CANCELLED -> validateCanCancel(currentStatus);
        }
    }

    public EventStatus resolveResumeTargetStatus(Event event) {
        validateEventNotNull(event);

        if (event.getStatus() != EventStatus.PAUSED) {
            throw new BusinessException("Only PAUSED event can be resumed");
        }

        validateStartAndEndTime(event);

        LocalDateTime now = LocalDateTime.now();

        if (event.getEndAt() != null && !event.getEndAt().isAfter(now)) {
            throw new BusinessException("Expired event cannot be resumed. Please extend event first");
        }

        if (event.getStartAt() != null && event.getStartAt().isAfter(now)) {
            return EventStatus.SCHEDULED;
        }

        return EventStatus.ACTIVE;
    }

    public void validateBeforeUpdate(Event event) {
        validateEventNotNull(event);

        EventStatus status = event.getStatus();

        if (status == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be updated");
        }

        if (status == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be updated");
        }

        if (status == EventStatus.EXPIRED) {
            throw new BusinessException("Expired event cannot be updated. Please extend event first");
        }

        if (status == EventStatus.ACTIVE) {
            throw new BusinessException("Active event cannot be updated. Please pause, cancel, end or extend it first");
        }
    }

    public void validateBeforeDelete(Event event) {
        validateEventNotNull(event);

        EventStatus status = event.getStatus();

        if (status == EventStatus.ACTIVE) {
            throw new BusinessException("Active event cannot be deleted. Please cancel or end it first");
        }

        if (status == EventStatus.PAUSED) {
            throw new BusinessException("Paused event cannot be deleted. Please cancel or end it first");
        }

        if (status == EventStatus.EXPIRED) {
            throw new BusinessException("Expired event cannot be deleted. Please end it first");
        }

        if (status == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be deleted");
        }

        if (status == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be deleted");
        }
    }

    public void validateCanExtend(Event event, LocalDateTime newEndAt) {
        validateEventNotNull(event);

        EventStatus status = event.getStatus();

        if (status == EventStatus.DRAFT) {
            throw new BusinessException("Draft event does not need to be extended. Please update event time instead");
        }

        if (status == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be extended");
        }

        if (status == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be extended");
        }

        if (newEndAt == null) {
            throw new BusinessException("New end time is required");
        }

        LocalDateTime now = LocalDateTime.now();

        if (!newEndAt.isAfter(now)) {
            throw new BusinessException("New end time must be in the future");
        }

        if (event.getStartAt() != null && !newEndAt.isAfter(event.getStartAt())) {
            throw new BusinessException("New end time must be after start time");
        }

        if (event.getEndAt() != null && !newEndAt.isAfter(event.getEndAt())) {
            throw new BusinessException("New end time must be after current end time");
        }
    }

    private EventStatus validateAndGetCurrentStatus(Event event, EventStatus targetStatus) {
        validateEventNotNull(event);

        if (targetStatus == null) {
            throw new BusinessException("Target event status is required");
        }

        EventStatus currentStatus = event.getStatus();

        if (currentStatus == null) {
            throw new BusinessException("Current event status is required");
        }

        if (currentStatus == targetStatus) {
            throw new BusinessException("Event is already " + targetStatus.name());
        }

        if (currentStatus == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot change status");
        }

        if (currentStatus == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot change status");
        }

        return currentStatus;
    }

    private void validateEventNotNull(Event event) {
        if (event == null) {
            throw new BusinessException("Event is required");
        }
    }

    private void validateCanMoveToDraft() {
        throw new BusinessException("Event cannot be moved back to DRAFT");
    }

    private void validateCanSchedule(Event event, EventStatus currentStatus) {
        if (currentStatus != EventStatus.DRAFT && currentStatus != EventStatus.PAUSED) {
            throw new BusinessException("Only DRAFT or PAUSED event can be scheduled");
        }

        if (event.getStartAt() == null) {
            throw new BusinessException("Start time is required to schedule event");
        }

        validateStartAndEndTime(event);

        if (!event.getStartAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Start time must be in the future to schedule event");
        }
    }

    private void validateCanActivate(Event event, EventStatus currentStatus) {
        if (currentStatus != EventStatus.DRAFT
                && currentStatus != EventStatus.SCHEDULED
                && currentStatus != EventStatus.PAUSED
                && currentStatus != EventStatus.EXPIRED) {
            throw new BusinessException("Only DRAFT, SCHEDULED, PAUSED or EXPIRED event can be activated");
        }

        validateStartAndEndTime(event);

        LocalDateTime now = LocalDateTime.now();

        if (event.getEndAt() != null && !event.getEndAt().isAfter(now)) {
            throw new BusinessException("Expired event cannot be activated. Please extend event first");
        }

        if (event.getStartAt() != null && event.getStartAt().isAfter(now)) {
            throw new BusinessException("Event has not started yet");
        }
    }

    private void validateCanPause(EventStatus currentStatus) {
        if (currentStatus != EventStatus.ACTIVE) {
            throw new BusinessException("Only ACTIVE event can be paused");
        }
    }

    private void validateCanExpire(Event event, EventStatus currentStatus) {
        if (currentStatus != EventStatus.SCHEDULED
                && currentStatus != EventStatus.ACTIVE
                && currentStatus != EventStatus.PAUSED) {
            throw new BusinessException("Only SCHEDULED, ACTIVE or PAUSED event can be expired");
        }

        if (event.getEndAt() == null) {
            throw new BusinessException("End time is required to expire event");
        }

        if (event.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Event has not reached end time yet");
        }

        validateStartAndEndTime(event);
    }

    private void validateCanEnd(EventStatus currentStatus) {
        if (currentStatus != EventStatus.ACTIVE
                && currentStatus != EventStatus.PAUSED
                && currentStatus != EventStatus.EXPIRED) {
            throw new BusinessException("Only ACTIVE, PAUSED or EXPIRED event can be ended");
        }
    }

    private void validateCanCancel(EventStatus currentStatus) {
        if (currentStatus != EventStatus.DRAFT
                && currentStatus != EventStatus.SCHEDULED
                && currentStatus != EventStatus.ACTIVE
                && currentStatus != EventStatus.PAUSED) {
            throw new BusinessException("Only DRAFT, SCHEDULED, ACTIVE or PAUSED event can be cancelled");
        }
    }

    private void validateStartAndEndTime(Event event) {
        if (event.getStartAt() != null
                && event.getEndAt() != null
                && !event.getStartAt().isBefore(event.getEndAt())) {
            throw new BusinessException("Start time must be before end time");
        }
    }
}