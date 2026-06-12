package fpt.swp391.parkingmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Payment;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByTransactionCode(String transactionCode);

    Optional<Payment> findFirstBySessionSessionIdAndPaymentStatusOrderByCreatedAtDesc(
            String sessionId, String paymentStatus);
}
