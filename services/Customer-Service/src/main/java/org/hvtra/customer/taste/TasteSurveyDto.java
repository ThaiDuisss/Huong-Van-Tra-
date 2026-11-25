package org.hvtra.customer.taste;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class TasteSurveyDto {
    private TasteSurveyDto() {
    }

    public record Create(
            UUID customerId,
            @NotNull UUID originInvoiceId,
            @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,39}") String taxonomyVersion,
            @NotNull @Pattern(regexp = "[0-9a-f]{64}") String taxonomySha256,
            @Size(max = 4000) String rawDescription,
            JsonNode criteria
    ) {
    }

    public record UpdateDraft(@Size(max = 4000) String rawDescription, @NotNull JsonNode criteria) {
    }

    public record Copy(@NotNull UUID customerId, @NotNull UUID originInvoiceId) {
    }

    public record Link(@NotNull UUID customerId, @NotNull UUID originInvoiceId, @NotNull UUID sessionId) {
    }

    public record RecommendationSurvey(
            UUID customerId,
            TasteSurveyStatus status,
            long rowVersion,
            Instant submittedAt,
            Instant effectiveLinkedAt,
            String taxonomyVersion,
            String taxonomySha256,
            String criteriaSha256,
            JsonNode criteria
    ) {
    }

    public record Response(
            UUID id,
            UUID customerId,
            UUID sessionId,
            UUID originInvoiceId,
            UUID copiedFromSurveyId,
            TasteSurveyStatus status,
            long rowVersion,
            int bindingVersion,
            String taxonomyVersion,
            String taxonomySha256,
            String criteriaSha256,
            String rawDescription,
            JsonNode criteria,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt,
            Instant expiresAt,
            Instant linkedAt
    ) {
    }
}
