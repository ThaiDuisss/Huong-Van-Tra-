package org.hvtra.iventoryservice.service.impl;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hvtra.iventoryservice.dto.request.ProductVariantRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.ProductResponse;
import org.hvtra.iventoryservice.entity.Product;
import org.hvtra.iventoryservice.entity.ProductVariant;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.exception.BadRequestException;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.ProductMapper;
import org.hvtra.iventoryservice.repository.ProductRepository;
import org.hvtra.iventoryservice.repository.ProductVariantRepository;
import org.hvtra.iventoryservice.service.ProductVariantService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductVariantRepository repository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;

    public ProductVariantServiceImpl(
            ProductVariantRepository repository, ProductRepository productRepository, ProductMapper mapper) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String sku,
            String variantName,
            String barcode,
            Long productId,
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
    public ProductResponse.VariantResponse findById(Long id) {
        return mapper.toVariantResponse(get(id));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse create(Long productId, ProductVariantRequest request) {
        validateSku(request.sku(), null);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        apply(variant, request);
        return mapper.toVariantResponse(repository.save(variant));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse update(Long id, ProductVariantRequest request) {
        ProductVariant variant = get(id);
        validateSku(request.sku(), id);
        apply(variant, request);
        return mapper.toVariantResponse(repository.save(variant));
    }

    @Override
    @Transactional
    public ProductResponse.VariantResponse updateStatus(Long id, Status status) {
        ProductVariant variant = get(id);
        variant.setStatus(status);
        return mapper.toVariantResponse(repository.save(variant));
    }

    private ProductVariant get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant", id));
    }

    private void apply(ProductVariant variant, ProductVariantRequest request) {
        variant.setSku(request.sku().trim());
        variant.setVariantName(request.variantName().trim());
        variant.setSellingPrice(request.sellingPrice());
        variant.setCostPrice(request.costPrice());
        variant.setInventoryPolicy(request.inventoryPolicy());
        variant.setStatus(request.status());
    }

    private void validateSku(String sku, Long id) {
        String normalized = sku.trim();
        boolean exists = id == null
                ? repository.existsBySku(normalized)
                : repository.existsBySkuAndIdNot(normalized, id);
        if (exists) throw new BadRequestException("SKU already exists: " + normalized);
    }
}
