package org.hvtra.promotionservice.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hvtra.promotionservice.dto.request.event.EventCreateRequest;
import org.hvtra.promotionservice.dto.request.promotion.PromotionUpdateRequest;
import org.hvtra.promotionservice.validation.validator.TimeRangeValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("@ValidTimeRange / TimeRangeValidator")
class TimeRangeValidatorTest {

    static final String TIME_RANGE_MESSAGE = "End time must be after start time";

    static ValidatorFactory validatorFactory;
    static Validator validator;

    final LocalDateTime start = LocalDateTime.of(2027, 1, 1, 8, 0);

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    <T> List<ConstraintViolation<T>> timeRangeViolations(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        return violations.stream().filter(violation -> TIME_RANGE_MESSAGE.equals(violation.getMessage())).toList();
    }

    EventCreateRequest event(LocalDateTime startAt, LocalDateTime endAt) {
        return EventCreateRequest.builder().startAt(startAt).endAt(endAt).build();
    }

    @Test
    @DisplayName("end after start is valid")
    void endAfterStart_valid() {
        assertThat(timeRangeViolations(event(start, start.plusSeconds(1)))).isEmpty();
    }

    @Test
    @DisplayName("end equal to start is reported on the endAt property")
    void endEqualsStart_invalid() {
        List<ConstraintViolation<EventCreateRequest>> violations = timeRangeViolations(event(start, start));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getPropertyPath()).hasToString("endAt");
    }

    @Test
    @DisplayName("end before start is reported on the endAt property")
    void endBeforeStart_invalid() {
        List<ConstraintViolation<EventCreateRequest>> violations = timeRangeViolations(event(start, start.minusDays(1)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getPropertyPath()).hasToString("endAt");
    }

    @Test
    @DisplayName("a missing start or end is left to the @NotNull constraints")
    void missingBound_skipped() {
        assertThat(timeRangeViolations(event(null, start))).isEmpty();
        assertThat(timeRangeViolations(event(start, null))).isEmpty();
        assertThat(timeRangeViolations(event(null, null))).isEmpty();
    }

    @Test
    @DisplayName("the constraint also guards promotion update requests")
    void promotionUpdateRequest_guarded() {
        PromotionUpdateRequest invalid = new PromotionUpdateRequest();
        invalid.setStartAt(start);
        invalid.setEndAt(start.minusMinutes(1));
        PromotionUpdateRequest valid = new PromotionUpdateRequest();
        valid.setStartAt(start);
        valid.setEndAt(start.plusMinutes(1));

        assertThat(timeRangeViolations(invalid)).hasSize(1);
        assertThat(timeRangeViolations(valid)).isEmpty();
    }

    @Test
    @DisplayName("a null bean is valid")
    void nullBean_valid() {
        assertThat(new TimeRangeValidator().isValid(null, null)).isTrue();
    }
}
