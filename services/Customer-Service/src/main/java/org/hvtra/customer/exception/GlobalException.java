package org.hvtra.customer.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")

public class GlobalException extends BaseGlobalExceptionHandler {

    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientAbortException(Exception exception) {
        log.debug("Client aborted request: {}", exception.getMessage());
    }
}

