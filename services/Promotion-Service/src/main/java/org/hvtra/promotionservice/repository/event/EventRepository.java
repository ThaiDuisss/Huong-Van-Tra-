package org.hvtra.promotionservice.repository.event;

import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository
        extends JpaRepository<Event, UUID>,
                EventRepositoryCustom {

    Optional<Event> findByIdAndIsDeletedFalse(UUID id);

    Page<Event> findAllByIsDeletedFalse(Pageable pageable);

    Page<Event> findAllByStatusAndIsDeletedFalse(EventStatus status, Pageable pageable);

}
