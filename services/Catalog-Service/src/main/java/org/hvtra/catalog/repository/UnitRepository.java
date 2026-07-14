package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UnitRepository extends JpaRepository<Unit, UUID>, JpaSpecificationExecutor<Unit> {
    Page<Unit> findByNameContainingIgnoreCaseAndIsDeletedFalse(String name, Pageable pageable);
    List<Unit> findByBaseUnitIsNullAndIsDeletedFalseAndStatus(Status status);
}
