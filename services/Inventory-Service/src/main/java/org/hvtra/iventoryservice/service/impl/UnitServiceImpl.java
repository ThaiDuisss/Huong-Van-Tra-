package org.hvtra.iventoryservice.service.impl;

import org.hvtra.iventoryservice.dto.request.UnitRequest;
import org.hvtra.iventoryservice.dto.response.UnitResponse;
import org.hvtra.iventoryservice.entity.Unit;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.mapper.UnitMapper;
import org.hvtra.iventoryservice.repository.UnitRepository;
import org.hvtra.iventoryservice.service.UnitService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public UnitResponse update(Long id, UnitRequest request) {
        Unit unit = get(id);
        mapper.update(unit, request);
        return mapper.toResponse(repository.save(unit));
    }

    @Override
    @Transactional
    public UnitResponse updateStatus(Long id, Status status) {
        Unit unit = get(id);
        unit.setStatus(status);
        return mapper.toResponse(repository.save(unit));
    }

    private Unit get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unit", id));
    }
}
