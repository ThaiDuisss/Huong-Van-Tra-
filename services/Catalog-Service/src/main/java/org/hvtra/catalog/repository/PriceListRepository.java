package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PriceListRepository extends JpaRepository<PriceList, Long>, JpaSpecificationExecutor<PriceList> {
}
