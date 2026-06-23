package org.hvtra.promotionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.promotionservice.enums.event.EventStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Event extends BaseEntity {

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "start_at", nullable = false)
    LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    LocalDateTime endAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    EventStatus status = EventStatus.INACTIVE;

    @Builder.Default
    @OneToMany(mappedBy = "event")
    List<Promotion> promotions = new ArrayList<>();
}