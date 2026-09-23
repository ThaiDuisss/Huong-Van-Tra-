package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturn extends BaseEntity {

    @Column(name = "return_code", nullable = false, unique = true, length = 50)
    private String returnCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReturnStatus status;

    @Column(name = "refund_required", nullable = false)
    @Builder.Default
    private Boolean refundRequired = true;

    @Column(name = "note", length = 500)
    private String note;

    @Builder.Default
    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderReturnItem> items = new ArrayList<>();

    public void addItem(OrderReturnItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        item.setOrderReturn(this);
    }
}
