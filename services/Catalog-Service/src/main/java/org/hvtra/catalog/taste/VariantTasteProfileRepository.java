package org.hvtra.catalog.taste;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VariantTasteProfileRepository extends JpaRepository<VariantTasteProfile, UUID> {
    Optional<VariantTasteProfile> findFirstByVariantIdOrderByProfileRevisionDesc(UUID variantId);

    Optional<VariantTasteProfile> findByVariantIdAndTaxonomyIdAndPreparationCodeAndStatus(
            UUID variantId, UUID taxonomyId, String preparationCode, VariantTasteProfile.State status);

    @EntityGraph(attributePaths = "taxonomy")
    Page<VariantTasteProfile> findByVariantIdOrderByProfileRevisionDesc(UUID variantId, Pageable page);

    @EntityGraph(attributePaths = "taxonomy")
    List<VariantTasteProfile> findByVariantIdInAndStatusAndTaxonomyStateOrderById(
            Collection<UUID> variantIds, VariantTasteProfile.State status, TasteTaxonomy.State state, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<VariantTasteProfile> findByVariantIdAndStatus(UUID variantId, VariantTasteProfile.State status);
}
