package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID>, JpaSpecificationExecutor<PriceList> {
}
