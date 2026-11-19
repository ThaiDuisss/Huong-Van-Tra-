package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "taste_taxonomy_versions")
@Getter
@Setter
class TasteTaxonomy extends TasteRecord {
    enum State { DRAFT, PUBLISHED, RETIRED }

    @Column(name = "version_code", nullable = false, updatable = false, length = 40)
    private String versionCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode manifest;

    @Column(name = "manifest_sha256", columnDefinition = "CHAR(64)", nullable = false)
    private String manifestSha256;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private State state = State.DRAFT;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "published_by", columnDefinition = "BINARY(16)")
    private UUID publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
