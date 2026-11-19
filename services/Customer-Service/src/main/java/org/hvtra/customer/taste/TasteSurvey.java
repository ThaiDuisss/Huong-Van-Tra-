package org.hvtra.customer.taste;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_taste_surveys")
@Getter
@Setter
class TasteSurvey {
    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "session_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID sessionId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "owner_actor_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID ownerActorId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "origin_invoice_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID originInvoiceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "copied_from_survey_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID copiedFromSurveyId;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Column(name = "raw_description", columnDefinition = "TEXT")
    private String rawDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria_json", nullable = false)
    private JsonNode criteria;

    @Column(name = "taxonomy_version", length = 40, nullable = false, updatable = false)
    private String taxonomyVersion;

    @Column(name = "taxonomy_sha256", columnDefinition = "CHAR(64)", nullable = false, updatable = false)
    private String taxonomySha256;

    @Column(name = "criteria_sha256", columnDefinition = "CHAR(64)")
    private String criteriaSha256;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private TasteSurveyStatus status;

    @Column(name = "binding_version", nullable = false)
    private int bindingVersion;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "create_request_key", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID createRequestKey;

    @Column(name = "create_request_hash", columnDefinition = "CHAR(64)", nullable = false, updatable = false)
    private String createRequestHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "submit_request_key", columnDefinition = "BINARY(16)")
    private UUID submitRequestKey;

    @Column(name = "submit_request_hash", columnDefinition = "CHAR(64)")
    private String submitRequestHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "created_by", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "updated_by", columnDefinition = "BINARY(16)", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "linked_by", columnDefinition = "BINARY(16)")
    private UUID linkedBy;

    protected TasteSurvey() {
    }
}
