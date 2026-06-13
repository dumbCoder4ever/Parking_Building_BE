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

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.session.reservation.user.userId = :userId AND UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')")
    Double sumTotalAmountByUserId(@Param("userId") String userId);

    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL
                OR (:status = 'UNPAID' AND UPPER(p.paymentStatus) NOT IN ('PAID', 'CONFIRMED', 'SUCCESS'))
                OR (:status = 'PAID' AND UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS'))
                OR (:status = 'AWAITING_CONFIRM' AND UPPER(p.paymentStatus) IN ('PAID', 'SUCCESS')))
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findAllByPaidStatus(@Param("status") String status, Pageable pageable);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.session.sessionId = :sessionId
            AND UPPER(p.paymentStatus) IN :statuses
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findBySessionSessionIdAndPaymentStatusInOrderByCreatedAtDesc(
            @Param("sessionId") String sessionId,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.session.reservation.user.userId = :driverId
            ORDER BY p.paymentTime DESC
            """)
    List<Payment> findByDriverIdOrderByPaymentTimeDesc(@Param("driverId") String driverId, Pageable pageable);
}
