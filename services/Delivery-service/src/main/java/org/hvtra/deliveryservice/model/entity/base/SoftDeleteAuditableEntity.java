package org.hvtra.deliveryservice.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SoftDeleteAuditableEntity extends AuditableEntity {

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public void markCreatedBy(String actor) {
        this.createdBy = actor;
    }

    public void markUpdatedBy(String actor) {
        this.updatedBy = actor;
    }

    public void softDelete(String actor) {
        this.deleted = true;
        this.updatedBy = actor;
    }
}
