package org.hvtra.promotionservice.bootstrap;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.hvtra.promotionservice.entity.PromotionTarget;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.repository.PromotionBenefitRepository;
import org.hvtra.promotionservice.repository.PromotionConditionRepository;
import org.hvtra.promotionservice.repository.coupon.CouponRepository;
import org.hvtra.promotionservice.repository.coupon.CouponUsageRepository;
import org.hvtra.promotionservice.repository.event.EventRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionRepository;
import org.hvtra.promotionservice.repository.promotion.PromotionTargetRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
@Profile("clean")
@Order(1)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PromotionServiceDataInitializer implements ApplicationRunner {

    private static final int SEED_RECORD_COUNT = 60;
    private static final String SEED_PREFIX = "INIT";

    private static final List<String> EVENT_THEMES = List.of(
            "Tet Sum Vay",
            "Flash Sale Tra Sua",
            "Xa Kho O Long",
            "Tri An Hoi Vien",
            "Don Lon Tang Qua",
            "Sinh Nhat Khach Hang",
            "Trung Thu Doan Vien",
            "Back To School",
            "Black Friday",
            "Christmas Gift Set",
            "Hello Summer",
            "Tea Break Van Phong"
    );

    private static final List<String> PROMOTION_PURPOSES = List.of(
            "Tang gia tri don hang",
            "Keo khach quay lai",
            "Day nhanh hang ton",
            "Tang ty le mua combo",
            "Thu hut khach moi",
            "Cham soc khach VIP",
            "Giam phi giao hang",
            "Tang tan suat mua hang",
            "Kich hoat khung gio thap diem",
            "Kiem thu luong coupon"
    );

    EventRepository eventRepository;
    PromotionRepository promotionRepository;
    PromotionBenefitRepository promotionBenefitRepository;
    PromotionTargetRepository promotionTargetRepository;
    PromotionConditionRepository promotionConditionRepository;
    CouponRepository couponRepository;
    CouponUsageRepository couponUsageRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now()
                .withSecond(0)
                .withNano(0);

        List<Event> events = seedEvents(now);
        List<Promotion> promotions = seedPromotions(events, now);
        seedPromotionBenefits(promotions);
        seedPromotionTargets(promotions);
        seedPromotionConditions(promotions);
        List<Coupon> coupons = seedCoupons(promotions, now);
        seedCouponUsages(coupons, now);

        log.info(
                "Promotion-Service seed completed. Target records per table: {}.",
                SEED_RECORD_COUNT
        );
    }

    private List<Event> seedEvents(LocalDateTime now) {
        List<String> existingNames = eventRepository.findAll()
                .stream()
                .map(Event::getName)
                .toList();

        List<Event> missingEvents = IntStream.range(0, SEED_RECORD_COUNT)
                .mapToObj(index -> event(index, now))
                .filter(event -> !existingNames.contains(event.getName()))
                .toList();

        if (!missingEvents.isEmpty()) {
            eventRepository.saveAll(missingEvents);
            eventRepository.flush();
            log.info("Seeded {} events.", missingEvents.size());
        }

        return seedEvents();
    }

    private List<Promotion> seedPromotions(List<Event> events, LocalDateTime now) {
        List<String> existingNames = promotionRepository.findAll()
                .stream()
                .map(Promotion::getName)
                .toList();

        List<Promotion> missingPromotions = IntStream.range(0, SEED_RECORD_COUNT)
                .mapToObj(index -> promotion(index, events.get(index % events.size()), now))
                .filter(promotion -> !existingNames.contains(promotion.getName()))
                .toList();

        if (!missingPromotions.isEmpty()) {
            promotionRepository.saveAll(missingPromotions);
            promotionRepository.flush();
            log.info("Seeded {} promotions.", missingPromotions.size());
        }

        return seedPromotions();
    }

    private void seedPromotionBenefits(List<Promotion> promotions) {
        long existingCount = promotionBenefitRepository.count();

        if (existingCount >= SEED_RECORD_COUNT) {
            return;
        }

        List<PromotionBenefit> benefits = IntStream
                .range((int) existingCount, SEED_RECORD_COUNT)
                .mapToObj(index -> benefit(index, promotions.get(index % promotions.size())))
                .toList();

        promotionBenefitRepository.saveAll(benefits);
        log.info("Seeded {} promotion benefits.", benefits.size());
    }

    private void seedPromotionTargets(List<Promotion> promotions) {
        long existingCount = promotionTargetRepository.count();

        if (existingCount >= SEED_RECORD_COUNT) {
            return;
        }

        List<PromotionTarget> targets = IntStream
                .range((int) existingCount, SEED_RECORD_COUNT)
                .mapToObj(index -> target(index, promotions.get(index % promotions.size())))
                .toList();

        promotionTargetRepository.saveAll(targets);
        log.info("Seeded {} promotion targets.", targets.size());
    }

    private void seedPromotionConditions(List<Promotion> promotions) {
        long existingCount = promotionConditionRepository.count();

        if (existingCount >= SEED_RECORD_COUNT) {
            return;
        }

        List<PromotionCondition> conditions = IntStream
                .range((int) existingCount, SEED_RECORD_COUNT)
                .mapToObj(index -> condition(index, promotions.get(index % promotions.size())))
                .toList();

        promotionConditionRepository.saveAll(conditions);
        log.info("Seeded {} promotion conditions.", conditions.size());
    }

    private List<Coupon> seedCoupons(List<Promotion> promotions, LocalDateTime now) {
        List<String> existingCodes = couponRepository.findAll()
                .stream()
                .map(Coupon::getCode)
                .toList();

        List<Coupon> missingCoupons = IntStream.range(0, SEED_RECORD_COUNT)
                .mapToObj(index -> coupon(index, promotions.get(index % promotions.size()), now))
                .filter(coupon -> !existingCodes.contains(coupon.getCode()))
                .toList();

        if (!missingCoupons.isEmpty()) {
            couponRepository.saveAll(missingCoupons);
            couponRepository.flush();
            log.info("Seeded {} coupons.", missingCoupons.size());
        }

        return seedCoupons();
    }

    private void seedCouponUsages(List<Coupon> coupons, LocalDateTime now) {
        List<String> existingTokens = couponUsageRepository.findAll()
                .stream()
                .map(CouponUsage::getReservationToken)
                .toList();

        List<CouponUsage> missingUsages = IntStream.range(0, SEED_RECORD_COUNT)
                .mapToObj(index -> usage(index, coupons.get(index % coupons.size()), now))
                .filter(usage -> !existingTokens.contains(usage.getReservationToken()))
                .toList();

        if (!missingUsages.isEmpty()) {
            couponUsageRepository.saveAll(missingUsages);
            log.info("Seeded {} coupon usages.", missingUsages.size());
        }
    }

    private Event event(int index, LocalDateTime now) {
        EventStatus status = cycle(EventStatus.values(), index);
        EventType eventType = cycle(EventType.values(), index);
        EventAudience audience = cycle(EventAudience.values(), index);
        String theme = EVENT_THEMES.get(index % EVENT_THEMES.size());
        TimeRange range = eventTimeRange(index, status, now);

        return Event.builder()
                .name(seedName("EVENT", index, theme))
                .description("Campaign " + theme + " cho nhom " + audience.name()
                        + ", dung de kiem thu lifecycle " + status.name() + ".")
                .startAt(range.startAt())
                .endAt(range.endAt())
                .eventType(eventType)
                .audience(audience)
                .status(status)
                .build();
    }

    private Promotion promotion(int index, Event event, LocalDateTime now) {
        PromotionType type = cycle(PromotionType.values(), index);
        PromotionApplyMethod applyMethod = cycle(PromotionApplyMethod.values(), index);
        PromotionStatus status = promotionStatusFor(event.getStatus(), index);
        TimeRange range = promotionTimeRange(index, status, event, now);
        String purpose = PROMOTION_PURPOSES.get(index % PROMOTION_PURPOSES.size());

        return Promotion.builder()
                .event(event)
                .name(seedName("PROMO", index, type.name() + " " + applyMethod.name()))
                .description("Khuyen mai " + type.name() + " theo hinh thuc "
                        + applyMethod.name() + " cho " + event.getName() + ".")
                .objective(purpose)
                .type(type)
                .applyMethod(applyMethod)
                .startAt(range.startAt())
                .endAt(range.endAt())
                .stackable(index % 4 == 0)
                .priority(100 - index)
                .status(status)
                .build();
    }

    private PromotionBenefit benefit(int index, Promotion promotion) {
        BenefitType benefitType = benefitTypeFor(promotion.getType());
        Integer buyQuantity = benefitType == BenefitType.BUY_X_GET_Y ? 2 + index % 3 : null;
        Integer getQuantity = benefitType == BenefitType.BUY_X_GET_Y
                || benefitType == BenefitType.FREE_PRODUCT ? 1 + index % 2 : null;
        UUID giftProductId = benefitType == BenefitType.BUY_X_GET_Y
                || benefitType == BenefitType.FREE_PRODUCT ? productUuid(index) : null;

        return PromotionBenefit.builder()
                .promotion(promotion)
                .benefitType(benefitType)
                .value(benefitValue(benefitType, index))
                .maxDiscount(maxDiscount(benefitType, index))
                .giftProductId(giftProductId)
                .buyQuantity(buyQuantity)
                .getQuantity(getQuantity)
                .reason(benefitReason(benefitType, index))
                .minMarginPercent(bd(18 + index % 8))
                .expectedMarginPercent(bd(28 + index % 12))
                .recommendedValue(index % 3 == 0 ? benefitValue(benefitType, index + 1) : null)
                .recommendReason(index % 3 == 0
                        ? "AI de xuat dieu chinh de giu bien loi nhuan."
                        : null)
                .confidenceScore(index % 3 == 0 ? bd(70 + index % 25) : null)
                .aiRecommended(index % 3 == 0)
                .build();
    }

    private PromotionTarget target(int index, Promotion promotion) {
        PromotionTargetType targetType = cycle(PromotionTargetType.values(), index);
        boolean excluded = index % 9 == 0;
        BenefitType overrideBenefitType = index % 5 == 0
                ? benefitTypeFor(promotion.getType())
                : null;

        return PromotionTarget.builder()
                .promotion(promotion)
                .targetType(targetType)
                .targetId(targetUuid(targetType, index))
                .importBatchId(index % 6 == 0 ? importBatchUuid(index / 6) : null)
                .excluded(excluded)
                .overrideBenefitType(overrideBenefitType)
                .overrideValue(overrideBenefitType == null ? null : benefitValue(overrideBenefitType, index + 2))
                .overrideMaxDiscount(overrideBenefitType == null ? null : maxDiscount(overrideBenefitType, index + 2))
                .reason(excluded
                        ? "Loai tru item co bien loi nhuan thap hoac sap het hang."
                        : "Ap dung cho nhom hang phu hop voi chien dich.")
                .build();
    }

    private PromotionCondition condition(int index, Promotion promotion) {
        ConditionType conditionType = cycle(ConditionType.values(), index);
        ConditionOperator operator = operatorFor(conditionType, index);

        return PromotionCondition.builder()
                .promotion(promotion)
                .conditionType(conditionType)
                .operator(operator)
                .conditionValue(conditionValue(conditionType, operator, index))
                .build();
    }

    private Coupon coupon(int index, Promotion promotion, LocalDateTime now) {
        CouponStatus status = cycle(CouponStatus.values(), index);
        CouponScope scope = index % 4 == 0 ? CouponScope.PERSONAL : CouponScope.PUBLIC;
        CouponIssueReason issueReason = cycle(CouponIssueReason.values(), index);
        BenefitType snapshotBenefitType = benefitTypeFor(promotion.getType());
        int usedCount = usedCountFor(status, index);

        return Coupon.builder()
                .promotion(promotion)
                .scope(scope)
                .customerId(scope == CouponScope.PERSONAL ? customerUuid(index) : null)
                .code(seedName("COUPON", index, promotion.getType().name()).replace(" ", "-"))
                .issueReason(issueReason)
                .sourceOrderId(index % 5 == 0 ? orderUuid("source-" + index) : null)
                .issuedAt(index % 2 == 0 ? now.minusDays(Math.max(1, index % 20)) : null)
                .claimedAt(scope == CouponScope.PERSONAL && status == CouponStatus.ACTIVE
                        ? now.minusHours(2 + index % 12)
                        : null)
                .usageLimit(1 + index % 5)
                .perCustomerLimit(1)
                .usedCount(usedCount)
                .expiresAt(couponExpiresAt(status, promotion, now, index))
                .status(status)
                .snapshotPromotionName(promotion.getName())
                .snapshotBenefitType(snapshotBenefitType)
                .snapshotDiscountValue(benefitValue(snapshotBenefitType, index))
                .snapshotMaxDiscount(maxDiscount(snapshotBenefitType, index))
                .build();
    }

    private CouponUsage usage(int index, Coupon coupon, LocalDateTime now) {
        CouponUsageStatus status = cycle(CouponUsageStatus.values(), index);
        LocalDateTime reservedAt = now.minusHours(1 + index % 96);
        LocalDateTime usedAt = status == CouponUsageStatus.APPLIED
                ? reservedAt.plusMinutes(12 + index % 40)
                : null;
        LocalDateTime releasedAt = status == CouponUsageStatus.RELEASED
                || status == CouponUsageStatus.CANCELLED
                ? reservedAt.plusMinutes(20 + index % 50)
                : null;

        return CouponUsage.builder()
                .coupon(coupon)
                .orderId(orderUuid("usage-" + index))
                .customerId(coupon.getCustomerId() != null ? coupon.getCustomerId() : customerUuid(index))
                .discountAmount(discountAmount(coupon, index))
                .status(status)
                .reservationToken(seedName("RESERVATION", index, coupon.getCode()).replace(" ", "-"))
                .reservedAt(reservedAt)
                .reservationExpiresAt(reservedAt.plusMinutes(30))
                .usedAt(usedAt)
                .releasedAt(releasedAt)
                .branchId(branchUuid(index % 8))
                .cashierId(cashierUuid(index % 12))
                .channel(cycle(PromotionChannel.values(), index))
                .build();
    }

    private List<Event> seedEvents() {
        return eventRepository.findAll()
                .stream()
                .filter(event -> event.getName() != null && event.getName().startsWith(SEED_PREFIX + "-EVENT-"))
                .sorted(Comparator.comparing(Event::getName))
                .limit(SEED_RECORD_COUNT)
                .toList();
    }

    private List<Promotion> seedPromotions() {
        return promotionRepository.findAll()
                .stream()
                .filter(promotion -> promotion.getName() != null
                        && promotion.getName().startsWith(SEED_PREFIX + "-PROMO-"))
                .sorted(Comparator.comparing(Promotion::getName))
                .limit(SEED_RECORD_COUNT)
                .toList();
    }

    private List<Coupon> seedCoupons() {
        return couponRepository.findAll()
                .stream()
                .filter(coupon -> coupon.getCode() != null
                        && coupon.getCode().startsWith(SEED_PREFIX + "-COUPON-"))
                .sorted(Comparator.comparing(Coupon::getCode))
                .limit(SEED_RECORD_COUNT)
                .toList();
    }

    private TimeRange eventTimeRange(int index, EventStatus status, LocalDateTime now) {
        return switch (status) {
            case DRAFT -> new TimeRange(now.plusDays(20 + index % 30), now.plusDays(35 + index % 30));
            case SCHEDULED -> new TimeRange(now.plusDays(2 + index % 12), now.plusDays(10 + index % 20));
            case ACTIVE -> new TimeRange(now.minusDays(1 + index % 10), now.plusDays(5 + index % 20));
            case PAUSED -> new TimeRange(now.minusDays(2 + index % 12), now.plusDays(4 + index % 15));
            case EXPIRED -> new TimeRange(now.minusDays(45 + index % 20), now.minusDays(25 + index % 10));
            case ENDED -> new TimeRange(now.minusDays(70 + index % 20), now.minusDays(50 + index % 10));
            case CANCELLED -> new TimeRange(now.minusDays(5 + index % 8), now.plusDays(5 + index % 8));
        };
    }

    private TimeRange promotionTimeRange(
            int index,
            PromotionStatus status,
            Event event,
            LocalDateTime now
    ) {
        LocalDateTime eventStart = event.getStartAt();
        LocalDateTime eventEnd = event.getEndAt();

        if (status == PromotionStatus.SCHEDULED || status == PromotionStatus.DRAFT) {
            LocalDateTime startAt = eventStart.plusDays(Math.min(2, Math.max(0, index % 4)));
            return new TimeRange(startAt, min(startAt.plusDays(5 + index % 5), eventEnd));
        }

        if (status == PromotionStatus.EXPIRED) {
            LocalDateTime startAt = eventStart;
            return new TimeRange(startAt, min(startAt.plusDays(3 + index % 5), eventEnd));
        }

        LocalDateTime startAt = max(eventStart, now.minusDays(1 + index % 5));
        LocalDateTime endAt = min(eventEnd, now.plusDays(2 + index % 10));

        if (!startAt.isBefore(endAt)) {
            startAt = eventStart;
            endAt = eventEnd;
        }

        return new TimeRange(startAt, endAt);
    }

    private PromotionStatus promotionStatusFor(EventStatus eventStatus, int index) {
        if (eventStatus == EventStatus.DRAFT) {
            return index % 3 == 0 ? PromotionStatus.CANCELLED : PromotionStatus.DRAFT;
        }

        if (eventStatus == EventStatus.SCHEDULED) {
            return index % 4 == 0 ? PromotionStatus.DRAFT : PromotionStatus.SCHEDULED;
        }

        if (eventStatus == EventStatus.PAUSED) {
            return PromotionStatus.PAUSED;
        }

        if (eventStatus == EventStatus.EXPIRED) {
            return PromotionStatus.EXPIRED;
        }

        if (eventStatus == EventStatus.ENDED) {
            return PromotionStatus.ENDED;
        }

        if (eventStatus == EventStatus.CANCELLED) {
            return PromotionStatus.CANCELLED;
        }

        return cycle(PromotionStatus.values(), index);
    }

    private BenefitType benefitTypeFor(PromotionType promotionType) {
        return switch (promotionType) {
            case PERCENT_DISCOUNT -> BenefitType.PERCENT_DISCOUNT;
            case FIXED_DISCOUNT -> BenefitType.FIXED_DISCOUNT;
            case FREE_SHIPPING -> BenefitType.FREE_SHIPPING;
            case BUY_X_GET_Y -> BenefitType.BUY_X_GET_Y;
            case FREE_PRODUCT -> BenefitType.FREE_PRODUCT;
        };
    }

    private BigDecimal benefitValue(BenefitType benefitType, int index) {
        return switch (benefitType) {
            case PERCENT_DISCOUNT -> bd(5 + index % 26);
            case FIXED_DISCOUNT -> bd((index % 8 + 1) * 10000);
            case FREE_SHIPPING, FREE_PRODUCT, BUY_X_GET_Y -> BigDecimal.ZERO;
        };
    }

    private BigDecimal maxDiscount(BenefitType benefitType, int index) {
        return switch (benefitType) {
            case PERCENT_DISCOUNT -> bd((index % 10 + 2) * 10000);
            case FIXED_DISCOUNT, FREE_PRODUCT, BUY_X_GET_Y -> null;
            case FREE_SHIPPING -> bd(20000 + (index % 5) * 5000);
        };
    }

    private String benefitReason(BenefitType benefitType, int index) {
        return switch (benefitType) {
            case PERCENT_DISCOUNT -> "Giam phan tram theo gia tri don va gioi han max discount.";
            case FIXED_DISCOUNT -> "Giam tien co dinh cho don dat moc doanh thu.";
            case FREE_SHIPPING -> "Ho tro phi giao hang de tang ty le hoan tat don.";
            case FREE_PRODUCT -> "Tang san pham dung thu cho khach hang muc tieu.";
            case BUY_X_GET_Y -> "Tang item khi khach mua combo nhieu san pham.";
        } + " Batch " + (index + 1) + ".";
    }

    private ConditionOperator operatorFor(ConditionType conditionType, int index) {
        return switch (conditionType) {
            case CUSTOMER_TIER, CUSTOMER_ID, CHANNEL, BRANCH -> index % 2 == 0
                    ? ConditionOperator.IN
                    : ConditionOperator.NOT_IN;
            case FIRST_PURCHASE, PAYMENT_METHOD -> ConditionOperator.EQ;
            case MAX_ORDER_AMOUNT -> ConditionOperator.LTE;
            default -> index % 3 == 0 ? ConditionOperator.GT : ConditionOperator.GTE;
        };
    }

    private String conditionValue(ConditionType conditionType, ConditionOperator operator, int index) {
        return switch (conditionType) {
            case MIN_ORDER_AMOUNT -> String.valueOf(100000 + (index % 10) * 50000);
            case MAX_ORDER_AMOUNT -> String.valueOf(1500000 + (index % 8) * 250000);
            case MIN_ITEM_QUANTITY -> String.valueOf(2 + index % 6);
            case MIN_ORDER_COUNT -> String.valueOf(1 + index % 8);
            case MIN_TOTAL_SPENT -> String.valueOf(500000 + (index % 12) * 100000);
            case MIN_LOYALTY_POINT -> String.valueOf(50 + (index % 10) * 25);
            case FIRST_PURCHASE -> index % 2 == 0 ? "true" : "false";
            case CUSTOMER_TIER -> operator == ConditionOperator.NOT_IN
                    ? "[\"BRONZE\"]"
                    : "[\"SILVER\",\"GOLD\",\"VIP\"]";
            case CUSTOMER_ID -> "[\"" + customerUuid(index) + "\",\"" + customerUuid(index + 100) + "\"]";
            case CHANNEL -> "[\"POS\",\"APP\",\"WEB\"]";
            case BRANCH -> "[\"" + branchUuid(index % 8) + "\",\"" + branchUuid((index + 1) % 8) + "\"]";
            case PAYMENT_METHOD -> index % 2 == 0 ? "\"CASH\"" : "\"BANK_TRANSFER\"";
        };
    }

    private LocalDateTime couponExpiresAt(
            CouponStatus status,
            Promotion promotion,
            LocalDateTime now,
            int index
    ) {
        if (status == CouponStatus.EXPIRED || status == CouponStatus.ARCHIVED) {
            return now.minusDays(1 + index % 30);
        }

        return promotion.getEndAt() != null ? promotion.getEndAt() : now.plusDays(14);
    }

    private int usedCountFor(CouponStatus status, int index) {
        if (status == CouponStatus.ACTIVE || status == CouponStatus.ARCHIVED) {
            return index % 3;
        }

        if (status == CouponStatus.EXPIRED) {
            return index % 2;
        }

        return 0;
    }

    private BigDecimal discountAmount(Coupon coupon, int index) {
        BenefitType benefitType = coupon.getSnapshotBenefitType();

        if (benefitType == BenefitType.PERCENT_DISCOUNT) {
            return bd(10000 + (index % 7) * 5000);
        }

        if (benefitType == BenefitType.FIXED_DISCOUNT) {
            return coupon.getSnapshotDiscountValue();
        }

        if (benefitType == BenefitType.FREE_SHIPPING) {
            return coupon.getSnapshotMaxDiscount() != null
                    ? coupon.getSnapshotMaxDiscount()
                    : bd(25000);
        }

        return BigDecimal.ZERO;
    }

    private UUID targetUuid(PromotionTargetType targetType, int index) {
        return switch (targetType) {
            case PRODUCT -> productUuid(index);
            case CATEGORY -> categoryUuid(index % 12);
            case BRAND -> brandUuid(index % 6);
        };
    }

    private String seedName(String entity, int index, String label) {
        return SEED_PREFIX + "-" + entity + "-" + String.format("%03d", index + 1)
                + "-" + label;
    }

    private <T> T cycle(T[] values, int index) {
        return values[index % values.length];
    }

    private LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private BigDecimal bd(int value) {
        return BigDecimal.valueOf(value);
    }

    private UUID productUuid(int index) {
        return namedUuid("promotion-seed:product:" + index);
    }

    private UUID categoryUuid(int index) {
        return namedUuid("promotion-seed:category:" + index);
    }

    private UUID brandUuid(int index) {
        return namedUuid("promotion-seed:brand:" + index);
    }

    private UUID importBatchUuid(int index) {
        return namedUuid("promotion-seed:import-batch:" + index);
    }

    private UUID customerUuid(int index) {
        return namedUuid("promotion-seed:customer:" + index);
    }

    private UUID orderUuid(String value) {
        return namedUuid("promotion-seed:order:" + value);
    }

    private UUID branchUuid(int index) {
        return namedUuid("promotion-seed:branch:" + index);
    }

    private UUID cashierUuid(int index) {
        return namedUuid("promotion-seed:cashier:" + index);
    }

    private UUID namedUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record TimeRange(LocalDateTime startAt, LocalDateTime endAt) {
    }
}
