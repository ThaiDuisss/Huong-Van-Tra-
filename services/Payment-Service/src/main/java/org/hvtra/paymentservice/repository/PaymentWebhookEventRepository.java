package org.hvtra.paymentservice.repository;

import org.hvtra.paymentservice.domain.entity.PaymentWebhookEvent;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    boolean existsByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    );
}