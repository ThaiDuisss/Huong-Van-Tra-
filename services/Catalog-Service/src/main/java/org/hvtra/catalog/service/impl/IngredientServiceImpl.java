package org.hvtra.catalog.service.impl;

import jakarta.persistence.criteria.Predicate;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.IngredientMapper;
import org.hvtra.catalog.repository.IngredientRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.service.IngredientService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class IngredientServiceImpl implements IngredientService {
    private final IngredientRepository repository;
    private final UnitRepository unitRepository;
    private final IngredientMapper mapper;

    public IngredientServiceImpl(
            IngredientRepository repository, UnitRepository unitRepository, IngredientMapper mapper) {
        this.repository = repository;
        this.unitRepository = unitRepository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<IngredientResponse> findAll(String search, Status status, Pageable pageable) {
        Specification<Ingredient> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("ingredientCode")), pattern)));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Override
    public IngredientResponse findById(Long id) {
        return mapper.toResponse(get(id));
    }

    @Override
    @Transactional
    public IngredientResponse create(IngredientRequest request) {
        validateCode(request.ingredientCode(), null);
        Ingredient ingredient = new Ingredient();
        mapper.update(ingredient, request, getUnit(request.baseUnitId()));
        return mapper.toResponse(repository.save(ingredient));
    }

    @Override
    @Transactional
    public IngredientResponse update(Long id, IngredientRequest request) {
        validateCode(request.ingredientCode(), id);
        Ingredient ingredient = get(id);
        mapper.update(ingredient, request, getUnit(request.baseUnitId()));
        return mapper.toResponse(repository.save(ingredient));
    }

    @Override
    @Transactional
    public IngredientResponse updateStatus(Long id, Status status) {
        Ingredient ingredient = get(id);
        ingredient.setStatus(status);
        return mapper.toResponse(repository.save(ingredient));
    }

    private void validateCode(String code, Long id) {
        boolean exists = id == null
                ? repository.existsByIngredientCode(code)
                : repository.existsByIngredientCodeAndIdNot(code, id);
        if (exists) throw new BadRequestException("Ingredient code already exists: " + code);
    }

    private Ingredient get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ingredient", id));
    }

    private Unit getUnit(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unit", id));
    }
}
