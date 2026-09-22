package org.hvtra.commonservice.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 10 + * Sao chép MDC (requestId, userId, service) sang thread chạy {@code @Async}
 11 + * và các TaskExecutor của Spring Boot, để log trong thread con vẫn có requestId.
 12 + * Spring Boot tự nhận bean TaskDecorator cho executor mặc định.
 13 + */
@Component

public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if(context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            }finally {
                if(previous != null) {
                    MDC.setContextMap(previous);
                }else {
                    MDC.clear();
                }
            }
        };
    }
}
