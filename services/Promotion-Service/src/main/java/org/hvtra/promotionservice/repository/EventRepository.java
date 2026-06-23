package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndIsDeletedFalse(UUID id);

    Page<Event> findAllByIsDeletedFalse(Pageable pageable);

}
