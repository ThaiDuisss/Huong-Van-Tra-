package org.hvtra.promotionservice.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hvtra.promotionservice.validation.annotation.ValidTimeRange;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDateTime;

public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, Object> {

    private String startField;
    private String endField;
    private String message;

    @Override
    public void initialize(ValidTimeRange constraintAnnotation) {
        this.startField = constraintAnnotation.startField();
        this.endField = constraintAnnotation.endField();
        this.message = constraintAnnotation.message();
    }

    @Override
    public boolean isValid(Object request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(request);

        Object startValue = beanWrapper.getPropertyValue(startField);
        Object endValue = beanWrapper.getPropertyValue(endField);

        if (startValue == null || endValue == null) {
            return true;
        }

        LocalDateTime startAt = (LocalDateTime) startValue;
        LocalDateTime endAt = (LocalDateTime) endValue;

        boolean valid = endAt.isAfter(startAt);

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(endField)
                    .addConstraintViolation();
        }

        return valid;
    }
}
