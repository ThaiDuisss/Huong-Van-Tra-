package org.hvtra.commonservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public class BaseEntityNoId extends  AuditableEntity{

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
