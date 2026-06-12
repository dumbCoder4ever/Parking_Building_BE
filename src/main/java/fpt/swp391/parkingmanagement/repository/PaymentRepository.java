package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByTransactionCode(String transactionCode);

    Optional<Payment> findFirstBySessionSessionIdAndPaymentStatusOrderByCreatedAtDesc(
            String sessionId, String paymentStatus);

    @Query("SELECT p FROM Payment p WHERE p.session.reservation.user.userId = :userId ORDER BY p.paymentTime DESC")
    List<Payment> findByUserIdOrderByPaymentTimeDesc(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.session.reservation.user.userId = :userId")
    Double sumTotalAmountByUserId(@Param("userId") String userId);
}
