package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @Query("SELECT p FROM Payment p WHERE p.session.reservation.user.userId = :userId ORDER BY p.paymentTime DESC")
    List<Payment> findByUserIdOrderByPaymentTimeDesc(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.session.reservation.user.userId = :userId")
    Double sumTotalAmountByUserId(@Param("userId") String userId);
}
