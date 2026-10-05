package org.hvtra.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hvtra.catalog.enums.PriceChangeType;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Nhật ký thay đổi giá (append-only). Không tham chiếu khóa ngoại tới bảng giá / Mặt hàng
 * mà lưu snapshot để lịch sử vẫn đọc được khi dữ liệu gốc bị xóa.
 */
@Entity
@Table(name = "price_change_histories", indexes = {
        @Index(name = "idx_price_change_histories_list_time", columnList = "price_list_id, changed_at"),
        @Index(name = "idx_price_change_histories_list_variant_time",
                columnList = "price_list_id, product_variant_id, changed_at"),
        @Index(name = "idx_price_change_histories_variant_time", columnList = "product_variant_id, changed_at")
})
@Getter
@Setter
public class PriceChangeHistory {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "price_list_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID priceListId;

    @Column(name = "price_list_name", nullable = false, updatable = false)
    private String priceListName;

    /** Null khi changeType = STATUS_CHANGED. */
    @Column(name = "product_variant_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(updatable = false, length = 100)
    private String sku;

    @Column(name = "variant_name", updatable = false)
    private String variantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false, length = 50)
    private PriceChangeType changeType;

    @Column(name = "old_price", updatable = false, precision = 18, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", updatable = false, precision = 18, scale = 2)
    private BigDecimal newPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", updatable = false, length = 50)
    private Status oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", updatable = false, length = 50)
    private Status newStatus;

    @Column(name = "changed_by", updatable = false, columnDefinition = "BINARY(16)")
    private UUID changedBy;

    @Column(name = "changed_by_name", updatable = false)
    private String changedByName;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
