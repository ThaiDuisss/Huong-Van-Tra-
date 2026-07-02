package org.hvtra.promotionservice.repository.event;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.dto.request.event.EventFilterRequest;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.QEvent;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventRepositoryImpl implements EventRepositoryCustom {

    JPAQueryFactory queryFactory;

    QEvent event = QEvent.event;

    @Override
    public Page<Event> searchEvents(EventFilterRequest request, Pageable pageable) {
        List<Event> events = queryFactory
                .selectFrom(event)
                .where(
                        keywordContains(request.getKeyword()),
                        statusEquals(request.getStatus()),
                        eventTypeEquals(request.getEventType()),
                        audienceEquals(request.getAudience()),
                        startAtFrom(request.getStartFrom()),
                        startAtTo(request.getStartTo()),
                        endAtFrom(request.getEndFrom()),
                        endAtTo(request.getEndTo())
                )
                .orderBy(
                        event.startAt.desc(),
                        event.eventType.asc(),
                        event.status.asc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(event.count())
                .from(event)
                .where(
                        keywordContains(request.getKeyword()),
                        statusEquals(request.getStatus()),
                        eventTypeEquals(request.getEventType()),
                        audienceEquals(request.getAudience()),
                        startAtFrom(request.getStartFrom()),
                        startAtTo(request.getStartTo()),
                        endAtFrom(request.getEndFrom()),
                        endAtTo(request.getEndTo())
                )
                .fetchOne();

        return new PageImpl<>(
                events,
                pageable,
                total == null ? 0L : total
        );
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        String value = keyword.trim();

        return event.name.containsIgnoreCase(value)
                .or(event.description.containsIgnoreCase(value));
    }

    private BooleanExpression statusEquals(EventStatus status) {
        return status == null ? null : event.status.eq(status);
    }

    private BooleanExpression eventTypeEquals(EventType eventType) {
        return eventType == null ? null : event.eventType.eq(eventType);
    }

    private BooleanExpression audienceEquals(EventAudience audience) {
        return audience == null ? null : event.audience.eq(audience);
    }

    private BooleanExpression startAtFrom(LocalDateTime startFrom) {
        return startFrom == null ? null : event.startAt.goe(startFrom);
    }

    private BooleanExpression startAtTo(LocalDateTime startTo) {
        return startTo == null ? null : event.startAt.loe(startTo);
    }

    private BooleanExpression endAtFrom(LocalDateTime endFrom) {
        return endFrom == null ? null : event.endAt.goe(endFrom);
    }

    private BooleanExpression endAtTo(LocalDateTime endTo) {
        return endTo == null ? null : event.endAt.loe(endTo);
    }
}