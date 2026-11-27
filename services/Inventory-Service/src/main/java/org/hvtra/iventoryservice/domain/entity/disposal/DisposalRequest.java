package org.hvtra.iventoryservice.domain.entity.disposal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspectionLine;
import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "disposal_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_disposal_request_code", columnNames = "request_code")
        },
        indexes = {
                @Index(name = "idx_disposal_requests_source_inspection", columnList = "source_inspection_id"),
                @Index(name = "idx_disposal_requests_status", columnList = "status"),
                @Index(name = "idx_disposal_requests_return", columnList = "return_id"),
                @Index(name = "idx_disposal_requests_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
public class DisposalRequest extends BaseEntity {

    /** Phiếu cũ: dòng QC WRITE_OFF chờ duyệt (đóng tự động khi QC xác nhận). */
    public static final String SOURCE_RETURN_WRITE_OFF = "CUSTOMER_RETURN_INSPECTION";
    /** Tiêu hủy hàng lỗi DAMAGED đã nhập vị trí hàng lỗi từ phiếu QC hoàn hàng. */
    public static final String SOURCE_RETURN_DAMAGED = "CUSTOMER_RETURN_DAMAGED";

    @Column(name = "request_code", nullable = false, unique = true, length = 100)
    private String requestCode;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_inspection_id", nullable = false)
    private CustomerReturnInspection sourceInspection;

    @Column(name = "return_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID returnId;

    @Column(name = "return_code", nullable = false, length = 50)
    private String returnCode;

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DisposalRequestStatus status = DisposalRequestStatus.PENDING_APPROVAL;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "transaction_id", unique = true, columnDefinition = "BINARY(16)")
    private UUID transactionId;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "disposalRequest", cascade = CascadeType.PERSIST)
    private List<CustomerReturnInspectionLine> lines = new ArrayList<>();

    public void addLine(CustomerReturnInspectionLine line) {
        lines.add(line);
        line.setDisposalRequest(this);
    }

    public boolean isDamagedReturnDisposal() {
        return SOURCE_RETURN_DAMAGED.equals(sourceType);
    }
}
