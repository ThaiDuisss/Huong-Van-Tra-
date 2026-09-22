package org.hvtra.commonservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class BaseEntityNoId extends  AuditableEntity{

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
