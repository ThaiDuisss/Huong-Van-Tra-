package org.hvtra.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.catalog.enums.Status;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ingredients", uniqueConstraints = @UniqueConstraint(columnNames = "ingredient_code"))
@Getter
@Setter
public class Ingredient extends BaseEntity {

    @Column(name = "ingredient_code", nullable = false, unique = true, length = 100)
    private String ingredientCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    @ManyToMany(mappedBy = "ingredients", fetch = FetchType.LAZY)
    private Set<Supplier> suppliers = new HashSet<>();
}
