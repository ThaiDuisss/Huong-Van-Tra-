package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.PriceChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PriceChangeHistoryRepository
        extends JpaRepository<PriceChangeHistory, UUID>, JpaSpecificationExecutor<PriceChangeHistory> {
}
