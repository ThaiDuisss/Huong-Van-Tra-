package org.hvtra.commonservice.domain.entity;

import io.micrometer.common.lang.Nullable;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class AuditableEntity {

    @Nullable
    @CreatedBy
    @JoinColumn(name = "created_by_id")
    String createdBy;

    @Nullable
    @LastModifiedBy
    @JoinColumn(name = "update_by_id")
    String updatedBy;


    @Column(name = "createdAt")
    @CreationTimestamp
    Instant creatDate;

    @Column(name = "updateAt")
    @UpdateTimestamp
    Instant updateDate;


    @Version
    @Column(name = "version")
    private Long version;

}
