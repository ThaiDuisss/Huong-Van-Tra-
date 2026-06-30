package org.hvtra.catalog.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // Kiểm tra nếu Bean đang khởi tạo chính là Flyway
        if (bean instanceof Flyway) {
            Flyway flyway = (Flyway) bean;
            try {
                System.out.println("====== [HVTRA] Đang tự động thực hiện Flyway Repair để dọn rác V7... ======");
                // Chủ động gọi lệnh repair() để gỡ trạng thái FAILED dưới DB hoàn toàn tự động
                flyway.repair();
                System.out.println("====== [HVTRA] Flyway Repair hoàn tất thành công! ======");
            } catch (Exception e) {
                System.err.println("====== [HVTRA] Lỗi khi chạy Flyway Repair: " + e.getMessage());
            }
        }
        return bean;
    }
}