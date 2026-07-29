package fpt.swp391.parkingmanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, String> {
    Optional<Ticket> findByTicketCode(String ticketCode);

    Optional<Ticket> findByReservationReservationId(String reservationId);

    /**
     * FIX N+1: Batch-load tickets cho nhiều reservation trong 1 query.
     */
    List<Ticket> findByReservationReservationIdIn(Collection<String> reservationIds);

    @Query("SELECT t FROM Ticket t JOIN FETCH t.reservation r JOIN FETCH r.user WHERE t.ticketCode = :ticketCode")
    Optional<Ticket> findByTicketCodeWithUser(@Param("ticketCode") String ticketCode);

    /**
     * FIX N+1: Full graph fetch for ticket lookup in checkin flow.
     * Loads reservation, vehicle, slot chain in one query.
     */
    @Query("SELECT t FROM Ticket t "
            + "LEFT JOIN FETCH t.reservation r "
            + "LEFT JOIN FETCH r.vehicle rv LEFT JOIN FETCH rv.vehicleType "
            + "LEFT JOIN FETCH r.slot s LEFT JOIN FETCH s.zone z LEFT JOIN FETCH z.floor LEFT JOIN FETCH z.floor.building "
            + "LEFT JOIN FETCH r.user "
            + "WHERE t.ticketCode = :ticketCode")
    Optional<Ticket> findByTicketCodeGraph(@Param("ticketCode") String ticketCode);
}
