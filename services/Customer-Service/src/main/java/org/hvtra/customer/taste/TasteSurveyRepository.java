package org.hvtra.customer.taste;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

interface TasteSurveyRepository extends JpaRepository<TasteSurvey, UUID> {
    Optional<TasteSurvey> findByOwnerActorIdAndCreateRequestKey(UUID ownerActorId, UUID createRequestKey);

    Optional<TasteSurvey> findFirstByCustomerIdAndStatusOrderBySubmittedAtDescIdDesc(
            UUID customerId, TasteSurveyStatus status);

    Page<TasteSurvey> findByCustomerIdAndStatus(UUID customerId, TasteSurveyStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update TasteSurvey s
               set s.customerId = :customerId, s.bindingVersion = 1, s.linkedAt = :now,
                   s.linkedBy = :actorId, s.updatedBy = :actorId, s.updatedAt = :now,
                   s.rowVersion = s.rowVersion + 1
             where s.id = :id and s.customerId is null and s.bindingVersion = 0
               and s.rowVersion = :version and s.ownerActorId = :actorId
               and s.sessionId = :sessionId and s.originInvoiceId = :invoiceId
               and s.status = :status and s.expiresAt > :now
            """)
    int linkGuest(
            @Param("id") UUID id,
            @Param("customerId") UUID customerId,
            @Param("actorId") UUID actorId,
            @Param("sessionId") UUID sessionId,
            @Param("invoiceId") UUID invoiceId,
            @Param("version") long version,
            @Param("status") TasteSurveyStatus status,
            @Param("now") LocalDateTime now);
}
