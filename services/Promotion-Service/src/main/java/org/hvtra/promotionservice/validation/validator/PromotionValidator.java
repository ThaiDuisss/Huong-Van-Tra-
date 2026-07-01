package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionStatusUpdateRequest;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionValidator {

    public void validateCreateRequest(PromotionCreateRequest request) {
        if (request.getEventId() == null) {
            throw new BusinessException("Event ID is required when creating promotion from /promotions endpoint");
        }
    }

    public void validateEventCanCreatePromotion(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Cannot create promotion under cancelled event");
        }

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Cannot create promotion under ended event");
        }
    }

    public void validatePromotionTimeWithinEvent(PromotionCreateRequest request, Event event) {
        if (request.getStartAt().isBefore(event.getStartAt())) {
            throw new BusinessException("Promotion start time must not be before event start time");
        }

        if (event.getEndAt() != null && request.getEndAt().isAfter(event.getEndAt())) {
            throw new BusinessException("Promotion end time must not be after event end time");
        }
    }

    public void validateBeforeUpdate(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be updated");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Expired promotion cannot be updated");
        }
    }

    public void validateStatusTransition(Promotion promotion, PromotionStatusUpdateRequest request) {
        PromotionStatus currentStatus = promotion.getStatus();
        PromotionStatus nextStatus = request.getStatus();

        if (currentStatus == nextStatus) {
            throw new BusinessException("Promotion is already in status: " + nextStatus);
        }

        if (currentStatus == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be updated to another status");
        }

        if (currentStatus == PromotionStatus.EXPIRED) {
            throw new BusinessException("Expired promotion cannot be updated to another status");
        }

        if (nextStatus == PromotionStatus.ACTIVE
                && promotion.getEndAt() != null
                && promotion.getEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Expired promotion cannot be activated");
        }

        Event event = promotion.getEvent();

        if (nextStatus == PromotionStatus.ACTIVE && event.getStatus() != EventStatus.ACTIVE) {
            throw new BusinessException("Promotion can only be activated when its event is active");
        }
    }

    public void validateBeforeDelete(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new BusinessException("Active promotion cannot be deleted. Please deactivate or cancel it first");
        }
    }

    public void validateBeforeActivate(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new BusinessException("Promotion is already active");
        }

        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be activated");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Expired promotion cannot be activated");
        }

        if (promotion.getEndAt() != null && promotion.getEndAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Expired promotion cannot be activated");
        }

        if (promotion.getEvent().getStatus() != EventStatus.ACTIVE) {
            throw new BusinessException("Promotion can only be activated when its event is active");
        }
    }

    public void validateBeforePause(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.INACTIVE) {
            throw new BusinessException("Promotion is already inactive");
        }

        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be paused");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Expired promotion cannot be paused");
        }
    }

    public void validateBeforeCancel(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Promotion is already cancelled");
        }

        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Expired promotion cannot be cancelled");
        }
    }

    public void validateBeforeExpire(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.EXPIRED) {
            throw new BusinessException("Promotion is already expired");
        }

        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be expired");
        }
    }

    public void validateBeforeDuplicate(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be duplicated");
        }
    }
}
