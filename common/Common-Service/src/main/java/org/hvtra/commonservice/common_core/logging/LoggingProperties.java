package org.hvtra.commonservice.logging;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hvtra.logging")
public class LoggingProperties {

    /** Cấu hình access log (một dòng cho mỗi request HTTP). */
    private AccessLog accessLog = new AccessLog();

    /** Cấu hình action log (hành động người dùng, gửi Kafka hoặc ghi file). */
    private ActionLog actionLog = new ActionLog();

    @Getter
    @Setter
    public static class AccessLog {
        /** Bật/tắt ghi access log. */
        private boolean enabled = true;
        /** Có ghi request body (chỉ với POST/PUT/PATCH, JSON) hay không. */
        private boolean logBody = true;
        /** Độ dài tối đa của body được ghi, phần dư bị cắt. */
        private int maxBodyLength = 2000;
        /** Các path prefix bỏ qua không ghi access log. */
        private List<String> skipPaths = List.of(
                "/actuator",
                "/v3/api-docs",
                "/swagger-ui"
        );
    }

    @Getter
    @Setter
    public static class ActionLog {
        /** Bật/tắt toàn bộ action log. */
        private boolean enabled = true;
        /** Topic Kafka nhận action log. */
        private String topic = "action.log";
        /**
         * Kafka riêng cho action log. Để trống thì dùng {@code kafka-config.bootstrap-servers}
         * khi {@code kafka-config.enabled=true}, còn không thì ghi file. Đặt giá trị này để
         * service tắt Kafka nghiệp vụ vẫn gửi được action log.
         */
        private String bootstrapServers;
        /** Độ dài tối đa của trường detail. */
        private int maxDetailLength = 2000;
    }
}
