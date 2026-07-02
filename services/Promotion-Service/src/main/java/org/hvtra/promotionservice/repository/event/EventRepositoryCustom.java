package org.hvtra.promotionservice.repository.event;

import org.hvtra.promotionservice.dto.request.event.EventFilterRequest;
import org.hvtra.promotionservice.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepositoryCustom {
    Page<Event> searchEvents(EventFilterRequest request, Pageable pageable);
}
