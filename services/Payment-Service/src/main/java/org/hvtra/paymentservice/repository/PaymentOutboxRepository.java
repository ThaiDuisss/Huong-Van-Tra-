package org.hvtra.paymentservice.repository;


import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;
import org.hvtra.paymentservice.domain.entity.PaymentOutbox;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOutboxRepository
        extends BaseOutboxEventRepository<PaymentOutbox> {
}