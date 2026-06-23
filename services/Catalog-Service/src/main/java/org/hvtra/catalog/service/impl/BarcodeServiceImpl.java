package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.dto.request.BarcodeCreateRequest;
import org.hvtra.catalog.dto.request.BarcodeUpdateRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Barcode;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.BarcodeRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.service.BarcodeService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BarcodeServiceImpl implements BarcodeService {
    private final BarcodeRepository repository;
    private final ProductVariantRepository variantRepository;
    private final ProductMapper mapper;

    public BarcodeServiceImpl(
            BarcodeRepository repository, ProductVariantRepository variantRepository, ProductMapper mapper) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<ProductResponse.BarcodeResponse> findAll(String value, Pageable pageable) {
        Specification<Barcode> spec = (root, query, cb) -> value == null || value.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("barcodeValue")), "%" + value.toLowerCase() + "%");
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toBarcodeResponse));
    }

    @Override
    public ProductResponse.VariantResponse scan(String value) {
        Barcode barcode = repository.findByBarcodeValue(value)
                .orElseThrow(() -> new ResourceNotFoundException("Barcode", value));
        return mapper.toVariantResponse(barcode.getProductVariant());
    }

    @Override
    @Transactional
    public ProductResponse.BarcodeResponse create(UUID variantId, BarcodeCreateRequest request) {
        String value = request.barcodeValue().trim();
        if (repository.existsByBarcodeValue(value)) {
            throw new BadRequestException("Barcode already exists: " + value);
        }
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant", variantId));
        if (request.primary()) clearCurrentPrimary(variantId, null);

        Barcode barcode = new Barcode();
        barcode.setProductVariant(variant);
        barcode.setBarcodeValue(value);
        barcode.setBarcodeType(request.barcodeType());
        barcode.setPrimary(request.primary());
        return mapper.toBarcodeResponse(repository.save(barcode));
    }

    @Override
    @Transactional
    public ProductResponse.BarcodeResponse update(UUID id, BarcodeUpdateRequest request) {
        Barcode barcode = get(id);
        if (request.primary()) clearCurrentPrimary(barcode.getProductVariant().getId(), id);
        barcode.setBarcodeType(request.barcodeType());
        barcode.setPrimary(request.primary());
        return mapper.toBarcodeResponse(repository.save(barcode));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Barcode barcode = get(id);
        if (barcode.isPrimary()) {
            throw new BadRequestException("Primary barcode cannot be deleted; set another barcode as primary first");
        }
        repository.delete(barcode);
    }

    private void clearCurrentPrimary(UUID variantId, UUID excludedId) {
        repository.findByProductVariantIdAndPrimaryTrue(variantId)
                .filter(current -> excludedId == null || !current.getId().equals(excludedId))
                .ifPresent(current -> {
                    current.setPrimary(false);
                    repository.save(current);
                });
    }

    private Barcode get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Barcode", id));
    }
}

