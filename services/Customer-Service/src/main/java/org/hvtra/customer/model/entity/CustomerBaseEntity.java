package org.hvtra.customer.model.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hvtra.commonservice.domain.entity.BaseEntity;

@MappedSuperclass
@AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "id", columnDefinition = "BINARY(16)")),
        @AttributeOverride(name = "createdBy", column = @Column(name = "created_by_id")),
        @AttributeOverride(name = "updatedBy", column = @Column(name = "updated_by_id")),
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at")),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at"))
})
public abstract class CustomerBaseEntity extends BaseEntity {
}
