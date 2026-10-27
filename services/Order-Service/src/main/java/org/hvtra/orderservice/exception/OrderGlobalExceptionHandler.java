package org.hvtra.orderservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class OrderGlobalExceptionHandler extends BaseGlobalExceptionHandler {
    // Xử lý AccessDeniedException (403) đã chuyển lên BaseGlobalExceptionHandler dùng chung.
}
