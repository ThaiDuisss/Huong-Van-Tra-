package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.iventoryservice.enums.Status;

@Entity
@Table(name = "ingredients", uniqueConstraints = @UniqueConstraint(columnNames = "ingredient_code"))
@Getter
@Setter
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_code", nullable = false, unique = true, length = 100)
    private String ingredientCode;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_unit_id", nullable = false)
    private Unit baseUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;
}
