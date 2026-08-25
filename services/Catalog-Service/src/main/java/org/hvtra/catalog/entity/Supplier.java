package org.hvtra.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.catalog.enums.Status;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "suppliers", uniqueConstraints = @UniqueConstraint(columnNames = "supplier_code"))
@Getter
@Setter
public class Supplier extends BaseEntity {

    @Column(name = "supplier_code", nullable = false, unique = true, length = 50)
    private String supplierCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 50)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "supplier_ingredients",
            joinColumns = @JoinColumn(name = "supplier_id"),
            inverseJoinColumns = @JoinColumn(name = "ingredient_id")
    )
    private Set<Ingredient> ingredients = new HashSet<>();
}
