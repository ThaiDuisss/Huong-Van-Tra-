package org.hvtra.paymentservice.repository;

import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    boolean existsByProviderAndExternalTransactionId(
            PaymentProvider provider,
            String externalTransactionId
    );

    Optional<PaymentTransaction> findByRefundReference(String refundReference);

    @Query("""
            select transaction
            from PaymentTransaction transaction
            join fetch transaction.payment payment
            where transaction.refundReference in :refundReferences
              and transaction.transactionType = :transactionType
              and transaction.status = :status
            """)
    List<PaymentTransaction> findRefundTransactions(
            @Param("refundReferences") Collection<String> refundReferences,
            @Param("transactionType") PaymentTransactionType transactionType,
            @Param("status") PaymentTransactionStatus status
    );
}
