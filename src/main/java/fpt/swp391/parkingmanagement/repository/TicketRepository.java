package fpt.swp391.parkingmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, String> {
    Optional<Ticket> findByTicketCode(String ticketCode);

    Optional<Ticket> findByReservationReservationId(String reservationId);

    @Query("SELECT t FROM Ticket t JOIN FETCH t.reservation r JOIN FETCH r.user WHERE t.ticketCode = :ticketCode")
    Optional<Ticket> findByTicketCodeWithUser(@Param("ticketCode") String ticketCode);
}
