package org.hvtra.promotionservice.bootstrap;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.promotionservice.entity.Event;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
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
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Order(1)
public class EventDataInitializer implements ApplicationRunner {

    EventRepository eventRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (eventRepository.count() > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now()
                .withSecond(0)
                .withNano(0);

        seedEvents().forEach(seed -> {
            LocalDateTime startAt = now.plusDays(seed.startOffsetDays()).withHour(0).withMinute(0);
            LocalDateTime endAt = startAt.plusDays(seed.durationDays()).withHour(23).withMinute(59);

            Event event = Event.builder()
                    .name(seed.name())
                    .description(seed.description())
                    .startAt(startAt)
                    .endAt(endAt)
                    .eventType(seed.eventType())
                    .audience(seed.audience())
                    .status(seed.status())
                    .build();

            eventRepository.save(event);
        });
        log.info("Seeded {} events successfully.", seedEvents().size());
    }

    private List<EventSeedData> seedEvents() {
        return List.of(
                new EventSeedData("Tết Sum Vầy Cùng Hương Vân Trà", "Campaign Tết dành cho khách mua trà biếu, hộp quà và combo gia đình.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 10, 20),
                new EventSeedData("Xuân An Khang - Ưu Đãi Hộp Quà", "Ưu đãi các set trà quà biếu cao cấp trong mùa Tết.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 12, 18),
                new EventSeedData("VIP Đón Tết Sang", "Đặc quyền Tết dành riêng cho nhóm khách VIP và khách chi tiêu cao.", EventType.SEASONAL_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 15, 15),
                new EventSeedData("Lì Xì Đơn Đầu Năm", "Tặng ưu đãi cho khách phát sinh đơn hàng đầu tiên trong năm mới.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 18, 12),
                new EventSeedData("Combo Trà Tết Cho Gia Đình", "Khuyến khích mua nhiều sản phẩm trà và bánh trong cùng một đơn hàng.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 20, 14),

                new EventSeedData("Flash Sale Trà Đào Cam Sả", "Flash sale ngắn hạn cho dòng trà đào cam sả bán chạy.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -1, 5),
                new EventSeedData("Giờ Vàng Trà Sữa Trân Châu", "Ưu đãi nhanh trong khung giờ cao điểm cho các món trà sữa.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -2, 6),
                new EventSeedData("Deal Chớp Nhoáng Buổi Trưa", "Campaign kích cầu đơn hàng vào khung giờ trưa.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -3, 7),
                new EventSeedData("Flash Sale Cuối Tuần", "Ưu đãi cuối tuần cho combo đồ uống nhóm bạn.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -4, 8),
                new EventSeedData("Săn Deal 20K", "Campaign giá tốt số lượng giới hạn cho một số sản phẩm chọn lọc.", EventType.FLASH_SALE, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.ACTIVE, -1, 4),

                new EventSeedData("Xả Kho Trà Ô Long Đóng Gói", "Clearance campaign cho nhóm sản phẩm trà ô long đóng gói tồn kho.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -5, 12),
                new EventSeedData("Dọn Kho Cuối Tháng", "Ưu đãi xả hàng các sản phẩm bán chậm cuối tháng.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -6, 10),
                new EventSeedData("Clearance Topping Trân Châu", "Giảm giá topping và nguyên liệu gần tới kỳ xoay vòng tồn kho.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.PAUSED, -4, 14),
                new EventSeedData("Xả Bộ Sưu Tập Mùa Cũ", "Ưu đãi sản phẩm theo mùa cũ trước khi ra mắt menu mới.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -7, 15),
                new EventSeedData("Deal Thanh Lý Hộp Quà", "Campaign xả các mẫu hộp quà còn tồn sau mùa lễ.", EventType.CLEARANCE_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.PAUSED, -5, 20),

                new EventSeedData("Tri Ân Hội Viên Thân Thiết", "Tặng ưu đãi riêng cho khách hàng có lịch sử mua hàng thường xuyên.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.ACTIVE, -10, 25),
                new EventSeedData("Gold Member Tea Week", "Tuần lễ ưu đãi dành riêng cho nhóm khách Gold.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.ACTIVE, -6, 14),
                new EventSeedData("VIP Privilege Month", "Campaign đặc quyền tháng cho khách VIP.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.ACTIVE, -8, 30),
                new EventSeedData("Tích Điểm Gấp Đôi", "Tăng điểm loyalty cho khách phát sinh đơn trong thời gian diễn ra campaign.", EventType.LOYALTY_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -3, 18),
                new EventSeedData("Khách Cũ Quay Lại", "Ưu đãi kéo lại khách hàng lâu chưa phát sinh đơn.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.ACTIVE, -5, 16),

                new EventSeedData("Đơn Từ 199K Nhận Voucher", "Kích thích tăng giá trị đơn hàng bằng voucher cho đơn đạt mốc tối thiểu.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -2, 15),
                new EventSeedData("Mua 4 Ly Giảm Sâu", "Ưu đãi cho đơn hàng có số lượng sản phẩm lớn.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -3, 12),
                new EventSeedData("Combo Văn Phòng Buổi Chiều", "Campaign dành cho đơn hàng nhóm từ văn phòng.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -1, 20),
                new EventSeedData("Đặt 3 Đơn Nhận Quà", "Thưởng khách hàng hoàn tất nhiều đơn trong thời gian campaign.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -6, 25),
                new EventSeedData("Freeship Đơn Nhóm", "Hỗ trợ giao hàng cho đơn hàng có nhiều sản phẩm.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.ACTIVE, -4, 18),

                new EventSeedData("Ưu Đãi Sinh Nhật Khách Hàng", "Campaign thủ công cho danh sách khách hàng có sinh nhật trong tháng.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.SCHEDULED, 5, 25),
                new EventSeedData("Campaign Riêng Cho Khách Doanh Nghiệp", "Ưu đãi theo danh sách khách hàng doanh nghiệp do admin cấu hình.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.SCHEDULED, 7, 20),
                new EventSeedData("Ưu Đãi Khách Gần Cửa Hàng", "Campaign áp dụng cho danh sách khách trong khu vực phục vụ.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.SCHEDULED, 8, 15),
                new EventSeedData("Chăm Sóc Khách Hàng Đặc Biệt", "Ưu đãi riêng cho nhóm khách được chăm sóc bởi bộ phận CRM.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.SCHEDULED, 9, 18),
                new EventSeedData("Ưu Đãi Theo Danh Sách Import", "Campaign được admin tạo từ danh sách khách hàng import.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.DRAFT, 12, 20),

                new EventSeedData("Valentine Ngọt Ngào", "Ưu đãi combo đồ uống đôi trong dịp Valentine.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 30, 5),
                new EventSeedData("Ngày Đôi 02/02", "Campaign ngày đôi với ưu đãi ngắn hạn cho toàn hệ thống.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 25, 2),
                new EventSeedData("Mừng 8/3 - Trà Ngọt Trao Yêu Thương", "Campaign tri ân khách hàng nữ trong ngày Quốc tế Phụ nữ.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 60, 7),
                new EventSeedData("Ladies Tea Week", "Ưu đãi riêng cho nhóm khách nữ và khách thân thiết.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 62, 7),
                new EventSeedData("Tea Break Văn Phòng", "Khuyến khích đặt đơn số lượng lớn cho team văn phòng.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 65, 15),

                new EventSeedData("Đại Lễ 30/4 - 1/5", "Campaign nghỉ lễ lớn cho combo trà, bánh và đồ uống mang đi.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 90, 8),
                new EventSeedData("Hello Summer", "Campaign mùa hè cho trà trái cây, trà chanh và đồ uống mát lạnh.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 110, 30),
                new EventSeedData("Summer Flash Deal", "Flash sale cho các món giải nhiệt mùa hè.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 115, 5),
                new EventSeedData("Tích Điểm Mùa Hè", "Tăng điểm thành viên cho khách mua sản phẩm mùa hè.", EventType.LOYALTY_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 120, 20),
                new EventSeedData("Combo Gia Đình Ngày Hè", "Ưu đãi đơn hàng nhiều sản phẩm cho gia đình.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 125, 18),

                new EventSeedData("Back To School", "Campaign mùa tựu trường cho học sinh, sinh viên và phụ huynh.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 160, 25),
                new EventSeedData("Student Tea Combo", "Ưu đãi combo đồ uống dành cho nhóm khách sinh viên.", EventType.ORDER_INCENTIVE, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 165, 20),
                new EventSeedData("Flash Sale Trà Sữa Tựu Trường", "Flash sale ngắn hạn cho trà sữa và topping.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 168, 3),
                new EventSeedData("Tích Điểm Tựu Trường", "Tặng điểm loyalty cho đơn hàng mùa tựu trường.", EventType.LOYALTY_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 170, 15),
                new EventSeedData("Ưu Đãi Theo Lớp Học", "Campaign thủ công cho danh sách khách hàng nhóm lớp hoặc câu lạc bộ.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.DRAFT, 175, 12),

                new EventSeedData("Trung Thu Đoàn Viên", "Campaign Trung Thu với combo trà, bánh và hộp quà biếu.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 200, 20),
                new EventSeedData("Hộp Quà Trung Thu Cao Cấp", "Ưu đãi đơn hàng giá trị cao cho gift set Trung Thu.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 202, 18),
                new EventSeedData("VIP Trung Thu Gift Set", "Đặc quyền Trung Thu cho khách VIP.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 205, 15),
                new EventSeedData("Xả Kho Hộp Quà Trung Thu", "Clearance campaign cho hộp quà sau mùa Trung Thu.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 225, 10),
                new EventSeedData("Khách Doanh Nghiệp Trung Thu", "Campaign riêng cho khách doanh nghiệp mua quà số lượng lớn.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.DRAFT, 208, 20),

                new EventSeedData("Ngày Phụ Nữ Việt Nam 20/10", "Campaign tri ân khách hàng nữ với combo trà hoa và quà tặng.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 240, 7),
                new EventSeedData("Quà Tặng 20/10 Cho Khách Thân Thiết", "Ưu đãi riêng cho khách hàng thân thiết trong dịp 20/10.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 242, 7),
                new EventSeedData("Đơn Từ 399K Tặng Trà Hoa", "Ưu đãi cho đơn hàng đạt giá trị cao trong dịp 20/10.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 243, 8),
                new EventSeedData("Flash Deal 20/10", "Flash sale giới hạn trong tuần lễ 20/10.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 244, 3),
                new EventSeedData("Khách Sinh Nhật Tháng Mười", "Campaign riêng cho danh sách khách sinh nhật tháng mười.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.DRAFT, 245, 25),

                new EventSeedData("Mega Sale 11/11", "Campaign ngày đôi 11/11 cho toàn bộ khách hàng.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 265, 3),
                new EventSeedData("Black Friday Hương Vân Trà", "Chiến dịch giảm giá lớn cuối tháng mười một.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 278, 7),
                new EventSeedData("VIP Black Friday Early Access", "Ưu đãi mở sớm cho khách VIP trước Black Friday.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 275, 5),
                new EventSeedData("Xả Kho Cuối Năm", "Clearance campaign cho sản phẩm tồn kho cuối năm.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 285, 20),
                new EventSeedData("Đơn Lớn Cuối Năm", "Kích thích khách mua combo, quà tặng và đơn hàng giá trị cao.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 288, 18),

                new EventSeedData("Giáng Sinh An Lành", "Campaign Giáng Sinh cho combo trà, bánh và set quà tặng.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 310, 20),
                new EventSeedData("Christmas Gift Set", "Ưu đãi hộp quà Giáng Sinh cho đơn hàng đạt giá trị tối thiểu.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 312, 18),
                new EventSeedData("VIP Christmas Privilege", "Đặc quyền Giáng Sinh dành cho khách VIP.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.SCHEDULED, 315, 15),
                new EventSeedData("Flash Sale Noel", "Ưu đãi nhanh cho đồ uống mùa Noel.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 318, 4),
                new EventSeedData("Countdown Tea Party", "Campaign cuối năm cho combo nhóm và đồ uống bán chạy.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.SCHEDULED, 325, 7),

                new EventSeedData("Campaign Nháp - Menu Mùa Xuân", "Bản nháp campaign menu mùa xuân, chưa public.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.DRAFT, 40, 15),
                new EventSeedData("Campaign Nháp - Flash Sale Trà Mới", "Bản nháp flash sale cho sản phẩm mới.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.DRAFT, 45, 3),
                new EventSeedData("Campaign Nháp - Khách VIP", "Bản nháp ưu đãi riêng cho khách VIP.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.DRAFT, 50, 10),
                new EventSeedData("Campaign Nháp - Xả Kho Thử Nghiệm", "Bản nháp xả hàng thử nghiệm cho sản phẩm tồn.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.DRAFT, 55, 12),
                new EventSeedData("Campaign Nháp - Đơn Nhóm", "Bản nháp campaign kích thích đơn nhóm.", EventType.ORDER_INCENTIVE, EventAudience.ALL_CUSTOMERS, EventStatus.DRAFT, 58, 14),

                new EventSeedData("Đã Hết Hạn - Flash Sale Trà Đen", "Campaign cũ đã hết hạn tự nhiên.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.EXPIRED, -60, 5),
                new EventSeedData("Đã Hết Hạn - Ưu Đãi Tháng Trước", "Campaign tháng trước đã quá thời gian kết thúc.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.EXPIRED, -50, 10),
                new EventSeedData("Đã Hết Hạn - Tích Điểm Cũ", "Campaign loyalty cũ đã hết hạn.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.EXPIRED, -45, 12),
                new EventSeedData("Đã Đóng - Campaign Khai Trương", "Campaign khai trương đã được admin kết thúc.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.ENDED, -90, 20),
                new EventSeedData("Đã Đóng - Tri Ân Khách Hàng", "Campaign tri ân đã hoàn tất và được đóng.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.ENDED, -80, 15),

                new EventSeedData("Đã Hủy - Flash Sale Sai Cấu Hình", "Campaign bị hủy do cấu hình ưu đãi không đúng.", EventType.FLASH_SALE, EventAudience.ALL_CUSTOMERS, EventStatus.CANCELLED, -20, 3),
                new EventSeedData("Đã Hủy - Hộp Quà Không Triển Khai", "Campaign hộp quà bị hủy vì không triển khai sản phẩm.", EventType.SEASONAL_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.CANCELLED, -15, 10),
                new EventSeedData("Đã Hủy - Danh Sách Khách Lỗi", "Campaign manual bị hủy do danh sách khách hàng không hợp lệ.", EventType.MANUAL_CAMPAIGN, EventAudience.SPECIFIC_CUSTOMERS, EventStatus.CANCELLED, -10, 7),
                new EventSeedData("Tạm Dừng - Xả Kho Trà Lài", "Campaign clearance đang tạm dừng để kiểm tra tồn kho.", EventType.CLEARANCE_CAMPAIGN, EventAudience.ALL_CUSTOMERS, EventStatus.PAUSED, -6, 20),
                new EventSeedData("Tạm Dừng - Ưu Đãi Khách Gold", "Campaign loyalty đang tạm dừng để điều chỉnh điều kiện áp dụng.", EventType.LOYALTY_CAMPAIGN, EventAudience.CUSTOMER_SEGMENT, EventStatus.PAUSED, -4, 18)
        );
    }

    private record EventSeedData(
            String name,
            String description,
            EventType eventType,
            EventAudience audience,
            EventStatus status,
            int startOffsetDays,
            int durationDays
    ) {
    }
}