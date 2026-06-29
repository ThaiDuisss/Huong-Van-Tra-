package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.UnitMapper;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.UnitService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UnitServiceImpl implements UnitService {
    private final UnitRepository repository;
    private final UnitMapper mapper;

    public UnitServiceImpl(UnitRepository repository, UnitMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<UnitResponse> findAll(String name, Status status, Pageable pageable) {
        Specification<Unit> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public List<UnitResponse> findAll() {
        return repository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public UnitResponse create(UnitRequest request) {
        Unit unit = new Unit();
        mapper.update(unit, request);
        return mapper.toResponse(repository.save(unit));
    }

    @Override
    @Transactional
    public UnitResponse update(UUID id, UnitRequest request) {
        Unit unit = get(id);
        mapper.update(unit, request);
        return mapper.toResponse(repository.save(unit));
    }

    @Override
    @Transactional
    public UnitResponse updateStatus(UUID id, Status status) {
        Unit unit = get(id);
        unit.setStatus(status);
        return mapper.toResponse(repository.save(unit));
    }

    private Unit get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unit", id));
    }

    @Override
    public List<UnitResponse> findAllByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllById(ids).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
