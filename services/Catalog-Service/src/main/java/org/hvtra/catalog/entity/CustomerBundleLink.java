package org.hvtra.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.util.UUID;

/** Liên kết idempotent giữa dòng CUSTOM_BUNDLE đã hoàn tất và biến thể Product CUSTOMER tương ứng. */
@Entity
@Table(
        name = "customer_bundle_links",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_bundle_links_order_item",
                columnNames = {"order_item_id"}
        ),
        indexes = {
                @Index(name = "idx_customer_bundle_links_recipe", columnList = "recipe_sha256"),
                @Index(name = "idx_customer_bundle_links_variant", columnList = "product_variant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CustomerBundleLink extends BaseEntity {

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    private UUID customerId;

    @Column(name = "recipe_sha256", nullable = false, columnDefinition = "CHAR(64)")
    private String recipeSha256;

    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;
}
