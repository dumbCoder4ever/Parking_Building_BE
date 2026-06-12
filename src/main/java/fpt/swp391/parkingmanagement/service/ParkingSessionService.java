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
import fpt.swp391.parkingmanagement.dto.PricingTierResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingStaffRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.service.PricingService;
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
    private final BuildingStaffRepository buildingStaffRepository;
    private final PricingService pricingService;

    private void checkStaffBuildingAssignment(String staffEmail, String buildingId) {
        String userId = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"))
                .getUserId();
        if (!buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(buildingId, userId)) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED,
                    "You are not assigned to this building");
        }
    }

    private String resolveBuildingId(ParkingSlot slot) {
        if (slot == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND);
        Zone zone = slot.getZone();
        if (zone == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND, "Slot has no zone");
        Floor floor = zone.getFloor();
        if (floor == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND, "Zone has no floor");
        Building building = floor.getBuilding();
        if (building == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND, "Floor has no building");
        return building.getBuildingId();
    }

    @Transactional
    public ParkingSessionResponse checkin(String staffEmail, CheckinRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (Boolean.TRUE.equals(ticket.getIsUsed())) throw new BaseAPIException(ErrorCode.TICKET_ALREADY_USED);

        // Check if ticket has expired
        if (ticket.getExpiredAt() != null && LocalDateTime.now().isAfter(ticket.getExpiredAt())) {
            throw new BaseAPIException(ErrorCode.TICKET_EXPIRED);
        }

        var reservation = ticket.getReservation();
        if (reservation == null) throw new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND);
        if (!"APPROVED".equalsIgnoreCase(reservation.getReservationStatus())) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_APPROVED);
        }

        if (req.getPlateNumber() != null && reservation.getVehicle() != null) {
            if (!req.getPlateNumber().equalsIgnoreCase(reservation.getVehicle().getPlateNumber())) {
                throw new BaseAPIException(ErrorCode.PLATE_NUMBER_MISMATCH);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.getReservationEnd() != null) {
            Integer gracePeriodMinutes = reservation.getGracePeriodMinutes();
            int grace = gracePeriodMinutes != null ? gracePeriodMinutes : 15;
            if (now.isAfter(reservation.getReservationEnd().plusMinutes(grace))) {
                throw new BaseAPIException(ErrorCode.RESERVATION_EXPIRED);
            }
        }

        Vehicle vehicle = reservation.getVehicle();
        ParkingSlot slot = reservation.getSlot();
        if (slot == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND);
        if (!"RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_RESERVED);
        }

        String buildingId = resolveBuildingId(slot);
        checkStaffBuildingAssignment(staffEmail, buildingId);

        PricingPolicy policy = null;
        if (vehicle != null && vehicle.getVehicleType() != null) {
            String vtId = vehicle.getVehicleType().getVehicleTypeId();
            policy = pricingPolicyRepository.findActiveForVehicleType(vtId).orElse(null);
        }

        BigDecimal estimatedFee = BigDecimal.ZERO;
        if (policy != null) {
            BigDecimal base = policy.getBasePrice() != null ? policy.getBasePrice() : BigDecimal.ZERO;
            BigDecimal hourly = policy.getHourlyRate() != null ? policy.getHourlyRate() : BigDecimal.ZERO;
            BigDecimal multiplier = policy.getPeakHourMultiplier() != null ? policy.getPeakHourMultiplier() : BigDecimal.ONE;
            int currentHour = now.getHour();
            boolean isPeak = (currentHour >= 7 && currentHour < 9) || (currentHour >= 17 && currentHour < 19);
            BigDecimal hourlyTotal = hourly.multiply(multiplier);
            estimatedFee = base.add(hourlyTotal);
            if (policy.getMaxDailyFee() != null && policy.getMaxDailyFee().compareTo(BigDecimal.ZERO) > 0
                    && estimatedFee.compareTo(policy.getMaxDailyFee()) > 0) {
                estimatedFee = policy.getMaxDailyFee();
            }
        }

        ParkingSession session = new ParkingSession();
        session.setTicket(ticket);
        session.setReservation(reservation);
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setSessionStatus("ACTIVE");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
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
        resp.setVehiclePlate(vehicle != null ? vehicle.getPlateNumber() : null);
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setEstimatedFee(estimatedFee);
        if (policy != null) {
            resp.setBasePrice(policy.getBasePrice());
            resp.setHourlyRate(policy.getHourlyRate());
            resp.setPeakHourMultiplier(policy.getPeakHourMultiplier());
            resp.setMaxDailyFee(policy.getMaxDailyFee());
            resp.setOvernightFee(policy.getOvernightFee());
            if (vehicle != null && vehicle.getVehicleType() != null) {
                resp.setVehicleTypeId(vehicle.getVehicleType().getVehicleTypeId());
                resp.setVehicleTypeName(vehicle.getVehicleType().getTypeName());
            }
        }

        return resp;
    }

    @Transactional
    public CheckoutResponse checkout(String staffEmail, CheckoutRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        Optional<ParkingSession> optSession = parkingSessionRepository
                .findByTicketTicketIdAndSessionStatus(ticket.getTicketId(), "ACTIVE");

        ParkingSession session = optSession.orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        LocalDateTime now = LocalDateTime.now();
        session.setCheckoutTime(now);

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = Duration.between(session.getCheckinTime(), now).toMinutes();
        int hours = (int) Math.ceil(minutes / 60.0);

        BigDecimal total = BigDecimal.ZERO;
        PricingPolicy policy = null;
        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);

            if (policy == null) {
                log.warn("No active pricing policy for vehicleTypeId={}", vtId);
            } else {
                total = pricingService.calculateFeeByPolicy(policy, hours);
            }
        }

        String paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH";
        boolean electronicPayment = "VNPAY".equals(paymentMethod)
                || "PAYOS".equals(paymentMethod)
                || "MOMO".equals(paymentMethod);

        session.setTotalFee(total);
        session.setParkingDuration(hours);
        session.setSessionStatus("COMPLETED");
        if (session.getReservation() != null) {
            session.getReservation().setReservationStatus("COMPLETED");
        }

        Payment savedPayment;
        if (electronicPayment) {
            if (!"PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new RuntimeException(
                        "Payment has not been confirmed yet. Initiate and complete payment before checkout.");
            }
            savedPayment = paymentRepository
                    .findFirstBySessionSessionIdAndPaymentStatusOrderByCreatedAtDesc(
                            session.getSessionId(), "SUCCESS")
                    .orElseThrow(() -> new RuntimeException(
                            "Successful payment record not found for this session"));
        } else {
            session.setPaymentStatus("PAID");
            savedPayment = null;
        }

        ParkingSession saved = parkingSessionRepository.save(session);

        if (!electronicPayment) {
            Payment payment = new Payment();
            payment.setSession(saved);
            payment.setPaymentMethod(paymentMethod);
            payment.setAmount(total);
            payment.setPaymentStatus("SUCCESS");
            savedPayment = paymentRepository.save(payment);
        }

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
        resp.setParkingHours(hours);
        resp.setParkingMinutes((int) minutes);
        resp.setOvernightCharge(BigDecimal.ZERO);
        if (policy != null) {
            resp.setBasePrice(policy.getBasePrice());
            resp.setHourlyRate(policy.getHourlyRate());
            resp.setPeakHourMultiplier(policy.getPeakHourMultiplier());
            resp.setMaxDailyFee(policy.getMaxDailyFee());
            resp.setOvernightFee(policy.getOvernightFee());
            resp.setLostTicketFee(policy.getLostTicketFee());
            resp.setPricingTiers(pricingService.toTierList(policy));
            resp.setFeeExplanation(buildFeeExplanation(policy, hours));
            if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
                resp.setVehicleTypeId(session.getVehicle().getVehicleType().getVehicleTypeId());
                resp.setVehicleTypeName(session.getVehicle().getVehicleType().getTypeName());
            }
        }
        if (savedPayment != null) {
            resp.setPaymentId(savedPayment.getPaymentId());
        }

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

    private String buildFeeExplanation(PricingPolicy policy, int hours) {
        if (policy == null || hours <= 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(hours).append("h parking: ");
        List<PricingTierResponse> tiers = pricingService.toTierList(policy);
        for (int i = 0; i < tiers.size(); i++) {
            PricingTierResponse tier = tiers.get(i);
            if (hours <= tier.getMaxHours() || i == tiers.size() - 1) {
                sb.append(tier.getTierLabel()).append(" = ").append(tier.getPrice()).append(" VND");
                break;
            }
        }
        return sb.toString();
    }
}
