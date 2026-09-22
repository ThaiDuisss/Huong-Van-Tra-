package org.hvtra.notificationservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class GlobalException extends BaseGlobalExceptionHandler {

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException exception) {
        log.debug("SSE client disconnected before response completed: {}", exception.getMessage());
    }
}
