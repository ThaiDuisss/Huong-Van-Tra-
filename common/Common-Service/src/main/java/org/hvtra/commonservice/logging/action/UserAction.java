package org.hvtra.commonservice.logging.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một method cần ghi action log.
 * <pre>
 * {@code
 * @UserAction(value = "ORDER_CANCEL", entityType = "Order", entityId = "#orderId")
 * public OrderResponse cancel(UUID orderId, CancelRequest request) { ... }
 *
 * @UserAction(value = "ORDER_CREATE", entityType = "Order", entityId = "#result.id",
 *             detail = "#request.customerId")
 * public OrderResponse create(CreateOrderRequest request) { ... }
 * }
 * </pre>
 * {@code entityId} và {@code detail} là biểu thức SpEL: tham chiếu tham số theo tên
 * ({@code #orderId}, {@code #request.customerId}) hoặc kết quả trả về ({@code #result.id}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserAction {
    /** Mã hành động, viết HOA, ví dụ ORDER_CANCEL. */
    String value();

    String entityType() default "";

    /** SpEL trả về id đối tượng bị tác động. */
    String entityId() default "";

    /** SpEL trả về mô tả bổ sung. */
    String detail() default "";

    /** Có ghi log khi method ném exception hay không. */
    boolean logOnFailure() default true;
}
