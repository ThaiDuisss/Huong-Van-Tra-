package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "variant_taste_profiles")
@Getter
@Setter
class VariantTasteProfile extends TasteRecord {
    enum State { DRAFT, APPROVED, STALE, RETIRED }

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "variant_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taxonomy_version_id", nullable = false, updatable = false)
    private TasteTaxonomy taxonomy;

    @Column(name = "profile_revision", nullable = false, updatable = false)
    private int profileRevision;

    @Column(name = "composition_revision", nullable = false, updatable = false)
    private long compositionRevision;

    @Column(name = "composition_sha256", columnDefinition = "CHAR(64)", nullable = false, updatable = false)
    private String compositionSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "basis_snapshot", nullable = false, updatable = false)
    private JsonNode basisSnapshot;

    @Column(name = "preparation_code", length = 80, nullable = false)
    private String preparationCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preparation_protocol", nullable = false)
    private JsonNode preparationProtocol;

    @Column(name = "preparation_sha256", columnDefinition = "CHAR(64)", nullable = false)
    private String preparationSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "values_json", nullable = false)
    private JsonNode sensoryValues;

    @Column(name = "source_method", length = 30, nullable = false)
    private String sourceMethod;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assessed_by", columnDefinition = "BINARY(16)")
    private UUID assessedBy;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "profile_sha256", columnDefinition = "CHAR(64)")
    private String profileSha256;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private State status = State.DRAFT;
}
