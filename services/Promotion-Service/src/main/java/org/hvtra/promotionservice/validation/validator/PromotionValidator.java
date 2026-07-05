package org.hvtra.promotionservice.validation.validator;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.promotionservice.dto.request.promotion.PromotionCreateRequest;
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
        if (event == null) {
            throw new BusinessException("Event is required when creating promotion");
        }

        if (event.getStatus() == null) {
            throw new BusinessException("Event status is required");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Cannot create promotion under cancelled event");
        }

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Cannot create promotion under ended event");
        }

        if (event.getStatus() == EventStatus.EXPIRED) {
            throw new BusinessException("Cannot create promotion under expired event");
        }
    }

    public void validatePromotionTimeWithinEvent(PromotionCreateRequest request, Event event) {
        if (request.getStartAt() != null
                && request.getEndAt() != null
                && !request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException("Promotion start time must be before promotion end time");
        }

        if (request.getStartAt() != null
                && event.getStartAt() != null
                && request.getStartAt().isBefore(event.getStartAt())) {
            throw new BusinessException("Promotion start time must not be before event start time");
        }

        if (request.getEndAt() != null
                && event.getEndAt() != null
                && request.getEndAt().isAfter(event.getEndAt())) {
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

    public void validateBeforeDelete(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new BusinessException("Active promotion cannot be deleted. Please pause or cancel it first");
        }
    }

    public void validateBeforeDuplicate(Promotion promotion) {
        if (promotion.getStatus() == PromotionStatus.CANCELLED) {
            throw new BusinessException("Cancelled promotion cannot be duplicated");
        }
    }

    public PromotionStatus resolveResumeTargetStatus(Promotion promotion) {
        if (promotion.getStatus() != PromotionStatus.INACTIVE) {
            throw new BusinessException("Only inactive promotion can be resumed");
        }

        LocalDateTime now = LocalDateTime.now();

        if (promotion.getStartAt() != null && promotion.getStartAt().isAfter(now)) {
            return PromotionStatus.SCHEDULED;
        }

        return PromotionStatus.ACTIVE;
    }

    public void validateStatusTransition(Promotion promotion, PromotionStatus targetStatus) {
        PromotionStatus currentStatus = validateAndGetCurrentStatus(promotion, targetStatus);

        switch (currentStatus) {
            case DRAFT -> validateFromDraft(promotion, targetStatus);
            case SCHEDULED -> validateFromScheduled(promotion, targetStatus);
            case ACTIVE -> validateFromActive(promotion, targetStatus);
            case INACTIVE -> validateFromInactive(promotion, targetStatus);
            case EXPIRED -> validateFromExpired(promotion, targetStatus);
            case CANCELLED -> throw new BusinessException("Cancelled promotion cannot change status");
        }
    }

    private PromotionStatus validateAndGetCurrentStatus(
            Promotion promotion,
            PromotionStatus targetStatus
    ) {
        if (promotion == null) {
            throw new BusinessException("Promotion is required");
        }

        PromotionStatus currentStatus = promotion.getStatus();

        if (currentStatus == null) {
            throw new BusinessException("Current promotion status is required");
        }

        if (targetStatus == null) {
            throw new BusinessException("Target promotion status is required");
        }

        if (currentStatus == targetStatus) {
            throw new BusinessException("Promotion is already in status: " + targetStatus);
        }

        return currentStatus;
    }

    private void validateFromDraft(Promotion promotion, PromotionStatus targetStatus) {
        switch (targetStatus) {
            case SCHEDULED -> validateCanSchedule(promotion);
            case ACTIVE -> validateCanActivate(promotion);
            case CANCELLED -> {
                // valid
            }
            default -> throwInvalidTransition(PromotionStatus.DRAFT, targetStatus);
        }
    }

    private void validateFromScheduled(Promotion promotion, PromotionStatus targetStatus) {
        switch (targetStatus) {
            case ACTIVE -> validateCanActivate(promotion);
            case INACTIVE -> {
                // valid: admin khóa promotion đã lên lịch
            }
            case EXPIRED -> {
                // valid: scheduler/system đánh dấu hết hạn
            }
            case CANCELLED -> {
                // valid
            }
            default -> throwInvalidTransition(PromotionStatus.SCHEDULED, targetStatus);
        }
    }

    private void validateFromActive(Promotion promotion, PromotionStatus targetStatus) {
        switch (targetStatus) {
            case INACTIVE -> {
                // valid: pause/tạm khóa thủ công
            }
            case EXPIRED -> {
                // valid: scheduler/system đánh dấu hết hạn
            }
            case CANCELLED -> {
                // valid: hủy khẩn cấp promotion đang chạy
            }
            default -> throwInvalidTransition(PromotionStatus.ACTIVE, targetStatus);
        }
    }

    private void validateFromInactive(Promotion promotion, PromotionStatus targetStatus) {
        switch (targetStatus) {
            case ACTIVE -> validateCanActivate(promotion);
            case SCHEDULED -> validateCanSchedule(promotion);
            case EXPIRED -> {
                // valid: promotion đang khóa nhưng đã quá hạn
            }
            case CANCELLED -> {
                // valid
            }
            default -> throwInvalidTransition(PromotionStatus.INACTIVE, targetStatus);
        }
    }

    private void validateFromExpired(Promotion promotion, PromotionStatus targetStatus) {
        switch (targetStatus) {
            case ACTIVE -> validateCanActivate(promotion);
            case SCHEDULED -> validateCanSchedule(promotion);
            case CANCELLED -> {
                // valid: admin quyết định hủy promotion đã hết hạn
            }
            default -> throwInvalidTransition(PromotionStatus.EXPIRED, targetStatus);
        }
    }

    private void validateCanSchedule(Promotion promotion) {
        LocalDateTime now = LocalDateTime.now();

        if (promotion.getStartAt() == null) {
            throw new BusinessException("Promotion start time is required to schedule");
        }

        if (!promotion.getStartAt().isAfter(now)) {
            throw new BusinessException("Promotion start time must be in the future to schedule");
        }

        if (isPromotionExpiredByTime(promotion, now)) {
            throw new BusinessException("Expired promotion cannot be scheduled");
        }

        Event event = promotion.getEvent();

        if (event == null) {
            throw new BusinessException("Promotion event is required");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Promotion cannot be scheduled under cancelled event");
        }

        if (event.getStatus() == EventStatus.ENDED) {
            throw new BusinessException("Promotion cannot be scheduled under ended event");
        }

        if (event.getStatus() == EventStatus.EXPIRED) {
            throw new BusinessException("Promotion cannot be scheduled under expired event");
        }

        if (event.getStatus() != EventStatus.SCHEDULED
                && event.getStatus() != EventStatus.ACTIVE) {
            throw new BusinessException("Promotion can only be scheduled when its event is scheduled or active");
        }
    }

    private void validateCanActivate(Promotion promotion) {
        LocalDateTime now = LocalDateTime.now();

        if (promotion.getStartAt() != null && promotion.getStartAt().isAfter(now)) {
            throw new BusinessException("Promotion start time has not arrived yet. Please schedule it instead");
        }

        if (isPromotionExpiredByTime(promotion, now)) {
            throw new BusinessException("Expired promotion cannot be activated");
        }

        Event event = promotion.getEvent();

        if (event == null) {
            throw new BusinessException("Promotion event is required");
        }

        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new BusinessException("Promotion can only be activated when its event is active");
        }
    }

    private boolean isPromotionExpiredByTime(Promotion promotion, LocalDateTime now) {
        return promotion.getEndAt() != null && !promotion.getEndAt().isAfter(now);
    }

    private void throwInvalidTransition(
            PromotionStatus currentStatus,
            PromotionStatus targetStatus
    ) {
        throw new BusinessException(
                "Invalid promotion status transition: "
                        + currentStatus
                        + " -> "
                        + targetStatus
        );
    }
}