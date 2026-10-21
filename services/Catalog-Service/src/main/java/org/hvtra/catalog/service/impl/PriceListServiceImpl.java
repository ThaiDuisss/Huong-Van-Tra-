package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.PriceListRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceListResponse;
import org.hvtra.catalog.entity.PriceList;
import org.hvtra.catalog.entity.PriceListItem;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.PriceListMapper;
import org.hvtra.catalog.repository.PriceListRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.service.PriceChangeHistoryService;
import org.hvtra.catalog.service.PriceListService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PriceListServiceImpl implements PriceListService {
    private final PriceListRepository repository;
    private final ProductVariantRepository variantRepository;
    private final PriceListMapper mapper;
    private final PriceChangeHistoryService priceChangeHistoryService;

    public PriceListServiceImpl(
            PriceListRepository repository,
            ProductVariantRepository variantRepository,
            PriceListMapper mapper,
            PriceChangeHistoryService priceChangeHistoryService) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.mapper = mapper;
        this.priceChangeHistoryService = priceChangeHistoryService;
    }

    @Override
    public PageResponse<PriceListResponse> findAll(
            UUID branchId, Status status, LocalDateTime effectiveAt, Pageable pageable) {
        Specification<PriceList> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (branchId != null) predicates.add(cb.equal(root.get("branchId"), branchId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (effectiveAt != null) {
                predicates.add(cb.or(
                        cb.isNull(root.get("effectiveFrom")),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveAt)));
                predicates.add(cb.or(
                        cb.isNull(root.get("effectiveTo")),
                        cb.greaterThanOrEqualTo(root.get("effectiveTo"), effectiveAt)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<PriceList> page = repository.findAll(spec, pageable);
        List<UUID> ids = page.getContent().stream().map(PriceList::getId).toList();
        Map<UUID, PriceList> withItems = ids.isEmpty()
                ? Map.of()
                : repository.findAllWithItemsByIdIn(ids).stream()
                        .collect(Collectors.toMap(PriceList::getId, Function.identity()));
        return PageResponse.from(page.map(pl -> mapper.toResponse(withItems.getOrDefault(pl.getId(), pl))));
    }

    @Override
    public PriceListResponse findById(UUID id) {
        return repository.findAllWithItemsByIdIn(List.of(id)).stream()
                .findFirst()
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Price list", id));
    }

    @Override
    @Transactional
    public PriceListResponse create(PriceListRequest request) {
        validate(request);
        PriceList priceList = new PriceList();
        apply(priceList, request);
        PriceList saved = repository.save(priceList);
        priceChangeHistoryService.recordItemChanges(saved, List.of());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PriceListResponse update(UUID id, PriceListRequest request) {
        validate(request);
        PriceList priceList = get(id);
        // Chụp giá + trạng thái cũ trước khi apply() thay toàn bộ items, để ghi lịch sử thay đổi giá.
        var pricesBefore = priceChangeHistoryService.snapshot(priceList);
        Status statusBefore = priceList.getStatus();
        apply(priceList, request);
        PriceList saved = repository.save(priceList);
        priceChangeHistoryService.recordItemChanges(saved, pricesBefore);
        priceChangeHistoryService.recordStatusChange(saved, statusBefore);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PriceListResponse updateStatus(UUID id, Status status) {
        PriceList priceList = get(id);
        Status statusBefore = priceList.getStatus();
        priceList.setStatus(status);
        PriceList saved = repository.save(priceList);
        priceChangeHistoryService.recordStatusChange(saved, statusBefore);
        return mapper.toResponse(saved);
    }

    private void apply(PriceList priceList, PriceListRequest request) {
        priceList.setBranchId(request.branchId());
        priceList.setName(request.name().trim());
        priceList.setEffectiveFrom(request.effectiveFrom());
        priceList.setEffectiveTo(request.effectiveTo());
        priceList.setStatus(request.status());
        // Cập nhật tại chỗ thay vì clear() rồi thêm lại: Hibernate INSERT trước khi DELETE orphan nên
        // thêm lại cùng Mặt hàng sẽ vi phạm unique (price_list_id, product_variant_id).
        Set<UUID> requestedVariantIds = request.items().stream()
                .map(PriceListRequest.ItemRequest::productVariantId)
                .collect(Collectors.toSet());
        priceList.getItems().removeIf(item -> !requestedVariantIds.contains(item.getProductVariant().getId()));
        Map<UUID, PriceListItem> existingItems = priceList.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProductVariant().getId(), Function.identity()));
        for (PriceListRequest.ItemRequest itemRequest : request.items()) {
            PriceListItem existing = existingItems.get(itemRequest.productVariantId());
            if (existing != null) {
                existing.setSellingPrice(itemRequest.sellingPrice());
                continue;
            }
            ProductVariant variant = variantRepository.findById(itemRequest.productVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product variant", itemRequest.productVariantId()));
            PriceListItem item = new PriceListItem();
            item.setProductVariant(variant);
            item.setSellingPrice(itemRequest.sellingPrice());
            priceList.addItem(item);
        }
    }

    private void validate(PriceListRequest request) {
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must be after effectiveFrom");
        }
        var variantIds = new HashSet<UUID>();
        for (PriceListRequest.ItemRequest item : request.items()) {
            if (!variantIds.add(item.productVariantId())) {
                throw new BadRequestException("A product variant can occur only once in a price list");
            }
        }
    }

    private PriceList get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Price list", id));
    }
}

