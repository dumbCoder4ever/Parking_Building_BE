package fpt.swp391.parkingmanagement.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingSessionService {

    private final TicketRepository ticketRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final UserRepository userRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public ParkingSessionResponse checkin(String staffEmail, CheckinRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (Boolean.TRUE.equals(ticket.getIsUsed())) throw new RuntimeException("Ticket already used");

        // Check if ticket has expired
        if (ticket.getExpiredAt() != null && LocalDateTime.now().isAfter(ticket.getExpiredAt())) {
            throw new RuntimeException("Ticket has expired");
        }

        var reservation = ticket.getReservation();
        if (reservation == null) throw new RuntimeException("Reservation not found for ticket");
        if (!"APPROVED".equalsIgnoreCase(reservation.getReservationStatus())) {
            throw new RuntimeException("Reservation has not been approved yet");
        }

        if (req.getPlateNumber() != null && reservation.getVehicle() != null) {
            if (!req.getPlateNumber().equalsIgnoreCase(reservation.getVehicle().getPlateNumber())) {
                throw new RuntimeException("Plate number does not match reservation");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.getReservationEnd() != null) {
            Integer gracePeriodMinutes = reservation.getGracePeriodMinutes();
            int grace = gracePeriodMinutes != null ? gracePeriodMinutes : 15;
            if (now.isAfter(reservation.getReservationEnd().plusMinutes(grace))) {
                throw new RuntimeException("Reservation expired");
            }
        }

        ParkingSlot slot = reservation.getSlot();
        if (slot == null) throw new RuntimeException("Reserved slot not found");
        if (!"RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new RuntimeException("Slot is not in RESERVED status");
        }

        ParkingSession session = new ParkingSession();
        session.setTicket(ticket);
        session.setReservation(reservation);
        session.setVehicle(reservation.getVehicle());
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setSessionStatus("ACTIVE");
        session.setPaymentStatus("UNPAID");
        User staff = userRepository.findByEmail(staffEmail).orElse(null);
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        ticket.setIsUsed(true);
        ticket.setStatus("USED");
        ticketRepository.save(ticket);

        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        ParkingSessionResponse resp = new ParkingSessionResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setTicketCode(ticket.getTicketCode());
        applyHierarchy(resp, slot);
        resp.setVehiclePlate(saved.getVehicle() != null ? saved.getVehicle().getPlateNumber() : null);
        resp.setCheckinTime(saved.getCheckinTime());

        return resp;
    }

    @Transactional
    public CheckoutResponse checkout(String staffEmail, CheckoutRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        Optional<ParkingSession> optSession = parkingSessionRepository
                .findByTicketTicketIdAndSessionStatus(ticket.getTicketId(), "ACTIVE");

        ParkingSession session = optSession.orElseThrow(() -> new RuntimeException("Active parking session not found for this ticket"));

        LocalDateTime now = LocalDateTime.now();
        session.setCheckoutTime(now);

        long minutes = Duration.between(session.getCheckinTime(), now).toMinutes();
        int hours = (int) Math.ceil(minutes / 60.0);

        BigDecimal total = BigDecimal.ZERO;
        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            List<PricingPolicy> policies = pricingPolicyRepository.findByVehicleTypeVehicleTypeId(vtId);
            PricingPolicy policy = policies.stream()
                    .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (policy == null) {
                log.warn("No pricing policy found for vehicleTypeId={}; attempting to use any active policy as fallback", vtId);
                policy = pricingPolicyRepository.findAll().stream()
                        .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                        .findFirst()
                        .orElse(null);
            }

            if (policy == null) {
                log.warn("No active pricing policy available; charging total=0 for sessionId={}", session.getSessionId());
            } else {
                if (policy.getBasePrice() != null) total = total.add(policy.getBasePrice());
                if (policy.getHourlyRate() != null) total = total.add(policy.getHourlyRate().multiply(BigDecimal.valueOf(hours)));

                BigDecimal peakMultiplier = policy.getPeakHourMultiplier() != null ? policy.getPeakHourMultiplier() : BigDecimal.ONE;
                int hourOfDay = now.getHour();
                boolean isPeak = (hourOfDay >= 7 && hourOfDay < 9) || (hourOfDay >= 17 && hourOfDay < 19);
                if (isPeak && peakMultiplier.compareTo(BigDecimal.ONE) > 0) {
                    total = total.multiply(peakMultiplier);
                }

                if (policy.getMaxDailyFee() != null && policy.getMaxDailyFee().compareTo(BigDecimal.ZERO) > 0) {
                    if (total.compareTo(policy.getMaxDailyFee()) > 0) total = policy.getMaxDailyFee();
                }
            }
        }

        session.setTotalFee(total);
        session.setParkingDuration(hours);
        session.setPaymentStatus("PAID");
        session.setSessionStatus("COMPLETED");
        if (session.getReservation() != null) {
            session.getReservation().setReservationStatus("COMPLETED");
        }

        ParkingSession saved = parkingSessionRepository.save(session);

        Payment payment = new Payment();
        payment.setSession(saved);
        payment.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH");
        payment.setAmount(total);
        payment.setPaymentStatus("SUCCESS");
        Payment savedPayment = paymentRepository.save(payment);

        ParkingSlot slot = saved.getSlot();
        if (slot != null) {
            slot.setSlotStatus("AVAILABLE");
            parkingSlotRepository.save(slot);
        }

        CheckoutResponse resp = new CheckoutResponse();
        resp.setSessionId(saved.getSessionId());
        if (slot != null) {
            applyHierarchy(resp, slot);
        }
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setPaymentId(savedPayment.getPaymentId());

        return resp;
    }

    private void applyHierarchy(ParkingSessionResponse resp, ParkingSlot slot) {
        resp.setSlotId(slot.getSlotId());
        resp.setSlotName(slot.getSlotName());

        Zone zone = slot.getZone();
        if (zone != null) {
            resp.setZoneId(zone.getZoneId());
            resp.setZoneName(zone.getZoneName());

            Floor floor = zone.getFloor();
            if (floor != null) {
                resp.setFloorId(floor.getFloorId());
                resp.setFloorName(floor.getFloorName());

                Building building = floor.getBuilding();
                if (building != null) {
                    resp.setBuildingId(building.getBuildingId());
                    resp.setBuildingName(building.getBuildingName());
                }
            }
        }
    }

    private void applyHierarchy(CheckoutResponse resp, ParkingSlot slot) {
        resp.setSlotId(slot.getSlotId());
        resp.setSlotName(slot.getSlotName());

        Zone zone = slot.getZone();
        if (zone != null) {
            resp.setZoneId(zone.getZoneId());
            resp.setZoneName(zone.getZoneName());

            Floor floor = zone.getFloor();
            if (floor != null) {
                resp.setFloorId(floor.getFloorId());
                resp.setFloorName(floor.getFloorName());

                Building building = floor.getBuilding();
                if (building != null) {
                    resp.setBuildingId(building.getBuildingId());
                    resp.setBuildingName(building.getBuildingName());
                }
            }
        }
    }
}
