package org.hvtra.promotionservice.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hvtra.promotionservice.dto.request.Event.EventCreateRequest;
import org.hvtra.promotionservice.validation.annotation.ValidEventTime;

public class EventTimeValidator implements ConstraintValidator<ValidEventTime, EventCreateRequest> {

    @Override
    public boolean isValid(EventCreateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        if (request.getStartAt() == null || request.getEndAt() == null) {
            return true;
        }

        boolean valid = request.getEndAt().isAfter(request.getStartAt());

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("End time must be after start time")
                    .addPropertyNode("endAt")
                    .addConstraintViolation();
        }

        return valid;
    }
}
