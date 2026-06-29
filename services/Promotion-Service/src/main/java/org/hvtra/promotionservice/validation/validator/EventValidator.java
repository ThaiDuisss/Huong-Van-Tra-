package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.event.EventStatusUpdateRequest;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventValidator {

    public void validateStatusTransition(Event event, EventStatusUpdateRequest request) {
        EventStatus currentStatus = event.getStatus();
        EventStatus nextStatus = request.getStatus();

        if (currentStatus == nextStatus) {
            throw new BusinessException("Event is already in status: " + nextStatus);
        }

        if (currentStatus == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be updated to another status");
        }

        if (currentStatus == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be updated to another status");
        }

        if (nextStatus == EventStatus.ACTIVE && event.getEndAt() != null && event.getEndAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException("Expired event cannot be activated");
        }
    }

    public void validateBeforeUpdate(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be updated");
        }

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be updated");
        }
    }

    public void validateBeforeDelete(Event event) {
        if (event.getStatus() == EventStatus.ACTIVE) {
            throw new BusinessException("Active event cannot be deleted. Please cancel or end it first");
        }
    }

    public void validateBeforeActivate(Event event) {
        if (event.getStatus() == EventStatus.ACTIVE) {
            throw new BusinessException("Event is already active");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be activated");
        }

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be activated");
        }

        if (event.getEndAt() != null && event.getEndAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException("Expired event cannot be activated");
        }
    }

    public void validateBeforeCancel(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Event is already cancelled");
        }

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Ended event cannot be cancelled");
        }
    }

    public void validateBeforeEnd(Event event) {
        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Event is already ended");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Cancelled event cannot be ended");
        }
    }
}
