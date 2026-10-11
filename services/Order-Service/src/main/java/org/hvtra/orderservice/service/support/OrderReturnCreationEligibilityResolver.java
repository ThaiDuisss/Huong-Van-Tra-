package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityCheckResponse;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityResponse;
import org.hvtra.orderservice.dto.response.OrderReturnItemEligibilityResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderReturnCreationEligibilityResolver {
    private static final BigDecimal MINIMUM_QUANTITY = new BigDecimal("0.001");

    public void requireValidOrderAndRequest(Order order, CreateOrderReturnRequest request) {
        requirePassed(orderChecks(order, request));
    }

    public OrderReturnEligibilityResponse resolve(
            Order order,
            CreateOrderReturnRequest request,
            Map<UUID, OrderItem> orderItems,
            Map<UUID, BigDecimal> returnedQuantities
    ) {
        List<OrderReturnEligibilityCheckResponse> checks = orderChecks(order, request);
        List<OrderReturnItemEligibilityResponse> items = new ArrayList<>();
        if (request != null && request.getItems() != null) {
            for (CreateOrderReturnItemRequest item : request.getItems()) {
                if (item != null) {
                    items.add(resolveItem(item, orderItems, returnedQuantities));
                }
            }
        }
        boolean eligible = checks.stream().allMatch(OrderReturnEligibilityCheckResponse::passed)
                && items.stream().flatMap(item -> item.checks().stream())
                .allMatch(OrderReturnEligibilityCheckResponse::passed);
        return new OrderReturnEligibilityResponse(eligible, List.copyOf(checks), List.copyOf(items));
    }

    public void requireEligible(OrderReturnEligibilityResponse result) {
        requirePassed(result.checks());
        for (OrderReturnItemEligibilityResponse item : result.items()) {
            requirePassed(item.checks());
        }
    }

    private List<OrderReturnEligibilityCheckResponse> orderChecks(Order order, CreateOrderReturnRequest request) {
        List<OrderReturnEligibilityCheckResponse> checks = new ArrayList<>();
        add(checks, "ORDER_EXISTS", order != null, "Đơn hàng tồn tại", "Không tìm thấy đơn hàng");
        if (order != null) {
            add(checks, "ORDER_COMPLETED", order.getStatus() == OrderStatus.COMPLETED,
                    "Đơn hàng đã hoàn thành", "Đơn hàng chưa hoàn thành");
            add(checks, "ORDER_NOT_FULLY_RETURNED", order.getReturnStatus() != OrderReturnStatus.FULLY_RETURNED,
                    "Đơn hàng chưa được hoàn toàn bộ", "Đơn hàng đã được hoàn toàn bộ");
            add(checks, "PAYMENT_VALID", order.getPaymentStatus() == PaymentStatus.PAID
                            || order.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED,
                    "Thanh toán hợp lệ", "Thanh toán phải ở trạng thái đã thanh toán hoặc đã hoàn tiền một phần");
        }
        boolean hasItems = request != null && request.getItems() != null && !request.getItems().isEmpty();
        add(checks, "ITEMS_REQUIRED", hasItems, "Đã chọn sản phẩm cần hoàn", "Vui lòng chọn sản phẩm cần hoàn");
        if (hasItems) {
            boolean identified = request.getItems().stream().allMatch(item -> item != null && item.getOrderItemId() != null);
            add(checks, "ITEM_IDENTIFIED", identified, "Thông tin sản phẩm đầy đủ", "Thiếu thông tin sản phẩm cần hoàn");
            HashSet<UUID> ids = new HashSet<>();
            boolean unique = request.getItems().stream().filter(item -> item != null && item.getOrderItemId() != null)
                    .allMatch(item -> ids.add(item.getOrderItemId()));
            add(checks, "ITEMS_UNIQUE", unique, "Sản phẩm không bị chọn trùng", "Không được chọn trùng sản phẩm trong phiếu hoàn");
        }
        return checks;
    }

    private OrderReturnItemEligibilityResponse resolveItem(
            CreateOrderReturnItemRequest request, Map<UUID, OrderItem> orderItems, Map<UUID, BigDecimal> returnedQuantities
    ) {
        OrderItem item = request.getOrderItemId() == null ? null : orderItems.get(request.getOrderItemId());
        BigDecimal purchased = item == null ? BigDecimal.ZERO : zeroIfNull(item.getQuantity());
        BigDecimal returned = request.getOrderItemId() == null ? BigDecimal.ZERO
                : zeroIfNull(returnedQuantities.get(request.getOrderItemId()));
        BigDecimal remaining = purchased.subtract(returned).max(BigDecimal.ZERO);
        BigDecimal quantity = zeroIfNull(request.getQuantity());
        List<OrderReturnEligibilityCheckResponse> checks = new ArrayList<>();
        add(checks, "ITEM_BELONGS_TO_ORDER", item != null, "Sản phẩm thuộc đơn hàng", "Sản phẩm không thuộc đơn hàng");
        add(checks, "QUANTITY_POSITIVE", quantity.compareTo(MINIMUM_QUANTITY) >= 0,
                "Số lượng hoàn hợp lệ", "Số lượng hoàn phải từ 0,001 trở lên");
        add(checks, "REASON_REQUIRED", request.getReason() != null, "Đã chọn lý do trả hàng", "Vui lòng chọn lý do trả hàng");
        add(checks, "CONDITION_REQUIRED", request.getCondition() != null,
                "Đã khai báo tình trạng sản phẩm", "Vui lòng khai báo tình trạng sản phẩm");
        if (request.getReason() == ReturnReason.OTHER) {
            add(checks, "OTHER_NOTE_REQUIRED", request.getNote() != null && !request.getNote().isBlank(),
                    "Đã ghi rõ lý do khác", "Vui lòng nhập ghi chú cho lý do khác");
        }
        if (request.getReason() == ReturnReason.CHANGE_OF_MIND) {
            add(checks, "CHANGE_OF_MIND_SEALED", request.getCondition() == ReturnCondition.SEALED,
                    "Sản phẩm được khai báo còn nguyên seal", "Khách đổi ý chỉ áp dụng với sản phẩm còn nguyên seal; QC sẽ xác nhận thực tế");
        }
        add(checks, "QUANTITY_REMAINING", remaining.signum() > 0,
                "Còn " + remaining.stripTrailingZeros().toPlainString().replace('.', ',') + " sản phẩm có thể hoàn",
                "Không còn số lượng có thể hoàn sau khi trừ các phiếu hoàn trước");
        add(checks, "QUANTITY_WITHIN_REMAINING", quantity.compareTo(remaining) <= 0,
                "Số lượng hoàn không vượt quá số lượng còn có thể hoàn", "Số lượng hoàn vượt quá số lượng còn có thể hoàn");
        return new OrderReturnItemEligibilityResponse(request.getOrderItemId(), request.getReason(), remaining,
                policyFor(request.getReason()), List.copyOf(checks));
    }

    private List<String> policyFor(ReturnReason reason) {
        if (reason == null) {
            return List.of();
        }
        List<String> policy = new ArrayList<>(switch (reason) {
            case DEFECTIVE -> List.of("Áp dụng khi sản phẩm bị lỗi; QC sẽ kiểm tra lỗi và tình trạng thực tế.");
            case DAMAGED -> List.of("Áp dụng khi sản phẩm bị hư hỏng hoặc không còn nguyên trạng.");
            case WRONG_ITEM -> List.of("Áp dụng khi sản phẩm nhận được không đúng với sản phẩm trong đơn hàng.");
            case QUALITY_ISSUE -> List.of("Áp dụng khi sản phẩm có vấn đề về chất lượng; sản phẩm bắt buộc được QC kiểm tra.");
            case EXPIRED -> List.of("Áp dụng khi sản phẩm hết hạn; QC sẽ kiểm tra hạn sử dụng và tình trạng thực tế.");
            case CHANGE_OF_MIND -> List.of("Áp dụng khi khách hàng thay đổi nhu cầu mua.",
                    "Theo chính sách hiện tại, sản phẩm phải được khai báo còn nguyên seal; QC sẽ xác nhận bao bì và tình trạng thực tế.");
            case OTHER -> List.of("Áp dụng với lý do khác; phải ghi rõ lý do trong ghi chú sản phẩm.");
        });
        policy.addAll(List.of("Sản phẩm phải thuộc đơn hàng đã hoàn thành, đã thanh toán hoặc đã hoàn tiền một phần.",
                "Số lượng hoàn không vượt quá số lượng còn có thể hoàn sau khi trừ các phiếu hoàn trước.",
                "Hàng trả sẽ được QC kiểm tra sau khi tiếp nhận; kết quả xử lý tồn kho do QC quyết định.",
                "Nếu yêu cầu hoàn tiền, chỉ xử lý khi các bước kiểm tra và xử lý hàng cần thiết đã hoàn tất."));
        return List.copyOf(policy);
    }

    private void requirePassed(List<OrderReturnEligibilityCheckResponse> checks) {
        checks.stream().filter(check -> !check.passed()).findFirst().ifPresent(check -> {
            String message = switch (check.code()) {
                case "ORDER_EXISTS" -> "Order not found";
                case "ORDER_COMPLETED" -> "Order is not completed";
                case "ORDER_NOT_FULLY_RETURNED" -> "Order has already been fully returned";
                case "PAYMENT_VALID" -> "Order payment status is not eligible for return";
                case "ITEMS_REQUIRED" -> "Return items are required";
                case "ITEM_IDENTIFIED" -> "Order item is required";
                case "ITEMS_UNIQUE" -> "Duplicate order item in return request";
                case "ITEM_BELONGS_TO_ORDER" -> "Order item does not belong to order";
                case "QUANTITY_POSITIVE" -> "Return quantity must be greater than 0";
                case "REASON_REQUIRED" -> "Return reason is required";
                case "CONDITION_REQUIRED" -> "Return condition is required";
                case "OTHER_NOTE_REQUIRED" -> "Note is required when reason is OTHER";
                case "CHANGE_OF_MIND_SEALED" -> "Change-of-mind return requires sealed product";
                case "QUANTITY_REMAINING" -> "No remaining quantity available for return";
                case "QUANTITY_WITHIN_REMAINING" -> "Return quantity exceeds remaining returnable quantity";
                default -> check.message();
            };
            throw new InvalidOrderReturnException(message);
        });
    }

    private void add(List<OrderReturnEligibilityCheckResponse> checks, String code, boolean passed, String success, String failure) {
        checks.add(new OrderReturnEligibilityCheckResponse(code, passed, passed ? success : failure));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
