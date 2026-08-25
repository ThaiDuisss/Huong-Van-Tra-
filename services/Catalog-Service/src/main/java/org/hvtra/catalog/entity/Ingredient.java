package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.catalog.enums.Status;

@Entity
@Table(name = "ingredients", uniqueConstraints = @UniqueConstraint(columnNames = "ingredient_code"))
@Getter
@Setter
public class Ingredient extends BaseEntity {

    @Column(name = "ingredient_code", nullable = false, unique = true, length = 100)
    private String ingredientCode;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;
}
