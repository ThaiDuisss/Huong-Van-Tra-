package org.hvtra.promotionservice.validation.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.hvtra.promotionservice.validation.validator.EventTimeValidator;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventTimeValidator.class)
@Documented
public @interface ValidEventTime {

    String message() default "End time must be after start time";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
