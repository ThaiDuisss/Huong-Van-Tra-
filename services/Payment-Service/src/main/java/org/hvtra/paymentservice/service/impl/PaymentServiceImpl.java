package org.hvtra.paymentservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.client.OrderClient;
import org.hvtra.paymentservice.domain.entity.Payments;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.CashPaymentResponse;
import org.hvtra.paymentservice.repository.PaymentRepository;
import org.hvtra.paymentservice.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_UNPAID = "UNPAID";
    private static final String TRANSACTION_SUCCESS = "SUCCESS";
    private static final String METHOD_CASH = "CASH";

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;

    @Override
    @Transactional
    public CashPaymentResponse payByCash(CheckoutRequest request) {

//        if (orderInfo == null) {
//            throw new IllegalArgumentException("Không lấy được thông tin đơn hàng");
//        }
//
//        if (!STATUS_UNPAID.equals(orderInfo.getPaymentStatus())) {
//            throw new IllegalArgumentException("Đơn hàng đã được thanh toán hoặc không hợp lệ");
//        }
//
//        BigDecimal amountRequired = orderInfo.getFinalAmount();
//        BigDecimal receivedAmount = request.getReceivedAmount();
//
//        if (receivedAmount.compareTo(amountRequired) < 0) {
//            throw new IllegalArgumentException("Số tiền khách đưa không đủ để thanh toán");
//        }
//
//        if (paymentRepository.existsByOrderIdAndStatusAndIsDeletedFalse(request.getOrderId(), STATUS_PAID)) {
//            throw new IllegalArgumentException("Đơn hàng này đã có payment PAID");
//        }
//
//        BigDecimal changeAmount = receivedAmount.subtract(amountRequired);
//        String paymentCode = generatePaymentCode();
//
//        Payments payment = Payments.builder()
//                .paymentCode(paymentCode)
//                .orderId(request.getOrderId())
//                .amountRequired(amountRequired)
//                .amountPaid(amountRequired)
//                .amountRefunded(BigDecimal.ZERO)
//                .status(STATUS_PAID)
//                .completedAt(Instant.now())
//                .build();
//
//        PaymentTransaction transaction = PaymentTransaction.builder()
//                .method(METHOD_CASH)
//                .amount(amountRequired)
//                .status(TRANSACTION_SUCCESS)
//                .provider(METHOD_CASH)
//                .externalTransactionId(null)
//                .paidAt(Instant.now())
//                .build();
//
//        Payments savedPayment = paymentRepository.save(payment);
//
//        MarkOrderPaidRequest markPaidRequest = MarkOrderPaidRequest.builder()
//                .paidAmount(amountRequired)
//                .paymentCode(paymentCode)
//                .changedBy(request.getCreatedBy())
//                .build();
//
//        orderClient.markOrderPaid(request.getOrderId(), markPaidRequest);
//
//        return CashPaymentResponse.builder()
//                .paymentId(savedPayment.getId())
//                .paymentCode(savedPayment.getPaymentCode())
//                .orderId(savedPayment.getOrderId())
//                .amountRequired(savedPayment.getAmountRequired())
//                .amountPaid(savedPayment.getAmountPaid())
//                .receivedAmount(receivedAmount)
//                .changeAmount(changeAmount)
//                .status(savedPayment.getStatus())
//                .method(METHOD_CASH)
//                .build();
        return null;
    }

    private String generatePaymentCode() {
        return "PAY" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}