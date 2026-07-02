package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.catalog.enums.Status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "price_lists")
@Getter
@Setter
public class PriceList extends BaseEntity {

    @Column(name = "branch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(nullable = false)
    private String name;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    @OneToMany(mappedBy = "priceList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PriceListItem> items = new ArrayList<>();

    public void addItem(PriceListItem item) {
        items.add(item);
        item.setPriceList(this);
    }
}
