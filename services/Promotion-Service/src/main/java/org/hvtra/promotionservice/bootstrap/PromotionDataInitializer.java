package org.hvtra.promotionservice.bootstrap;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.repository.PromotionRepository;
import org.hvtra.promotionservice.repository.event.EventRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("clean")
@Order(2)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PromotionDataInitializer implements ApplicationRunner {

    PromotionRepository promotionRepository;
    EventRepository eventRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (promotionRepository.count() > 0) {
            log.info("Promotion data already exists. Promotion seed skipped.");
            return;
        }

        List<Event> events = eventRepository.findAll()
                .stream()
                .filter(event -> !Boolean.TRUE.equals(event.getIsDeleted()))
                .filter(this::canSeedPromotionForEvent)
                .toList();

        if (events.isEmpty()) {
            log.warn("No valid events found. Promotion seed skipped.");
            return;
        }

        int savedCount = 0;
        LocalDateTime now = LocalDateTime.now()
                .withSecond(0)
                .withNano(0);

        for (Event event : events) {
            List<PromotionSeedData> seedDataList = createSeedDataByEvent(event);

            for (PromotionSeedData seed : seedDataList) {
                LocalDateTime promotionStartAt = resolvePromotionStartAt(event, seed.startOffsetDays());
                LocalDateTime promotionEndAt = resolvePromotionEndAt(event, promotionStartAt, seed.durationDays());

                if (!isPromotionTimeInsideEvent(event, promotionStartAt, promotionEndAt)) {
                    log.warn(
                            "Skip promotion seed because time is outside event. eventId={}, eventName={}, promotionName={}",
                            event.getId(),
                            event.getName(),
                            seed.name()
                    );
                    continue;
                }

                PromotionStatus status = resolvePromotionStatus(
                        event,
                        seed.defaultStatus(),
                        promotionStartAt,
                        promotionEndAt,
                        now
                );

                Promotion promotion = Promotion.builder()
                        .event(event)
                        .name(seed.name())
                        .description(seed.description())
                        .objective(seed.objective())
                        .type(seed.type())
                        .applyMethod(seed.applyMethod())
                        .startAt(promotionStartAt)
                        .endAt(promotionEndAt)
                        .stackable(seed.stackable())
                        .priority(seed.priority())
                        .status(status)
                        .build();

                promotionRepository.save(promotion);
                savedCount++;
            }
        }

        log.info("Seeded {} promotions successfully.", savedCount);
    }

    private boolean canSeedPromotionForEvent(Event event) {
        return event.getStatus() != null
                && event.getStartAt() != null
                && event.getEndAt() != null
                && event.getStartAt().isBefore(event.getEndAt());
    }

    private List<PromotionSeedData> createSeedDataByEvent(Event event) {
        EventType eventType = event.getEventType();

        if (eventType == null) {
            return defaultPromotionSeeds(event);
        }

        return switch (eventType) {
            case SEASONAL_CAMPAIGN -> seasonalCampaignSeeds(event);
            case FLASH_SALE -> flashSaleSeeds(event);
            case CLEARANCE_CAMPAIGN -> clearanceCampaignSeeds(event);
            case LOYALTY_CAMPAIGN -> loyaltyCampaignSeeds(event);
            case ORDER_INCENTIVE -> orderIncentiveSeeds(event);
            case MANUAL_CAMPAIGN -> manualCampaignSeeds(event);
        };
    }

    private List<PromotionSeedData> seasonalCampaignSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Giảm 20% toàn chiến dịch - " + event.getName(),
                        "Giảm 20% cho các sản phẩm nổi bật trong chiến dịch " + event.getName() + ".",
                        "Tăng doanh số và thu hút khách hàng trong giai đoạn chiến dịch.",
                        PromotionType.PERCENT_DISCOUNT,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.ACTIVE,
                        0,
                        3,
                        false,
                        20
                ),
                new PromotionSeedData(
                        "Voucher quà tặng - " + event.getName(),
                        "Tặng sản phẩm hoặc phần quà cho đơn hàng đủ điều kiện.",
                        "Tăng giá trị đơn hàng trung bình và khuyến khích mua combo.",
                        PromotionType.FREE_PRODUCT,
                        PromotionApplyMethod.COUPON_CODE,
                        PromotionStatus.SCHEDULED,
                        3,
                        4,
                        true,
                        15
                ),
                new PromotionSeedData(
                        "Freeship mùa lễ - " + event.getName(),
                        "Miễn phí vận chuyển cho đơn hàng đạt giá trị tối thiểu.",
                        "Giảm rào cản phí giao hàng và tăng tỷ lệ hoàn tất đơn.",
                        PromotionType.FREE_SHIPPING,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.SCHEDULED,
                        7,
                        5,
                        true,
                        10
                )
        );
    }

    private List<PromotionSeedData> flashSaleSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Flash giảm 30% - " + event.getName(),
                        "Giảm nhanh 30% cho nhóm sản phẩm nổi bật, số lượng giới hạn.",
                        "Tạo hiệu ứng mua nhanh trong thời gian ngắn.",
                        PromotionType.PERCENT_DISCOUNT,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.ACTIVE,
                        0,
                        1,
                        false,
                        30
                ),
                new PromotionSeedData(
                        "Deal 20K - " + event.getName(),
                        "Giảm trực tiếp 20.000đ cho đơn hàng đủ điều kiện.",
                        "Kích cầu đơn hàng nhanh trong khung giờ cao điểm.",
                        PromotionType.FIXED_DISCOUNT,
                        PromotionApplyMethod.DIRECT_DISCOUNT,
                        PromotionStatus.ACTIVE,
                        1,
                        1,
                        false,
                        25
                )
        );
    }

    private List<PromotionSeedData> clearanceCampaignSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Xả kho giảm sâu - " + event.getName(),
                        "Giảm giá mạnh cho nhóm sản phẩm cần xoay vòng tồn kho.",
                        "Đẩy nhanh hàng tồn và giải phóng kho.",
                        PromotionType.PERCENT_DISCOUNT,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.ACTIVE,
                        0,
                        4,
                        false,
                        18
                ),
                new PromotionSeedData(
                        "Giảm cố định hàng tồn - " + event.getName(),
                        "Giảm số tiền cố định cho sản phẩm thuộc danh sách xả kho.",
                        "Tối ưu tốc độ bán hàng tồn kho.",
                        PromotionType.FIXED_DISCOUNT,
                        PromotionApplyMethod.DIRECT_DISCOUNT,
                        PromotionStatus.INACTIVE,
                        4,
                        5,
                        true,
                        12
                )
        );
    }

    private List<PromotionSeedData> loyaltyCampaignSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Ưu đãi hội viên thân thiết - " + event.getName(),
                        "Giảm giá riêng cho nhóm khách hàng thân thiết.",
                        "Tăng tỷ lệ quay lại mua hàng của khách cũ.",
                        PromotionType.PERCENT_DISCOUNT,
                        PromotionApplyMethod.COUPON_CODE,
                        PromotionStatus.ACTIVE,
                        0,
                        5,
                        true,
                        16
                ),
                new PromotionSeedData(
                        "Voucher khách VIP - " + event.getName(),
                        "Mã ưu đãi dành riêng cho khách VIP hoặc khách chi tiêu cao.",
                        "Gia tăng sự trung thành và giữ chân khách hàng giá trị cao.",
                        PromotionType.FIXED_DISCOUNT,
                        PromotionApplyMethod.COUPON_CODE,
                        PromotionStatus.SCHEDULED,
                        5,
                        7,
                        false,
                        14
                )
        );
    }

    private List<PromotionSeedData> orderIncentiveSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Freeship đơn đủ điều kiện - " + event.getName(),
                        "Miễn phí vận chuyển cho đơn hàng đạt điều kiện.",
                        "Tăng tỷ lệ hoàn tất đơn hàng và giảm rào cản phí ship.",
                        PromotionType.FREE_SHIPPING,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.ACTIVE,
                        0,
                        4,
                        true,
                        13
                ),
                new PromotionSeedData(
                        "Mua nhiều nhận ưu đãi - " + event.getName(),
                        "Ưu đãi mua X tặng Y cho các đơn hàng nhiều sản phẩm.",
                        "Tăng số lượng sản phẩm trên mỗi đơn hàng.",
                        PromotionType.BUY_X_GET_Y,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.ACTIVE,
                        4,
                        6,
                        false,
                        11
                ),
                new PromotionSeedData(
                        "Giảm trực tiếp đơn lớn - " + event.getName(),
                        "Giảm tiền trực tiếp cho đơn hàng đạt giá trị tối thiểu.",
                        "Tăng giá trị đơn hàng trung bình.",
                        PromotionType.FIXED_DISCOUNT,
                        PromotionApplyMethod.DIRECT_DISCOUNT,
                        PromotionStatus.SCHEDULED,
                        10,
                        5,
                        false,
                        9
                )
        );
    }

    private List<PromotionSeedData> manualCampaignSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Voucher riêng theo danh sách - " + event.getName(),
                        "Mã ưu đãi dành cho danh sách khách hàng được chọn thủ công.",
                        "Chăm sóc nhóm khách hàng đặc biệt theo chiến dịch riêng.",
                        PromotionType.FIXED_DISCOUNT,
                        PromotionApplyMethod.COUPON_CODE,
                        PromotionStatus.DRAFT,
                        0,
                        5,
                        false,
                        7
                ),
                new PromotionSeedData(
                        "Tặng sản phẩm cho khách chọn lọc - " + event.getName(),
                        "Tặng sản phẩm cho nhóm khách hàng trong danh sách campaign.",
                        "Tăng trải nghiệm cá nhân hóa cho khách hàng mục tiêu.",
                        PromotionType.FREE_PRODUCT,
                        PromotionApplyMethod.COUPON_CODE,
                        PromotionStatus.DRAFT,
                        5,
                        5,
                        true,
                        6
                )
        );
    }

    private List<PromotionSeedData> defaultPromotionSeeds(Event event) {
        return List.of(
                new PromotionSeedData(
                        "Ưu đãi mặc định - " + event.getName(),
                        "Promotion mặc định cho event " + event.getName() + ".",
                        "Tạo dữ liệu mẫu để kiểm thử màn hình promotion.",
                        PromotionType.PERCENT_DISCOUNT,
                        PromotionApplyMethod.AUTO,
                        PromotionStatus.DRAFT,
                        0,
                        3,
                        false,
                        0
                )
        );
    }

    private LocalDateTime resolvePromotionStartAt(Event event, int offsetDays) {
        LocalDateTime eventStartAt = event.getStartAt();
        LocalDateTime eventEndAt = event.getEndAt();

        LocalDateTime promotionStartAt = eventStartAt
                .plusDays(offsetDays)
                .withSecond(0)
                .withNano(0);

        if (promotionStartAt.isBefore(eventStartAt)) {
            return eventStartAt.withSecond(0).withNano(0);
        }

        if (promotionStartAt.isAfter(eventEndAt)) {
            return eventStartAt.withSecond(0).withNano(0);
        }

        return promotionStartAt;
    }

    private LocalDateTime resolvePromotionEndAt(
            Event event,
            LocalDateTime promotionStartAt,
            int durationDays
    ) {
        LocalDateTime promotionEndAt = promotionStartAt
                .plusDays(durationDays)
                .withHour(23)
                .withMinute(59)
                .withSecond(0)
                .withNano(0);

        if (promotionEndAt.isAfter(event.getEndAt())) {
            return event.getEndAt().withSecond(0).withNano(0);
        }

        return promotionEndAt;
    }

    private boolean isPromotionTimeInsideEvent(
            Event event,
            LocalDateTime promotionStartAt,
            LocalDateTime promotionEndAt
    ) {
        if (promotionStartAt == null || promotionEndAt == null) {
            return false;
        }

        if (!promotionStartAt.isBefore(promotionEndAt)) {
            return false;
        }

        return !promotionStartAt.isBefore(event.getStartAt())
                && !promotionEndAt.isAfter(event.getEndAt());
    }

    private PromotionStatus resolvePromotionStatus(
            Event event,
            PromotionStatus defaultStatus,
            LocalDateTime promotionStartAt,
            LocalDateTime promotionEndAt,
            LocalDateTime now
    ) {
        EventStatus eventStatus = event.getStatus();

        if (eventStatus == null) {
            return PromotionStatus.DRAFT;
        }

        return switch (eventStatus) {
            case DRAFT -> PromotionStatus.DRAFT;
            case SCHEDULED -> PromotionStatus.SCHEDULED;
            case PAUSED -> PromotionStatus.INACTIVE;
            case EXPIRED -> PromotionStatus.EXPIRED;
            case ENDED, CANCELLED -> PromotionStatus.CANCELLED;
            case ACTIVE -> resolveActiveEventPromotionStatus(
                    defaultStatus,
                    promotionStartAt,
                    promotionEndAt,
                    now
            );
        };
    }

    private PromotionStatus resolveActiveEventPromotionStatus(
            PromotionStatus defaultStatus,
            LocalDateTime promotionStartAt,
            LocalDateTime promotionEndAt,
            LocalDateTime now
    ) {
        if (defaultStatus == PromotionStatus.DRAFT
                || defaultStatus == PromotionStatus.INACTIVE
                || defaultStatus == PromotionStatus.CANCELLED) {
            return defaultStatus;
        }

        if (now.isBefore(promotionStartAt)) {
            return PromotionStatus.SCHEDULED;
        }

        if (!now.isBefore(promotionEndAt)) {
            return PromotionStatus.EXPIRED;
        }

        return PromotionStatus.ACTIVE;
    }

    private record PromotionSeedData(
            String name,
            String description,
            String objective,
            PromotionType type,
            PromotionApplyMethod applyMethod,
            PromotionStatus defaultStatus,
            int startOffsetDays,
            int durationDays,
            Boolean stackable,
            Integer priority
    ) {
    }
}