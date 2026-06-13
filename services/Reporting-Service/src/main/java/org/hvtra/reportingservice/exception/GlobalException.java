package org.hvtra.reportingservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.exception.BaseGlobalExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class GlobalException extends BaseGlobalExceptionHandler {
}
