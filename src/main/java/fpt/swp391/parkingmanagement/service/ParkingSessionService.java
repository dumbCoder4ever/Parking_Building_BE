package fpt.swp391.parkingmanagement.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckinRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutRequest;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.BuildingStaffRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.PaymentRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.VehicleRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
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
    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final PricingService pricingService;
    private final VehicleTypeRepository vehicleTypeRepository;

    private void checkStaffBuildingAssignment(String staffEmail, String buildingId) {
        String userId = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"))
                .getUserId();
        if (!buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(buildingId, userId)) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED, "You are not assigned to this building");
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

        if (Boolean.TRUE.equals(ticket.getIsUsed())) {
            throw new BaseAPIException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (ticket.getExpiredAt() != null && LocalDateTime.now().isAfter(ticket.getExpiredAt())) {
            throw new BaseAPIException(ErrorCode.TICKET_EXPIRED);
        }

        var reservation = ticket.getReservation();
        if (reservation == null) throw new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND);

        String resStatus = reservation.getReservationStatus();
        if (!"PENDING".equalsIgnoreCase(resStatus) && !"APPROVED".equalsIgnoreCase(resStatus)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_APPROVED);
        }

        if (req.getPlateNumber() != null && reservation.getVehicle() != null) {
            if (!req.getPlateNumber().equalsIgnoreCase(reservation.getVehicle().getPlateNumber())) {
                throw new BaseAPIException(ErrorCode.PLATE_NUMBER_MISMATCH);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        Vehicle vehicle = reservation.getVehicle();
        ParkingSlot slot = reservation.getSlot();
        if (slot == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND);
        if (!"RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_RESERVED);
        }

        String buildingId = resolveBuildingId(slot);
        checkStaffBuildingAssignment(staffEmail, buildingId);

        PricingPolicy policy = null;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;
        BigDecimal estimatedFee = BigDecimal.ZERO;

        if (vehicle != null && vehicle.getVehicleType() != null) {
            String vtId = vehicle.getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
                estimatedFee = pricingService.calculateByPolicy(policy, 1);
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
        session.setCheckinImageUrl(req.getCheckinImageUrl());
        if (req.getCheckinImageUrl() != null && vehicle != null) {
            vehicle.setImageUrl(req.getCheckinImageUrl());
            vehicleRepository.save(vehicle);
        }
        User staff = userRepository.findByEmail(staffEmail).orElse(null);
        session.setCreatedBy(staff);

        // Update reservation status to CHECKED_IN to prevent auto-expiration job
        reservation.setReservationStatus("CHECKED_IN");
        reservationRepository.save(reservation);

        ParkingSession saved = parkingSessionRepository.save(session);

        ticket.setIsUsed(true);
        ticket.setStatus("USED");
        ticketRepository.save(ticket);

        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        ParkingSessionResponse resp = new ParkingSessionResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setTicketCode(ticket.getTicketCode());
        resp.setVehiclePlate(vehicle != null ? vehicle.getPlateNumber() : null);
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckinImageUrl(saved.getCheckinImageUrl());
        resp.setEstimatedFee(estimatedFee);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        if (policy != null && vehicle != null && vehicle.getVehicleType() != null) {
            resp.setVehicleTypeId(vehicle.getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(vehicle.getVehicleType().getTypeName());
        }
        applyHierarchy(resp, slot);

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
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;

        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                total = pricingService.calculateByPolicy(policy, hours);
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        String paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH";
        boolean electronicPayment = "VNPAY".equals(paymentMethod) || "PAYOS".equals(paymentMethod) || "MOMO".equals(paymentMethod);

        session.setTotalFee(total);
        session.setParkingDuration(hours);
        session.setSessionStatus("COMPLETED");
        session.setCheckoutImageUrl(req.getCheckoutImageUrl());
        if (req.getCheckoutImageUrl() != null && session.getVehicle() != null) {
            Vehicle checkoutVehicle = session.getVehicle();
            checkoutVehicle.setImageUrl(req.getCheckoutImageUrl());
            vehicleRepository.save(checkoutVehicle);
        }

        // Update reservation to COMPLETED after successful checkout
        if (session.getReservation() != null) {
            session.getReservation().setReservationStatus("COMPLETED");
        }

        Payment savedPayment = null;
        if (electronicPayment) {
            if (!"PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new BaseAPIException(ErrorCode.PAYMENT_NOT_COMPLETED,
                        "Payment has not been completed yet. Initiate and complete payment before checkout.");
            }
            savedPayment = findLatestSessionPayment(session.getSessionId(), List.of("PAID", "CONFIRMED"))
                    .orElseThrow(() -> new BaseAPIException(ErrorCode.PAYMENT_NOT_FOUND,
                            "Successful payment record not found for this session"));
            session.setPaymentStatus("PAID");
        } else {
            session.setPaymentStatus("PAID");
            Payment payment = new Payment();
            payment.setSession(session);
            payment.setPaymentMethod(paymentMethod);
            payment.setAmount(total);
            payment.setPaymentStatus("PAID");
            savedPayment = paymentRepository.save(payment);
        }

        ParkingSession saved = parkingSessionRepository.save(session);

        ParkingSlot slot = saved.getSlot();
        if (slot != null) {
            slot.setSlotStatus("AVAILABLE");
            parkingSlotRepository.save(slot);
        }

        CheckoutResponse resp = new CheckoutResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingHours(hours);
        resp.setParkingMinutes((int) minutes);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setSessionStatus(saved.getSessionStatus());
        resp.setPaymentStatus(saved.getPaymentStatus());
        resp.setCheckoutImageUrl(saved.getCheckoutImageUrl());
        if (savedPayment != null) {
            resp.setPaymentId(savedPayment.getPaymentId());
        }
        if (policy != null && session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            resp.setVehicleTypeId(session.getVehicle().getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(session.getVehicle().getVehicleType().getTypeName());
        }
        if (slot != null) {
            applyHierarchy(resp, slot);
        }

        return resp;
    }

    public EstimateResponse estimateFee(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        Optional<ParkingSession> optSession = parkingSessionRepository
                .findByTicketTicketIdAndSessionStatus(ticket.getTicketId(), "ACTIVE");

        ParkingSession session = optSession.orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(session.getCheckinTime(), now).toMinutes();
        int hours = (int) Math.ceil(minutes / 60.0);

        PricingPolicy policy = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;

        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                total = pricingService.calculateByPolicy(policy, hours);
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        EstimateResponse resp = EstimateResponse.builder()
                .sessionId(session.getSessionId())
                .ticketCode(ticket.getTicketCode())
                .checkinTime(session.getCheckinTime())
                .estimatedCheckoutTime(now)
                .parkingHours(hours)
                .parkingMinutes((int) (minutes % 60))
                .totalFee(total)
                .basePrice(basePrice)
                .hourlyRate(hourlyRate)
                .build();

        if (session.getVehicle() != null) {
            resp.setVehiclePlate(session.getVehicle().getPlateNumber());
            if (session.getVehicle().getVehicleType() != null) {
                resp.setVehicleTypeId(session.getVehicle().getVehicleType().getVehicleTypeId());
                resp.setVehicleTypeName(session.getVehicle().getVehicleType().getTypeName());
            }
        }

        applyHierarchyForEstimate(resp, session.getSlot());
        return resp;
    }

    @Transactional
    public CheckoutResponse confirmExitAndCheckout(String staffEmail, String sessionId, String paymentMethod) {
        ParkingSession session = parkingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        if (!"ACTIVE".equalsIgnoreCase(session.getSessionStatus())) {
            throw new BaseAPIException(ErrorCode.SESSION_NOT_FOUND, "Session is not active");
        }

        LocalDateTime now = LocalDateTime.now();
        session.setCheckoutTime(now);

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = Duration.between(session.getCheckinTime(), now).toMinutes();
        int hours = (int) Math.ceil(minutes / 60.0);

        PricingPolicy policy = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;

        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                total = pricingService.calculateByPolicy(policy, hours);
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        String method = paymentMethod != null ? paymentMethod : "CASH";
        boolean electronicPayment = "VNPAY".equals(method) || "PAYOS".equals(method) || "MOMO".equals(method);

        session.setTotalFee(total);
        session.setParkingDuration(hours);
        session.setSessionStatus("ACTIVE");

        if (electronicPayment) {
            if (!"PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new BaseAPIException(ErrorCode.PAYMENT_NOT_COMPLETED,
                        "Payment has not been completed yet. Driver must complete payment before exit.");
            }
            findLatestSessionPayment(session.getSessionId(), List.of("PAID", "CONFIRMED"))
                    .orElseThrow(() -> new BaseAPIException(ErrorCode.PAYMENT_NOT_FOUND,
                            "Successful payment record not found for this session"));
            session.setPaymentStatus("PAID");
        } else {
            session.setPaymentStatus("PAID");
            Payment payment = new Payment();
            payment.setSession(session);
            payment.setPaymentMethod(method);
            payment.setAmount(total);
            payment.setPaymentStatus("PAID");
            payment.setPaymentTime(now);
            paymentRepository.save(payment);
        }

        // Update reservation to COMPLETED after successful exit
        if (session.getReservation() != null) {
            session.getReservation().setReservationStatus("COMPLETED");
        }

        session.setSessionStatus("COMPLETED");
        ParkingSession saved = parkingSessionRepository.save(session);

        ParkingSlot slot = saved.getSlot();
        if (slot != null) {
            slot.setSlotStatus("PENDING_EXIT");
            parkingSlotRepository.save(slot);
        }

        CheckoutResponse resp = new CheckoutResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingHours(hours);
        resp.setParkingMinutes((int) minutes);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setSessionStatus(saved.getSessionStatus());
        resp.setPaymentStatus(saved.getPaymentStatus());
        if (policy != null && session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            resp.setVehicleTypeId(session.getVehicle().getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(session.getVehicle().getVehicleType().getTypeName());
        }
        if (slot != null) {
            applyHierarchy(resp, slot);
        }

        return resp;
    }

    private void applyHierarchy(ParkingSessionResponse resp, ParkingSlot slot) {
        if (slot == null) return;
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
        if (slot == null) return;
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

    private void applyHierarchyForEstimate(EstimateResponse resp, ParkingSlot slot) {
        if (slot == null) return;
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

    // ======================== GUEST FLOW ========================

    @Transactional
    public GuestCheckinResponse guestCheckin(String staffEmail, GuestCheckinRequest req) {
        ParkingSlot slot = parkingSlotRepository.findBySlotId(req.getSlotId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_FOUND));

        if (!"AVAILABLE".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE);
        }

        String buildingId = resolveBuildingId(slot);
        checkStaffBuildingAssignment(staffEmail, buildingId);

        VehicleType vehicleType = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND));

        // Reuse existing guest vehicle by plate number, or create a new one
        Vehicle vehicle = vehicleRepository.findByPlateNumberIgnoreCase(req.getPlateNumber())
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(req.getPlateNumber().toUpperCase());
                    v.setVehicleType(vehicleType);
                    v.setVehicleColor(req.getVehicleColor());
                    v.setBrand(req.getBrand());
                    v.setModel(req.getModel());
                    v.setStatus("ACTIVE");
                    return vehicleRepository.save(v);
                });

        // Update vehicle info if provided
        if (req.getVehicleColor() != null) vehicle.setVehicleColor(req.getVehicleColor());
        if (req.getBrand() != null) vehicle.setBrand(req.getBrand());
        if (req.getModel() != null) vehicle.setModel(req.getModel());
        if (req.getCheckinImageUrl() != null) vehicle.setImageUrl(req.getCheckinImageUrl());
        vehicleRepository.save(vehicle);

        LocalDateTime now = LocalDateTime.now();

        PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
        BigDecimal basePrice = policy != null ? policy.getBasePrice() : null;
        BigDecimal hourlyRate = policy != null ? policy.getHourlyRate() : null;
        BigDecimal estimatedFee = policy != null ? pricingService.calculateByPolicy(policy, 1) : BigDecimal.ZERO;

        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setSessionStatus("ACTIVE");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setGuestName(req.getGuestName());
        session.setGuestPhone(req.getGuestPhone());
        session.setNote(req.getNote());
        session.setCheckinImageUrl(req.getCheckinImageUrl());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // Create a guest ticket so staff can use ticket code for checkout/payment
        Ticket guestTicket = new Ticket();
        guestTicket.setTicketCode(generateGuestTicketCode());
        guestTicket.setIsUsed(false);
        guestTicket.setIsLost(false);
        guestTicket.setStatus("ACTIVE");
        guestTicket.setIssuedAt(now);
        Ticket savedTicket = ticketRepository.save(guestTicket);

        saved.setTicket(savedTicket);
        saved = parkingSessionRepository.save(saved);

        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        GuestCheckinResponse resp = new GuestCheckinResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setTicketCode(savedTicket.getTicketCode());
        resp.setGuestName(saved.getGuestName());
        resp.setGuestPhone(saved.getGuestPhone());
        resp.setVehiclePlate(vehicle.getPlateNumber());
        resp.setVehicleColor(vehicle.getVehicleColor());
        resp.setBrand(vehicle.getBrand());
        resp.setModel(vehicle.getModel());
        resp.setVehicleTypeId(vehicleType.getVehicleTypeId());
        resp.setVehicleTypeName(vehicleType.getTypeName());
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckinImageUrl(saved.getCheckinImageUrl());
        resp.setEstimatedFee(estimatedFee);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        applyHierarchyGuest(resp, slot);

        return resp;
    }

    @Transactional
    public CheckoutResponse guestCheckout(String staffEmail, GuestCheckoutRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        ParkingSession session = parkingSessionRepository
                .findByTicketTicketIdAndSessionStatus(ticket.getTicketId(), "ACTIVE")
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No active guest session found for ticket: " + req.getTicketCode()));

        if (session.getReservation() != null) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "This is not a guest session. Use regular checkout.");
        }

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        LocalDateTime now = LocalDateTime.now();

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = Duration.between(session.getCheckinTime(), now).toMinutes();
        int hours = (int) Math.ceil(minutes / 60.0);

        PricingPolicy policy = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;

        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                total = pricingService.calculateByPolicy(policy, hours);
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        String paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH";
        boolean electronicPayment = "VNPAY".equals(paymentMethod) || "PAYOS".equals(paymentMethod) || "MOMO".equals(paymentMethod);

        session.setCheckoutTime(now);
        session.setTotalFee(total);
        session.setParkingDuration(hours);
        session.setSessionStatus("COMPLETED");
        session.setCheckoutImageUrl(req.getCheckoutImageUrl());
        if (req.getCheckoutImageUrl() != null && session.getVehicle() != null) {
            session.getVehicle().setImageUrl(req.getCheckoutImageUrl());
            vehicleRepository.save(session.getVehicle());
        }

        Payment savedPayment = null;
        if (electronicPayment) {
            if (!"PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new BaseAPIException(ErrorCode.PAYMENT_NOT_COMPLETED,
                        "Payment has not been completed yet.");
            }
            savedPayment = findLatestSessionPayment(session.getSessionId(), List.of("PAID", "CONFIRMED"))
                    .orElseThrow(() -> new BaseAPIException(ErrorCode.PAYMENT_NOT_FOUND,
                            "Successful payment record not found for this session"));
            session.setPaymentStatus("PAID");
        } else {
            session.setPaymentStatus("PAID");
            Payment payment = new Payment();
            payment.setSession(session);
            payment.setPaymentMethod(paymentMethod);
            payment.setAmount(total);
            payment.setPaymentStatus("PAID");
            savedPayment = paymentRepository.save(payment);
        }

        ParkingSession saved = parkingSessionRepository.save(session);

        ParkingSlot slot = saved.getSlot();
        if (slot != null) {
            slot.setSlotStatus("AVAILABLE");
            parkingSlotRepository.save(slot);
        }

        CheckoutResponse resp = new CheckoutResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingHours(hours);
        resp.setParkingMinutes((int) minutes);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setSessionStatus(saved.getSessionStatus());
        resp.setPaymentStatus(saved.getPaymentStatus());
        resp.setCheckoutImageUrl(saved.getCheckoutImageUrl());
        if (savedPayment != null) resp.setPaymentId(savedPayment.getPaymentId());
        if (policy != null && session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            resp.setVehicleTypeId(session.getVehicle().getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(session.getVehicle().getVehicleType().getTypeName());
        }
        if (slot != null) applyHierarchy(resp, slot);

        return resp;
    }

    private void applyHierarchyGuest(GuestCheckinResponse resp, ParkingSlot slot) {
        if (slot == null) return;
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

    public GuestCheckinResponse getGuestSessionById(String sessionId) {
        ParkingSession ps = parkingSessionRepository.findGuestSessionById(sessionId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND));
        return mapToGuestCheckinResponse(ps);
    }

    public GuestCheckinResponse findActiveGuestByPlate(String plateNumber) {
        ParkingSession ps = parkingSessionRepository.findActiveGuestByPlateNumber(plateNumber)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No active guest session found for plate: " + plateNumber));
        return mapToGuestCheckinResponse(ps);
    }

    private GuestCheckinResponse mapToGuestCheckinResponse(ParkingSession ps) {
        Vehicle vehicle = ps.getVehicle();
        VehicleType vehicleType = vehicle != null ? vehicle.getVehicleType() : null;

        GuestCheckinResponse resp = new GuestCheckinResponse();
        resp.setSessionId(ps.getSessionId());
        if (ps.getTicket() != null) {
            resp.setTicketCode(ps.getTicket().getTicketCode());
        }
        resp.setGuestName(ps.getGuestName());
        resp.setGuestPhone(ps.getGuestPhone());
        resp.setCheckinTime(ps.getCheckinTime());
        resp.setCheckinImageUrl(ps.getCheckinImageUrl());
        resp.setEstimatedFee(ps.getEstimatedFee());

        if (vehicle != null) {
            resp.setVehiclePlate(vehicle.getPlateNumber());
            resp.setVehicleColor(vehicle.getVehicleColor());
            resp.setBrand(vehicle.getBrand());
            resp.setModel(vehicle.getModel());
        }
        if (vehicleType != null) {
            resp.setVehicleTypeId(vehicleType.getVehicleTypeId());
            resp.setVehicleTypeName(vehicleType.getTypeName());
        }

        applyHierarchyGuest(resp, ps.getSlot());
        return resp;
    }

    public GuestCheckinResponse getGuestSessionByTicketCode(String ticketCode) {
        ParkingSession ps = parkingSessionRepository.findGuestSessionByTicketCode(ticketCode)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No guest session found for ticket: " + ticketCode));
        GuestCheckinResponse resp = mapToGuestCheckinResponse(ps);
        if (ps.getTicket() != null) {
            resp.setTicketCode(ps.getTicket().getTicketCode());
        }
        return resp;
    }

    private String generateGuestTicketCode() {
        return "G-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 9000 + 1000);
    }

    // ======================== END GUEST FLOW ========================

    private Optional<Payment> findLatestSessionPayment(String sessionId, List<String> statuses) {
        List<String> normalizedStatuses = statuses.stream()
                .map(String::toUpperCase)
                .toList();
        return paymentRepository
                .findBySessionSessionIdAndPaymentStatusInOrderByCreatedAtDesc(
                        sessionId, normalizedStatuses, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

}
