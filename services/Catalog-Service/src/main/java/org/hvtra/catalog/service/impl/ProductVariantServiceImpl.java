package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.InventoryItemType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductVariantRepository repository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final InventoryClient inventoryClient;
    private final UnitRepository unitRepository;

    public ProductVariantServiceImpl(
            ProductVariantRepository repository,
            ProductRepository productRepository,
            ProductMapper mapper,
            InventoryClient inventoryClient,
            UnitRepository unitRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.inventoryClient = inventoryClient;
        this.unitRepository = unitRepository;
    }

    @Override
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String sku,
            String variantName,
            String barcode,
            UUID productId,
            Status status,
            Pageable pageable) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (sku != null && !sku.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sku")), "%" + sku.toLowerCase() + "%"));
            }
            if (variantName != null && !variantName.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("variantName")), "%" + variantName.toLowerCase() + "%"));
            }
            if (barcode != null && !barcode.isBlank()) {
                query.distinct(true);
                predicates.add(cb.like(
                        cb.lower(root.join("barcodes", JoinType.LEFT).get("barcodeValue")),
                        "%" + barcode.toLowerCase() + "%"));
            }
            if (productId != null) predicates.add(cb.equal(root.get("product").get("id"), productId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toVariantSummary));
    }

    @Override
    public ProductResponse.VariantResponse findById(UUID id) {
        return mapper.toVariantResponse(get(id));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request) {
        validateSku(request.sku(), null);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        apply(variant, request);
        ProductVariant savedVariant = repository.saveAndFlush(variant);
        inventoryClient.createProductVariantItem(
                InventoryClient.CreateInventoryItemRequest.builder()
                        .status(savedVariant.getStatus().toString())
                        .baseUnitId(savedVariant.getBaseUnitId())
                        .referenceId(savedVariant.getId())
                        .itemType(InventoryItemType.PRODUCT_VARIANT.toString())
                        .build());
        return mapper.toVariantResponse(savedVariant);
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request) {
        ProductVariant variant = get(id);
        validateSku(request.sku(), id);
        apply(variant, request);
        return mapper.toVariantResponse(repository.save(variant));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse updateStatus(UUID id, Status status) {
        ProductVariant variant = get(id);
        variant.setStatus(status);
        return mapper.toVariantResponse(repository.save(variant));
    }

    private ProductVariant get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant", id));
    }

    private void apply(ProductVariant variant, ProductVariantRequest request) {
        if (!unitRepository.existsById(request.baseUnitId())) {
            throw new ResourceNotFoundException("Unit", request.baseUnitId());
        }
        variant.setSku(request.sku().trim());
        variant.setVariantName(request.variantName().trim());
        variant.setSellingPrice(request.sellingPrice());
        variant.setCostPrice(request.costPrice());
        variant.setBaseUnitId(request.baseUnitId());
        variant.setInventoryPolicy(request.inventoryPolicy());
        variant.setStatus(request.status());
    }

    private void validateSku(String sku, UUID id) {
        String normalized = sku.trim();
        boolean exists = id == null
                ? repository.existsBySku(normalized)
                : repository.existsBySkuAndIdNot(normalized, id);
        if (exists) throw new BadRequestException("SKU already exists: " + normalized);
    }
}

