package org.hvtra.promotionservice.repository.promotion;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.dto.request.promotion.PromotionFilterRequest;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.QEvent;
import org.hvtra.promotionservice.entity.QPromotion;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionRepositoryImpl implements PromotionRepositoryCustom {

    JPAQueryFactory queryFactory;

    QPromotion promotion = QPromotion.promotion;
    QEvent event = QEvent.event;

    @Override
    public Page<Promotion> searchPromotions(PromotionFilterRequest request, Pageable pageable) {
        PromotionFilterRequest filter = request == null ? new PromotionFilterRequest() : request;

        List<Promotion> promotions = queryFactory
                .selectFrom(promotion)
                .leftJoin(promotion.event, event).fetchJoin()
                .where(filters(filter))
                .orderBy(
                        promotion.priority.desc(),
                        promotion.startAt.desc(),
                        promotion.createdAt.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(promotion.count())
                .from(promotion)
                .leftJoin(promotion.event, event)
                .where(filters(filter))
                .fetchOne();

        return new PageImpl<>(promotions, pageable, total == null ? 0L : total);
    }

    private BooleanExpression[] filters(PromotionFilterRequest request) {
        return new BooleanExpression[]{
                promotion.isDeleted.isFalse(),
                event.isDeleted.isFalse(),
                keywordContains(request.getKeyword()),
                eventIdEquals(request.getEventId()),
                typeEquals(request.getType()),
                applyMethodEquals(request.getApplyMethod()),
                statusEquals(request.getStatus()),
                startAtFrom(request.getStartFrom()),
                startAtTo(request.getStartTo()),
                endAtFrom(request.getEndFrom()),
                endAtTo(request.getEndTo())
        };
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String value = keyword.trim();
        return promotion.name.containsIgnoreCase(value)
                .or(promotion.description.containsIgnoreCase(value))
                .or(promotion.objective.containsIgnoreCase(value));
    }

    private BooleanExpression eventIdEquals(UUID eventId) {
        return eventId == null ? null : event.id.eq(eventId);
    }

    private BooleanExpression typeEquals(PromotionType type) {
        return type == null ? null : promotion.type.eq(type);
    }

    private BooleanExpression applyMethodEquals(PromotionApplyMethod applyMethod) {
        return applyMethod == null ? null : promotion.applyMethod.eq(applyMethod);
    }

    private BooleanExpression statusEquals(PromotionStatus status) {
        return status == null ? null : promotion.status.eq(status);
    }

    private BooleanExpression startAtFrom(LocalDateTime startFrom) {
        return startFrom == null ? null : promotion.startAt.goe(startFrom);
    }

    private BooleanExpression startAtTo(LocalDateTime startTo) {
        return startTo == null ? null : promotion.startAt.loe(startTo);
    }

    private BooleanExpression endAtFrom(LocalDateTime endFrom) {
        return endFrom == null ? null : promotion.endAt.goe(endFrom);
    }

    private BooleanExpression endAtTo(LocalDateTime endTo) {
        return endTo == null ? null : promotion.endAt.loe(endTo);
    }
}
