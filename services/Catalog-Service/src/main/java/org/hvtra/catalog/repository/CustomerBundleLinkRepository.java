package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.CustomerBundleLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerBundleLinkRepository extends JpaRepository<CustomerBundleLink, UUID> {
    boolean existsByOrderItemId(UUID orderItemId);

    Optional<CustomerBundleLink> findByOrderItemId(UUID orderItemId);
}
