package org.hvtra.paymentservice.client;

import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.paymentservice.dto.response.OrderPaymentInfoResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderClient {

    private final RestTemplate restTemplate;

//    private static final String ORDER_SERVICE_URL = "http://order-service/api/orders";
    private static final String ORDER_SERVICE_URL = "http://localhost:8086/api/orders";

    public OrderPaymentInfoResponse getPaymentInfo(UUID orderId) {
        return restTemplate.getForObject(
                ORDER_SERVICE_URL + "/" + orderId + "/payment-info",
                OrderPaymentInfoResponse.class
        );
    }

    public void markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        restTemplate.put(
                ORDER_SERVICE_URL + "/" + orderId + "/mark-paid",
                request
        );
    }
}