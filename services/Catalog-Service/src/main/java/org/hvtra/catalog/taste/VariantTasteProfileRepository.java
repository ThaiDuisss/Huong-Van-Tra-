package org.hvtra.catalog.taste;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Modifying
    @Query("""
            update VariantTasteProfile p
               set p.status = :stale,
                   p.rowVersion = p.rowVersion + 1, p.updatedAt = :now,
                   p.updatedBy = coalesce(:actor, p.updatedBy)
             where p.variantId = :id and p.status = :approved
            """)
    int markStale(@Param("id") UUID variantId, @Param("approved") VariantTasteProfile.State approved,
                  @Param("stale") VariantTasteProfile.State stale, @Param("actor") UUID actor,
                  @Param("now") LocalDateTime now);
}
