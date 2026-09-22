package org.hvtra.commonservice.logging.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hvtra.commonservice.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.logging.RequestLoggingFilter;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;


/**
 * Bắt các method có {@link UserAction}, đo thời gian, lấy user và request hiện tại,
 * đánh giá SpEL cho entityId và detail, rồi giao cho {@link ActionLogPublisher}.
 * Lỗi trong aspect chỉ ghi WARN, không làm hỏng nghiệp vụ.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class UserActionAspect {
    private final ActionLogPublisher publisher;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(userAction)")
    public Object around(ProceedingJoinPoint joinPoint, UserAction userAction) throws Throwable {
        long start = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            if (failure == null || userAction.logOnFailure()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                safePublish(joinPoint, userAction, result, failure, durationMs);
            }
        }
    }

    private void safePublish(ProceedingJoinPoint joinPoint, UserAction userAction,
                             Object result, Throwable failure, long durationMs) {
        try {
            EvaluationContext context = buildContext(joinPoint, result);
            ActionLogEvent.ActionLogEventBuilder builder = ActionLogEvent.builder()
                    .action(userAction.value())
                    .entityType(emptyToNull(userAction.entityType()))
                    .entityId(evaluate(userAction.entityId(), context))
                    .detail(evaluate(userAction.detail(), context))
                    .durationMs(durationMs)
                    .status(failure == null ? "SUCCESS" : "FAIL")
                    .errorMessage(failure == null ? null : failure.getMessage());

            fillUser(builder);
            fillRequest(builder);
            publisher.publish(builder.build());
        } catch (Exception ex) {
            log.warn("Cannot build action log for {}: {}", userAction.value(), ex.getMessage());
        }
    }

    private EvaluationContext buildContext(ProceedingJoinPoint joinPoint, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        context.setVariable("result", result);
        return context;
    }


    private String evaluate(String expression, EvaluationContext context) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            Object value = parser.parseExpression(expression).getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ex) {
            // Biểu thức lỗi (ví dụ #result null khi FAIL) thì bỏ qua trường đó
            return null;
        }
    }

    private void fillUser(ActionLogEvent.ActionLogEventBuilder builder) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof InternalUserPrincipal principal) {
            builder.userId(principal.userId());
            builder.username(emptyToNull(principal.username()));
            builder.requestId(principal.correlationId());
        }
    }

    private void fillRequest(ActionLogEvent.ActionLogEventBuilder builder) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            builder.httpMethod(request.getMethod());
            builder.path(request.getRequestURI());
            builder.ip(RequestLoggingFilter.clientIp(request));
        }
    }
    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
