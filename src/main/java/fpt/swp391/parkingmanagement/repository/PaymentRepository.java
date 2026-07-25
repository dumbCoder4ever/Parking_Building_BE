package fpt.swp391.parkingmanagement.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {
            "session",
            "session.reservation",
            "session.reservation.user",
            "session.ticket"
    })
    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL
                OR (:status = 'UNPAID' AND UPPER(p.paymentStatus) NOT IN ('PAID', 'CONFIRMED', 'SUCCESS'))
                OR (:status = 'PAID' AND UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS'))
                OR (:status = 'AWAITING_CONFIRM' AND UPPER(p.paymentStatus) IN ('PAID', 'SUCCESS')))
            AND (:paymentMethod IS NULL OR UPPER(p.paymentMethod) = UPPER(:paymentMethod))
            AND (:from IS NULL OR COALESCE(p.paymentTime, p.createdAt) >= :from)
            AND (:to IS NULL OR COALESCE(p.paymentTime, p.createdAt) <= :to)
            ORDER BY COALESCE(p.paymentTime, p.createdAt) DESC
            """)
    List<Payment> findForStaffList(
            @Param("status") String status,
            @Param("paymentMethod") String paymentMethod,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "session",
            "session.reservation",
            "session.reservation.user",
            "session.ticket"
    })
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
            LEFT JOIN p.session s
            LEFT JOIN s.slot sl
            LEFT JOIN sl.zone z
            LEFT JOIN z.floor f
            LEFT JOIN f.building b
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR COALESCE(p.paymentTime, p.createdAt) >= :from)
            AND (:to IS NULL OR COALESCE(p.paymentTime, p.createdAt) <= :to)
            AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            """)
    BigDecimal sumPaidRevenue(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

    @Query("""
            SELECT COUNT(p)
            FROM Payment p
            LEFT JOIN p.session s
            LEFT JOIN s.slot sl
            LEFT JOIN sl.zone z
            LEFT JOIN z.floor f
            LEFT JOIN f.building b
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR COALESCE(p.paymentTime, p.createdAt) >= :from)
            AND (:to IS NULL OR COALESCE(p.paymentTime, p.createdAt) <= :to)
            AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            """)
    long countPaidPayments(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

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
            AND (:from IS NULL OR COALESCE(p.paymentTime, p.createdAt) >= :from)
            AND (:to IS NULL OR COALESCE(p.paymentTime, p.createdAt) <= :to)
            AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            GROUP BY b.buildingId, b.buildingName
            ORDER BY SUM(p.amount) DESC
            """)
    List<BuildingRevenueProjection> sumRevenueByBuilding(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

    // ============ DASHBOARD STATS ============

    @Query("""
            SELECT p.paymentMethod AS paymentMethod,
                   COALESCE(SUM(p.amount), 0) AS totalRevenue,
                   COUNT(p.paymentId) AS count
            FROM Payment p
            LEFT JOIN p.session s
            LEFT JOIN s.slot sl
            LEFT JOIN sl.zone z
            LEFT JOIN z.floor f
            LEFT JOIN f.building b
            WHERE UPPER(p.paymentStatus) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND (:from IS NULL OR COALESCE(p.paymentTime, p.createdAt) >= :from)
            AND (:to IS NULL OR COALESCE(p.paymentTime, p.createdAt) <= :to)
            AND (:buildingId IS NULL OR b.buildingId = :buildingId)
            GROUP BY p.paymentMethod
            ORDER BY SUM(p.amount) DESC
            """)
    List<PaymentMethodProjection> sumRevenueByPaymentMethod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

    @Query(value = """
            SELECT DATE(COALESCE(p.payment_time, p.created_at)) AS date,
                   COALESCE(SUM(p.amount), 0) AS revenue,
                   COUNT(p.payment_id) AS count
            FROM payments p
            LEFT JOIN parking_sessions ps ON p.session_id = ps.session_id
            LEFT JOIN parking_slots sl ON ps.slot_id = sl.slot_id
            LEFT JOIN zones z ON sl.zone_id = z.zone_id
            LEFT JOIN floors f ON z.floor_id = f.floor_id
            WHERE UPPER(p.payment_status) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND COALESCE(p.payment_time, p.created_at) >= :from
            AND COALESCE(p.payment_time, p.created_at) <= :to
            AND (:buildingId IS NULL OR f.building_id = :buildingId)
            GROUP BY DATE(COALESCE(p.payment_time, p.created_at))
            ORDER BY DATE(COALESCE(p.payment_time, p.created_at)) ASC
            """, nativeQuery = true)
    List<RevenueTrendProjection> getRevenueTrend(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);

    @Query(value = """
            SELECT DATE(COALESCE(p.payment_time, p.created_at)) AS date,
                   p.payment_method AS paymentMethod,
                   COALESCE(SUM(p.amount), 0) AS revenue,
                   COUNT(p.payment_id) AS count
            FROM payments p
            LEFT JOIN parking_sessions ps ON p.session_id = ps.session_id
            LEFT JOIN parking_slots sl ON ps.slot_id = sl.slot_id
            LEFT JOIN zones z ON sl.zone_id = z.zone_id
            LEFT JOIN floors f ON z.floor_id = f.floor_id
            WHERE UPPER(p.payment_status) IN ('PAID', 'CONFIRMED', 'SUCCESS')
            AND COALESCE(p.payment_time, p.created_at) >= :from
            AND COALESCE(p.payment_time, p.created_at) <= :to
            AND (:buildingId IS NULL OR f.building_id = :buildingId)
            GROUP BY DATE(COALESCE(p.payment_time, p.created_at)), p.payment_method
            ORDER BY DATE(COALESCE(p.payment_time, p.created_at)) ASC, p.payment_method ASC
            """, nativeQuery = true)
    List<RevenueTrendByMethodProjection> getRevenueTrendByPaymentMethod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("buildingId") String buildingId);
}
