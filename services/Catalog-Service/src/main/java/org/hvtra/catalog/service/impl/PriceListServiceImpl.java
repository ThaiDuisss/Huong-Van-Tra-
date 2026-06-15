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
import org.hvtra.catalog.service.PriceListService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;

@Service
@Transactional(readOnly = true)
public class PriceListServiceImpl implements PriceListService {
    private final PriceListRepository repository;
    private final ProductVariantRepository variantRepository;
    private final PriceListMapper mapper;

    public PriceListServiceImpl(
            PriceListRepository repository,
            ProductVariantRepository variantRepository,
            PriceListMapper mapper) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<PriceListResponse> findAll(
            Long branchId, Status status, LocalDateTime effectiveAt, Pageable pageable) {
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
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public PriceListResponse findById(Long id) {
        return mapper.toResponse(get(id));
    }

    @Override
    @Transactional
    public PriceListResponse create(PriceListRequest request) {
        validate(request);
        PriceList priceList = new PriceList();
        apply(priceList, request);
        return mapper.toResponse(repository.save(priceList));
    }

    @Override
    @Transactional
    public PriceListResponse update(Long id, PriceListRequest request) {
        validate(request);
        PriceList priceList = get(id);
        apply(priceList, request);
        return mapper.toResponse(repository.save(priceList));
    }

    @Override
    @Transactional
    public PriceListResponse updateStatus(Long id, Status status) {
        PriceList priceList = get(id);
        priceList.setStatus(status);
        return mapper.toResponse(repository.save(priceList));
    }

    private void apply(PriceList priceList, PriceListRequest request) {
        priceList.setBranchId(request.branchId());
        priceList.setName(request.name().trim());
        priceList.setEffectiveFrom(request.effectiveFrom());
        priceList.setEffectiveTo(request.effectiveTo());
        priceList.setStatus(request.status());
        priceList.getItems().clear();
        for (PriceListRequest.ItemRequest itemRequest : request.items()) {
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
        var variantIds = new HashSet<Long>();
        for (PriceListRequest.ItemRequest item : request.items()) {
            if (!variantIds.add(item.productVariantId())) {
                throw new BadRequestException("A product variant can occur only once in a price list");
            }
        }
    }

    private PriceList get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Price list", id));
    }
}
