package org.hvtra.iventoryservice.domain.entity.bundle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.CustomBundlePackingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu đóng trà theo yêu cầu cho một dòng bundle của đơn bán. Nguyên liệu được giữ chỗ với khóa giữ chỗ
 * là {@code id} của yêu cầu này (không dùng orderId) để tách khỏi phần giữ nguyên liệu theo BOM của các món
 * khác trong cùng đơn.
 */
@Entity
@Table(
        name = "custom_bundle_packings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_custom_bundle_packings_code", columnNames = "packing_code"),
                @UniqueConstraint(name = "uq_custom_bundle_packings_order_item", columnNames = "order_item_id")
        },
        indexes = {
                @Index(name = "idx_custom_bundle_packings_status", columnList = "status"),
                @Index(name = "idx_custom_bundle_packings_order", columnList = "order_id")
        }
)
@Getter
@Setter
public class CustomBundlePacking extends BaseEntity {

    @Column(name = "packing_code", nullable = false, length = 100)
    private String packingCode;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "order_code", length = 100)
    private String orderCode;

    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    /** Variant hệ thống "Trà đóng theo yêu cầu" bên Catalog (bắt buộc trên dòng giữ nguyên liệu). */
    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "bundle_name", length = 255)
    private String bundleName;

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CustomBundlePackingStatus status = CustomBundlePackingStatus.WAITING_MATERIAL;

    @Column(name = "expected_total_weight", nullable = false, precision = 18, scale = 4)
    private BigDecimal expectedTotalWeight = BigDecimal.ZERO;

    @Column(name = "actual_total_weight", precision = 18, scale = 4)
    private BigDecimal actualTotalWeight;

    @Column(name = "packaging_type", length = 100)
    private String packagingType;

    @Column(name = "number_of_packages")
    private Integer numberOfPackages;

    @Column(name = "packing_note", length = 1000)
    private String packingNote;

    /** Ngày dự kiến đủ nguyên liệu khi đang chờ hàng. */
    @Column(name = "expected_available_at")
    private Instant expectedAvailableAt;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "packed_by", columnDefinition = "BINARY(16)")
    private UUID packedBy;

    @Column(name = "packed_at")
    private Instant packedAt;

    /** Mã giao dịch xuất kho CUSTOM_BUNDLE_ISSUE tạo khi xác nhận đóng gói. */
    @Column(name = "issue_transaction_code", length = 100)
    private String issueTransactionCode;

    @Column(name = "cancelled_by", columnDefinition = "BINARY(16)")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "packing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<CustomBundlePackingLine> lines = new ArrayList<>();

    public void addLine(CustomBundlePackingLine line) {
        lines.add(line);
        line.setPacking(this);
    }
}
