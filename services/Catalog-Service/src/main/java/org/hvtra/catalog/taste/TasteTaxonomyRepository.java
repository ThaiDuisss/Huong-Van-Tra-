package org.hvtra.catalog.taste;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TasteTaxonomyRepository extends JpaRepository<TasteTaxonomy, UUID> {
    Optional<TasteTaxonomy> findByVersionCode(String versionCode);

    Optional<TasteTaxonomy> findByCurrentTrue();

    List<TasteTaxonomy> findByStateOrderByVersionCode(TasteTaxonomy.State state, Pageable page);
}
