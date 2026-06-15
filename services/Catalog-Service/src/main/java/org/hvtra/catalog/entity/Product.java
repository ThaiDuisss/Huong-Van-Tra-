package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.catalog.enums.Status;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Lob
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }
}
