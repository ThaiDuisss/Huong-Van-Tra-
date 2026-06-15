package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {
}
