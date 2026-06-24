package org.hvtra.promotionservice.validation.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.hvtra.promotionservice.validation.validator.TimeRangeValidator;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeRangeValidator.class)
@Documented
public @interface ValidTimeRange {

    String message() default "End time must be after start time";

    String startField() default "startAt";

    String endField() default "endAt";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
