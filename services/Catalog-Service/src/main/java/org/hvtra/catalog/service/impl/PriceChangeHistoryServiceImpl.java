package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceChangeHistoryResponse;
import org.hvtra.catalog.entity.PriceChangeHistory;
import org.hvtra.catalog.entity.PriceList;
import org.hvtra.catalog.entity.PriceListItem;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.PriceChangeType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.repository.PriceChangeHistoryRepository;
import org.hvtra.catalog.repository.PriceListRepository;
import org.hvtra.catalog.service.PriceChangeHistoryService;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PriceChangeHistoryServiceImpl implements PriceChangeHistoryService {
    private static final int MAX_PAGE_SIZE = 200;
    /** Mới nhất trước; các dòng cùng một lần lưu có cùng changedAt nên thêm sku + id để phân trang ổn định. */
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("changedAt"), Sort.Order.asc("sku"), Sort.Order.asc("id"));

    private final PriceChangeHistoryRepository repository;
    private final PriceListRepository priceListRepository;

    public PriceChangeHistoryServiceImpl(
            PriceChangeHistoryRepository repository, PriceListRepository priceListRepository) {
        this.repository = repository;
        this.priceListRepository = priceListRepository;
    }

    @Override
    public Collection<PriceSnapshot> snapshot(PriceList priceList) {
        return priceList.getItems().stream()
                .map(item -> {
                    ProductVariant variant = item.getProductVariant();
                    return new PriceSnapshot(
                            variant.getId(), variant.getSku(), variant.getVariantName(), item.getSellingPrice());
                })
                .toList();
    }

    @Override
    @Transactional
    public void recordItemChanges(PriceList priceList, Collection<PriceSnapshot> before) {
        Map<UUID, PriceSnapshot> remaining = new LinkedHashMap<>();
        for (PriceSnapshot snapshot : before) {
            remaining.put(snapshot.productVariantId(), snapshot);
        }

        Actor actor = Actor.current();
        Instant now = Instant.now();
        List<PriceChangeHistory> rows = new ArrayList<>();

        for (PriceListItem item : priceList.getItems()) {
            ProductVariant variant = item.getProductVariant();
            PriceSnapshot old = remaining.remove(variant.getId());
            if (old == null) {
                rows.add(itemRow(priceList, variant.getId(), variant.getSku(), variant.getVariantName(),
                        PriceChangeType.CREATED, null, item.getSellingPrice(), actor, now));
            } else if (!samePrice(old.sellingPrice(), item.getSellingPrice())) {
                rows.add(itemRow(priceList, variant.getId(), variant.getSku(), variant.getVariantName(),
                        PriceChangeType.UPDATED, old.sellingPrice(), item.getSellingPrice(), actor, now));
            }
        }
        for (PriceSnapshot removed : remaining.values()) {
            rows.add(itemRow(priceList, removed.productVariantId(), removed.sku(), removed.variantName(),
                    PriceChangeType.REMOVED, removed.sellingPrice(), null, actor, now));
        }

        if (!rows.isEmpty()) {
            repository.saveAll(rows);
        }
    }

    @Override
    @Transactional
    public void recordStatusChange(PriceList priceList, Status oldStatus) {
        if (oldStatus == priceList.getStatus()) {
            return;
        }
        PriceChangeHistory row = baseRow(priceList, PriceChangeType.STATUS_CHANGED, Actor.current(), Instant.now());
        row.setOldStatus(oldStatus);
        row.setNewStatus(priceList.getStatus());
        repository.save(row);
    }

    @Override
    public PageResponse<PriceChangeHistoryResponse> findByPriceList(
            UUID priceListId, UUID variantId, PriceChangeType changeType, Instant from, Instant to,
            int page, int size) {
        if (!priceListRepository.existsById(priceListId)) {
            throw new ResourceNotFoundException("Price list", priceListId);
        }
        return find(priceListId, variantId, changeType, from, to, page, size);
    }

    @Override
    public PageResponse<PriceChangeHistoryResponse> findByVariant(
            UUID variantId, PriceChangeType changeType, Instant from, Instant to, int page, int size) {
        return find(null, variantId, changeType, from, to, page, size);
    }

    private PageResponse<PriceChangeHistoryResponse> find(
            UUID priceListId, UUID variantId, PriceChangeType changeType, Instant from, Instant to,
            int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        Specification<PriceChangeHistory> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (priceListId != null) predicates.add(cb.equal(root.get("priceListId"), priceListId));
            if (variantId != null) predicates.add(cb.equal(root.get("productVariantId"), variantId));
            if (changeType != null) predicates.add(cb.equal(root.get("changeType"), changeType));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST_FIRST);
        return PageResponse.from(repository.findAll(spec, pageable).map(PriceChangeHistoryResponse::from));
    }

    private static boolean samePrice(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null) {
            return oldPrice == newPrice;
        }
        // compareTo để 10000 và 10000.00 được coi là không đổi.
        return oldPrice.compareTo(newPrice) == 0;
    }

    private static PriceChangeHistory itemRow(
            PriceList priceList, UUID variantId, String sku, String variantName, PriceChangeType type,
            BigDecimal oldPrice, BigDecimal newPrice, Actor actor, Instant now) {
        PriceChangeHistory row = baseRow(priceList, type, actor, now);
        row.setProductVariantId(variantId);
        row.setSku(sku);
        row.setVariantName(variantName);
        row.setOldPrice(oldPrice);
        row.setNewPrice(newPrice);
        return row;
    }

    private static PriceChangeHistory baseRow(PriceList priceList, PriceChangeType type, Actor actor, Instant now) {
        PriceChangeHistory row = new PriceChangeHistory();
        row.setPriceListId(priceList.getId());
        row.setPriceListName(priceList.getName());
        row.setChangeType(type);
        row.setChangedBy(actor.id());
        row.setChangedByName(actor.name());
        row.setChangedAt(now);
        return row;
    }

    /** Người thực hiện; cả hai null khi không có ngữ cảnh đăng nhập (job nội bộ, test). */
    private record Actor(UUID id, String name) {
        static Actor current() {
            try {
                InternalUserPrincipal user = SecurityUtils.currentUser();
                return new Actor(parseUuid(user.userId()), user.username());
            } catch (RuntimeException ex) {
                return new Actor(null, null);
            }
        }

        private static UUID parseUuid(String value) {
            try {
                return value == null ? null : UUID.fromString(value);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}
