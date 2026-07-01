package fpt.swp391.parkingmanagement.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR p.paymentTime >= :from)
            AND (:to IS NULL OR p.paymentTime <= :to)
            """)
    BigDecimal sumPaidRevenue(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(p)
            FROM Payment p
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR p.paymentTime >= :from)
            AND (:to IS NULL OR p.paymentTime <= :to)
            """)
    long countPaidPayments(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT b.buildingId AS buildingId,
                   b.buildingName AS buildingName,
                   COALESCE(SUM(p.amount), 0) AS totalRevenue,
                   COUNT(p.paymentId) AS paymentCount
            FROM Payment p
            JOIN p.session s
            JOIN s.slot sl
            JOIN sl.zone z
            JOIN z.floor f
            JOIN f.building b
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR p.paymentTime >= :from)
            AND (:to IS NULL OR p.paymentTime <= :to)
            GROUP BY b.buildingId, b.buildingName
            ORDER BY SUM(p.amount) DESC
            """)
    List<BuildingRevenueProjection> sumRevenueByBuilding(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ============ DASHBOARD STATS ============

    @Query("""
            SELECT p.paymentMethod AS paymentMethod,
                   COALESCE(SUM(p.amount), 0) AS totalRevenue,
                   COUNT(p.paymentId) AS count
            FROM Payment p
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR p.paymentTime >= :from)
            AND (:to IS NULL OR p.paymentTime <= :to)
            GROUP BY p.paymentMethod
            ORDER BY SUM(p.amount) DESC
            """)
    List<PaymentMethodProjection> sumRevenueByPaymentMethod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT DATE(p.payment_time) AS date,
                   COALESCE(SUM(p.amount), 0) AS revenue,
                   COUNT(p.payment_id) AS count
            FROM payments p
            WHERE UPPER(p.payment_status) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND p.payment_time >= :from
            AND p.payment_time <= :to
            GROUP BY DATE(p.payment_time)
            ORDER BY DATE(p.payment_time) ASC
            """, nativeQuery = true)
    List<RevenueTrendProjection> getRevenueTrend(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
