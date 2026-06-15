package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.CategoryRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMapper mapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<ProductSummaryResponse> findAll(
            String name, Long categoryId, Status status, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(productRepository.findAll(spec, pageable).map(mapper::toSummaryResponse));
    }

    @Override
    public ProductResponse findById(Long id) {
        return mapper.toResponse(getProduct(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        updateProductFields(product, request);
        return mapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        updateProductFields(product, request);
        return mapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(Long id, Status status) {
        Product product = getProduct(id);
        product.setStatus(status);
        return mapper.toResponse(productRepository.save(product));
    }

    private void updateProductFields(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setStatus(request.status());
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
