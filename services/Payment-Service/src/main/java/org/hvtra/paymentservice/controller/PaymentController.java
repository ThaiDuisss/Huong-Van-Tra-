package org.hvtra.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.request.ManualPaymentConfirmRequest;
import org.hvtra.paymentservice.dto.request.RefundPaymentRequest;
import org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.dto.response.PaymentRefundResponse;
import org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse;
import org.hvtra.paymentservice.dto.response.SepayWebhookResponse;
import org.hvtra.paymentservice.service.PaymentService;
import org.hvtra.paymentservice.service.SepayWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SepayWebhookService sepayWebhookService;


    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thanh toán")
    public PaymentCheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return paymentService.checkout(request);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Lấy phương thức thanh toán mới nhất của đơn hàng")
    public OrderPaymentMethodResponse getPaymentByOrderId(@PathVariable("orderId") UUID orderId) {
        return paymentService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(summary = "Huỷ phiên chuyển khoản đang chờ tiền (thu ngân chọn lại phương thức)")
    public OrderPaymentMethodResponse cancelPendingPayment(@PathVariable("orderId") UUID orderId) {
        return paymentService.cancelPendingPayment(orderId);
    }

    @PostMapping("/orders/{orderId}/confirm-manual")
    @Operation(summary = "Đối soát tay: ghi PAID cho giao dịch cần kiểm tra")
    public OrderPaymentMethodResponse confirmManualPayment(
            @PathVariable("orderId") UUID orderId,
            @RequestBody(required = false) ManualPaymentConfirmRequest request
    ) {
        return paymentService.confirmManualPayment(orderId, request);
    }

    @PostMapping("/orders/{orderId}/refunds")
    @Operation(summary = "Hoàn tiền cho phiếu trả hàng")
    public PaymentRefundResponse refundOrderReturn(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody RefundPaymentRequest request
    ) {
        return paymentService.refundOrderReturn(orderId, request);
    }

    @PostMapping("/refunds/lookup")
    @Operation(summary = "Tra cứu trạng thái hoàn tiền theo phiếu hoàn hàng")
    public List<PaymentRefundSummaryResponse> lookupRefundSummaries(
            @Valid @RequestBody RefundSummaryLookupRequest request
    ) {
        return paymentService.lookupRefundSummaries(request);
    }

    @PostMapping("/webhooks/sepay")
    public SepayWebhookResponse handleSepayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        sepayWebhookService.handle(rawBody, signature, timestamp, authorization);
        return SepayWebhookResponse.of(true);
    }
}
