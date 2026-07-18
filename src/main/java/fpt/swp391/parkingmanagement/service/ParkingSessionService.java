package fpt.swp391.parkingmanagement.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import fpt.swp391.parkingmanagement.dto.CheckinRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckinOcrRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutOcrRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutRequest;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
import fpt.swp391.parkingmanagement.dto.PlateDuplicateInfo;
import fpt.swp391.parkingmanagement.dto.PlateLookupResponse;
import fpt.swp391.parkingmanagement.dto.QuickCheckinRequest;
import fpt.swp391.parkingmanagement.dto.QuickCheckinResponse;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Payment;
import fpt.swp391.parkingmanagement.entity.PricingPolicy;
import fpt.swp391.parkingmanagement.entity.Reservation;
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
import fpt.swp391.parkingmanagement.service.PlateRecognizerService;
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
    private final PlateRecognizerService ocrService;

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
        // [DEBUG] Log entry for checkin
        System.out.println("[DEBUG-6b654b] checkin called - staffEmail: " + staffEmail + ", ticketCode: " + req.getTicketCode());
        
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        System.out.println("[DEBUG-6b654b] Ticket found: " + ticket.getTicketCode() + ", isUsed: " + ticket.getIsUsed());

        if (Boolean.TRUE.equals(ticket.getIsUsed())) {
            throw new BaseAPIException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (ticket.getExpiredAt() != null && LocalDateTime.now().isAfter(ticket.getExpiredAt())) {
            throw new BaseAPIException(ErrorCode.TICKET_EXPIRED);
        }

        var reservation = ticket.getReservation();
        if (reservation == null) throw new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND);
        System.out.println("[DEBUG-6b654b] Reservation: " + reservation.getReservationId() + ", status: " + reservation.getReservationStatus());

        String resStatus = reservation.getReservationStatus();
        if (!"PENDING".equalsIgnoreCase(resStatus)) {
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
        System.out.println("[DEBUG-6b654b] Resolved buildingId from slot: " + buildingId);
        checkStaffBuildingAssignment(staffEmail, buildingId);
        System.out.println("[DEBUG-6b654b] Staff assignment check PASSED");

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
        session.setParkingDuration(0);
        session.setSessionStatus("PENDING_PAYMENT");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setCheckinImageUrl(req.getCheckinImageUrl());
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
        resp.setParkingDuration(0);
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
                .findByTicketTicketIdAndSessionStatusIn(ticket.getTicketId(),
                        java.util.List.of("ACTIVE", "PENDING_PAYMENT"));

        ParkingSession session = optSession.orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        LocalDateTime now = LocalDateTime.now();
        session.setCheckoutTime(now);

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = calculateParkingMinutes(session, now);
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
        session.setParkingDuration((int) minutes);
        session.setSessionStatus("COMPLETED");
        session.setCheckoutImageUrl(req.getCheckoutImageUrl());

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
            payment.setPaymentTime(now);
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
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingDuration((int) minutes);
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
                .findByTicketTicketIdAndSessionStatusIn(ticket.getTicketId(),
                        java.util.List.of("ACTIVE", "PENDING_PAYMENT"));

        ParkingSession session = optSession.orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        long minutes = calculateParkingMinutes(session, now);
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

    /**
     * Tìm {@code sessionId} của phiên ACTIVE ứng với {@code ticketCode}.
     * Trả về empty nếu không tìm thấy ticket hoặc session không ở trạng thái ACTIVE.
     */
    @Transactional(readOnly = true)
    public Optional<String> findSessionIdByTicketCode(String ticketCode) {
        if (ticketCode == null || ticketCode.isBlank()) {
            return Optional.empty();
        }
        Optional<ParkingSession> sessionOpt = parkingSessionRepository.findByTicketCode(ticketCode);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        ParkingSession session = sessionOpt.get();
        String currentStatus = session.getSessionStatus();
        if (!"ACTIVE".equalsIgnoreCase(currentStatus) && !"PENDING_PAYMENT".equalsIgnoreCase(currentStatus)) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.getSessionId());
    }

    @Transactional
    public CheckoutResponse confirmExitAndCheckout(String staffEmail, String sessionId, String paymentMethod, String checkoutImageUrl) {
        // [DEBUG] Log entry for confirmExitAndCheckout
        System.out.println("[DEBUG-6b654b] confirmExitAndCheckout called - sessionId: " + sessionId + ", checkoutImageUrl: " + (checkoutImageUrl != null ? "present" : "null"));

        ParkingSession session = parkingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        String currentStatus = session.getSessionStatus();
        if (!"ACTIVE".equalsIgnoreCase(currentStatus) && !"PENDING_PAYMENT".equalsIgnoreCase(currentStatus)) {
            throw new BaseAPIException(ErrorCode.SESSION_NOT_FOUND, "Session is not active");
        }

        LocalDateTime now = LocalDateTime.now();
        session.setCheckoutTime(now);

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = calculateParkingMinutes(session, now);
        int hours = (int) Math.ceil(minutes / 60.0);

        // [DEBUG] Log session vehicle plate for plate mismatch detection
        String sessionPlate = session.getVehicle() != null ? session.getVehicle().getPlateNumber() : "null";
        System.out.println("[DEBUG-6b654b] Session vehicle plate: " + sessionPlate + " - NO OCR VALIDATION PERFORMED!");

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
        session.setParkingDuration((int) minutes);
        session.setCheckoutImageUrl(checkoutImageUrl);
        if (checkoutImageUrl != null && session.getVehicle() != null) {
            Vehicle checkoutVehicle = session.getVehicle();
            checkoutVehicle.setImageUrl(checkoutImageUrl);
            vehicleRepository.save(checkoutVehicle);
        }

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
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingDuration((int) minutes);
        resp.setParkingHours(hours);
        resp.setParkingMinutes((int) minutes);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setSessionStatus(saved.getSessionStatus());
        resp.setPaymentStatus(saved.getPaymentStatus());
        resp.setCheckoutImageUrl(saved.getCheckoutImageUrl());
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
        String plateNumber = req.getPlateNumber().toUpperCase();

        validateNoActiveSessionForPlate(plateNumber);

        ParkingSlot slot = parkingSlotRepository.findBySlotId(req.getSlotId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_FOUND));

        if (!"AVAILABLE".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE);
        }

        String buildingId = resolveBuildingId(slot);
        checkStaffBuildingAssignment(staffEmail, buildingId);

        VehicleType vehicleType = resolveVehicleTypeFromSlot(slot);

        Vehicle vehicle = vehicleRepository.findByPlateNumberIgnoreCase(plateNumber)
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(plateNumber);
                    v.setVehicleType(vehicleType);
                    v.setStatus("ACTIVE");
                    return vehicleRepository.save(v);
                });

        if (vehicle.getVehicleType() == null) {
            vehicle.setVehicleType(vehicleType);
            vehicleRepository.save(vehicle);
        }

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
        session.setParkingDuration(0);
        session.setSessionStatus("PENDING_PAYMENT");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setNote(req.getNote());
        session.setCheckinImageUrl(req.getCheckinImageUrl());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

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
        resp.setVehiclePlate(vehicle.getPlateNumber());
        resp.setVehicleTypeId(vehicleType.getVehicleTypeId());
        resp.setVehicleTypeName(vehicleType.getTypeName());
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckinImageUrl(saved.getCheckinImageUrl());
        resp.setStatus(saved.getSessionStatus());
        resp.setParkingDuration(0);
        resp.setEstimatedFee(estimatedFee);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        applyHierarchyGuest(resp, slot);

        return resp;
    }

    private VehicleType resolveVehicleTypeFromSlot(ParkingSlot slot) {
        Zone zone = slot.getZone();
        if (zone == null || zone.getFloor() == null || zone.getFloor().getVehicleType() == null) {
            throw new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                    "Cannot resolve vehicle type from the selected slot");
        }
        return zone.getFloor().getVehicleType();
    }

    @Transactional
    public CheckoutResponse guestCheckout(String staffEmail, GuestCheckoutRequest req) {
        String plateNumber = req.getPlateNumber().toUpperCase();

        ParkingSession session = parkingSessionRepository.findActiveGuestByPlateNumber(plateNumber)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No active guest session found for plate: " + plateNumber));

        if (session.getReservation() != null) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "This is not a guest session. Use regular checkout.");
        }

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        LocalDateTime now = LocalDateTime.now();

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = calculateParkingMinutes(session, now);
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
        session.setParkingDuration((int) minutes);
        session.setSessionStatus("COMPLETED");
        session.setCheckoutImageUrl(req.getCheckoutImageUrl());

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
            payment.setPaymentTime(now);
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
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingDuration((int) minutes);
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

    // ======================== GUEST OCR CHECKIN ========================
    // Staff quét ảnh biển số → OCR nhận diện → auto-assign slot → tạo session.

    @Transactional
    public GuestCheckinResponse guestCheckinOcr(String staffEmail, GuestCheckinOcrRequest req) {
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        VehicleType vehicleType = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "Không tìm thấy loại xe: " + req.getVehicleTypeId()));

        // 1. OCR biển số
        PlateRecognizerService.OcrResult ocr;
        try {
            ocr = ocrService.recognizeFromUpload(req.getPlateImage());
        } catch (Exception e) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Không thể đọc ảnh biển số: " + e.getMessage());
        }
        String plateNumber = ocr.plateNumber();
        if (plateNumber == null || ocr.confidence() < 0.3) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Không nhận diện được biển số từ ảnh. Vui lòng chụp lại hoặc nhập tay.");
        }
        final String finalPlateNumber = plateNumber.toUpperCase();

        // 2. Kiểm tra biển số đã có session ACTIVE chưa (không phân biệt driver/guest)
        Optional<ParkingSession> existingSession = parkingSessionRepository.findActiveGuestByPlateNumber(finalPlateNumber);
        if (existingSession.isPresent()) {
            ParkingSession dup = existingSession.get();
            String dupTicket = dup.getTicket() != null ? dup.getTicket().getTicketCode() : "N/A";
            throw new BaseAPIException(ErrorCode.PLATE_ALREADY_PARKED,
                    "Biển số " + finalPlateNumber + " đã đang đỗ trong bãi. "
                            + "Ticket: " + dupTicket + ". Vui lòng checkout trước.");
        }

        // 3. Tìm slot trống theo building + vehicleType (ưu tiên tầng thấp)
        ParkingSlot slot = parkingSlotRepository
                .findFirstAvailableByBuildingAndVehicleType(req.getBuildingId(), req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE,
                        "Không có slot trống nào cho loại xe " + vehicleType.getTypeName()
                                + " tại building này."));

        // 4. Tìm hoặc tạo Vehicle
        Vehicle vehicle = vehicleRepository.findByPlateNumberIgnoreCase(finalPlateNumber)
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(finalPlateNumber);
                    v.setVehicleType(vehicleType);
                    v.setVehicleColor(req.getVehicleColor());
                    v.setBrand(req.getBrand());
                    v.setModel(req.getModel());
                    v.setStatus("ACTIVE");
                    return vehicleRepository.save(v);
                });

        // 5. Cập nhật thông tin xe nếu có thay đổi
        if (req.getVehicleColor() != null) vehicle.setVehicleColor(req.getVehicleColor());
        if (req.getBrand() != null) vehicle.setBrand(req.getBrand());
        if (req.getModel() != null) vehicle.setModel(req.getModel());
        vehicleRepository.save(vehicle);

        // 6. Tính pricing
        PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
        BigDecimal basePrice = policy != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy != null ? policy.getHourlyRate() : BigDecimal.ZERO;
        BigDecimal estimatedFee = policy != null
                ? pricingService.calculateByPolicy(policy, 1) : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        // 7. Tạo ParkingSession (không có reservation)
        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setParkingDuration(0);
        session.setSessionStatus("ACTIVE");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setGuestName(req.getGuestName());
        session.setGuestPhone(req.getGuestPhone());
        session.setNote(req.getNote());
        session.setCheckinImageUrl(req.getCheckinImageUrl());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // 8. Tạo guest ticket G-xxx
        Ticket guestTicket = new Ticket();
        guestTicket.setTicketCode(generateGuestTicketCode());
        guestTicket.setIsUsed(false);
        guestTicket.setIsLost(false);
        guestTicket.setStatus("ACTIVE");
        guestTicket.setIssuedAt(now);
        Ticket savedTicket = ticketRepository.save(guestTicket);

        saved.setTicket(savedTicket);
        saved = parkingSessionRepository.save(saved);

        // 9. Cập nhật slot
        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        // 10. Build response
        GuestCheckinResponse resp = new GuestCheckinResponse();
        resp.setTicketCode(savedTicket.getTicketCode());
        resp.setSessionId(saved.getSessionId());
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
        resp.setStatus(saved.getSessionStatus());
        resp.setParkingDuration(0);
        resp.setEstimatedFee(estimatedFee);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setOcrConfidence(ocr.confidence());
        applyHierarchyGuest(resp, slot);

        return resp;
    }

    // ======================== GUEST OCR CHECKOUT ========================
    // Staff quét ảnh biển số → OCR nhận diện → validate vs ticketCode → checkout.

    @Transactional
    public CheckoutResponse guestCheckoutOcr(String staffEmail, GuestCheckoutOcrRequest req) {
        PlateRecognizerService.OcrResult ocr = recognizePlate(req.getPlateImage());
        return guestCheckoutOcr(staffEmail, req.getTicketCode(), req.getCheckoutImageUrl(), req.getPaymentMethod(), ocr.plateNumber().toUpperCase());
    }

    @Transactional
    public CheckoutResponse guestCheckoutOcr(String staffEmail, CheckoutRequest checkoutRequest, String scannedPlate) {
        return guestCheckoutOcr(staffEmail, checkoutRequest.getTicketCode(), checkoutRequest.getCheckoutImageUrl(), checkoutRequest.getPaymentMethod(), scannedPlate);
    }

    @Transactional
    public CheckoutResponse guestCheckoutOcr(String staffEmail, String ticketCode, String checkoutImageUrl, String paymentMethod, String scannedPlate) {
        // 1. Validate ticket tồn tại
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        // 2. Tìm session ACTIVE theo ticket
        ParkingSession session = parkingSessionRepository
                .findByTicketTicketIdAndSessionStatusIn(ticket.getTicketId(),
                        java.util.List.of("ACTIVE", "PENDING_PAYMENT"))
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "Không tìm thấy session ACTIVE cho ticket: " + ticketCode));

        // 3. Verify đây là guest session (không có reservation)
        if (session.getReservation() != null) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST,
                    "Đây là session của driver có reservation. Không dùng được luồng guest checkout.");
        }

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        // 4. Validate: biển số quét phải khớp với biển số trong session
        Vehicle sessionVehicle = session.getVehicle();
        if (sessionVehicle == null) {
            throw new BaseAPIException(ErrorCode.VEHICLE_NOT_FOUND,
                    "Session không có thông tin xe.");
        }
        String sessionPlate = sessionVehicle.getPlateNumber().toUpperCase();
        if (!scannedPlate.equals(sessionPlate)) {
            throw new BaseAPIException(ErrorCode.PLATE_MISMATCH,
                    "Biển số quét (" + scannedPlate + ") không khớp với biển số đăng ký (" + sessionPlate
                            + "). Kiểm tra lại xe hoặc dùng tìm kiếm thủ công.");
        }

        // 6. Tính phí
        LocalDateTime now = LocalDateTime.now();
        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = calculateParkingMinutes(session, now);
        int hours = (int) Math.ceil(minutes / 60.0);

        PricingPolicy policy = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;
        if (sessionVehicle.getVehicleType() != null) {
            policy = pricingService.getActivePolicy(sessionVehicle.getVehicleType().getVehicleTypeId());
            if (policy != null) {
                total = pricingService.calculateByPolicy(policy, hours);
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        String resolvedPaymentMethod = paymentMethod != null ? paymentMethod : "CASH";
        boolean electronicPayment = "VNPAY".equals(resolvedPaymentMethod) || "PAYOS".equals(resolvedPaymentMethod) || "MOMO".equals(resolvedPaymentMethod);

        session.setCheckoutTime(now);
        session.setTotalFee(total);
        session.setParkingDuration((int) minutes);
        session.setSessionStatus("COMPLETED");
        session.setCheckoutImageUrl(checkoutImageUrl);

        Payment savedPayment = null;
        if (electronicPayment) {
            if (!"PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new BaseAPIException(ErrorCode.PAYMENT_NOT_COMPLETED,
                        "Thanh toán điện tử chưa hoàn tất.");
            }
            savedPayment = findLatestSessionPayment(session.getSessionId(), List.of("PAID", "CONFIRMED"))
                    .orElseThrow(() -> new BaseAPIException(ErrorCode.PAYMENT_NOT_FOUND));
            session.setPaymentStatus("PAID");
        } else {
            session.setPaymentStatus("PAID");
            Payment payment = new Payment();
            payment.setSession(session);
            payment.setPaymentMethod(resolvedPaymentMethod);
            payment.setAmount(total);
            payment.setPaymentStatus("PAID");
            payment.setPaymentTime(now);
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
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckoutTime(saved.getCheckoutTime());
        resp.setTotalFee(saved.getTotalFee());
        resp.setParkingDuration((int) minutes);
        resp.setParkingHours(hours);
        resp.setParkingMinutes((int) minutes);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setSessionStatus(saved.getSessionStatus());
        resp.setPaymentStatus(saved.getPaymentStatus());
        resp.setCheckoutImageUrl(saved.getCheckoutImageUrl());
        if (savedPayment != null) resp.setPaymentId(savedPayment.getPaymentId());
        if (policy != null && sessionVehicle.getVehicleType() != null) {
            resp.setVehicleTypeId(sessionVehicle.getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(sessionVehicle.getVehicleType().getTypeName());
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
        Vehicle vehicle = resolveVehicleByPlate(plateNumber)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No active guest session or pending reservation found for plate: " + plateNumber));

        Optional<Reservation> reservation = reservationRepository.findFirstPendingByVehicleId(vehicle.getVehicleId());
        if (reservation.isPresent()) {
            return mapReservationToGuestCheckin(reservation.get());
        }

        return parkingSessionRepository.findActiveGuestByVehicleId(vehicle.getVehicleId())
                .map(this::mapToGuestCheckinResponse)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No active guest session or pending reservation found for plate: " + plateNumber));
    }

    private GuestCheckinResponse mapReservationToGuestCheckin(Reservation reservation) {
        GuestCheckinResponse resp = new GuestCheckinResponse();
        if (reservation.getUser() != null) {
            resp.setGuestName(reservation.getUser().getUsername());
        }
        resp.setStatus(reservation.getReservationStatus());
        resp.setCheckinTime(reservation.getReservationStart());

        Vehicle vehicle = reservation.getVehicle();
        if (vehicle != null) {
            resp.setVehiclePlate(vehicle.getPlateNumber());
            resp.setVehicleColor(vehicle.getVehicleColor());
            resp.setBrand(vehicle.getBrand());
            resp.setModel(vehicle.getModel());
            if (vehicle.getVehicleType() != null) {
                resp.setVehicleTypeId(vehicle.getVehicleType().getVehicleTypeId());
                resp.setVehicleTypeName(vehicle.getVehicleType().getTypeName());
            }
        }

        ParkingSlot slot = reservation.getSlot();
        if (slot != null) {
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
                    if (floor.getVehicleType() != null) {
                        if (resp.getVehicleTypeId() == null) {
                            resp.setVehicleTypeId(floor.getVehicleType().getVehicleTypeId());
                        }
                        if (resp.getVehicleTypeName() == null) {
                            resp.setVehicleTypeName(floor.getVehicleType().getTypeName());
                        }
                    }
                    Building building = floor.getBuilding();
                    if (building != null) {
                        resp.setBuildingId(building.getBuildingId());
                        resp.setBuildingName(building.getBuildingName());
                    }
                }
            }
        }

        ticketRepository.findByReservationReservationId(reservation.getReservationId())
                .ifPresent(ticket -> resp.setTicketCode(ticket.getTicketCode()));
        return resp;
    }

    /**
     * Tra cứu nhanh biển số cho màn staff check-in:
     * 1) reservation PENDING/APPROVED (driver)
     * 2) guest session ACTIVE (walk-in đã check-in)
     */
    @Transactional(readOnly = true)
    public PlateLookupResponse lookupByPlate(String plateNumber, String buildingId) {
        Optional<Vehicle> vehicleOpt = resolveVehicleByPlate(plateNumber);
        if (vehicleOpt.isEmpty()) {
            return PlateLookupResponse.builder().lookupType("NOT_FOUND").build();
        }

        Vehicle vehicle = vehicleOpt.get();
        Optional<Reservation> matchedReservation = reservationRepository.findFirstPendingByVehicleId(vehicle.getVehicleId())
                .filter(r -> matchesBuilding(r, buildingId));
        if (matchedReservation.isPresent()) {
            return PlateLookupResponse.builder()
                    .lookupType("RESERVATION")
                    .reservation(toReservationPreview(matchedReservation.get()))
                    .build();
        }

        return findActiveGuestSessionByVehicle(vehicle.getVehicleId())
                .map(ps -> PlateLookupResponse.builder()
                        .lookupType("GUEST_SESSION")
                        .guestSession(mapToGuestCheckinResponse(ps))
                        .build())
                .orElseGet(() -> PlateLookupResponse.builder().lookupType("NOT_FOUND").build());
    }

    private Optional<ParkingSession> findActiveGuestSessionByPlate(String plateNumber) {
        return resolveVehicleByPlate(plateNumber).flatMap(this::findActiveGuestSessionByVehicle);
    }

    private Optional<ParkingSession> findActiveGuestSessionByVehicle(Vehicle vehicle) {
        return findActiveGuestSessionByVehicle(vehicle.getVehicleId());
    }

    private Optional<ParkingSession> findActiveGuestSessionByVehicle(String vehicleId) {
        return parkingSessionRepository.findActiveGuestByVehicleId(vehicleId);
    }

    private Optional<Vehicle> resolveVehicleByPlate(String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return Optional.empty();
        }
        String trimmed = plateNumber.trim();

        Optional<Vehicle> exact = vehicleRepository.findByPlateNumberIgnoreCase(trimmed);
        if (exact.isPresent()) {
            return exact;
        }

        String normalized = normalizePlateLookup(trimmed);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        Optional<Vehicle> byNormalized = vehicleRepository.findByNormalizedPlateNumber(normalized);
        if (byNormalized.isPresent()) {
            return byNormalized;
        }

        if (normalized.length() >= 4) {
            String prefix = normalized.substring(0, 4);
            List<Vehicle> candidates = vehicleRepository.findByNormalizedPlateStartingWith(
                    prefix, PageRequest.of(0, 5));
            return pickBestPlateMatch(normalized, candidates);
        }

        return Optional.empty();
    }

    private Optional<Vehicle> pickBestPlateMatch(String normalizedInput, List<Vehicle> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        return candidates.stream()
                .min(Comparator.comparingInt(v -> Math.abs(
                        normalizePlateLookup(v.getPlateNumber()).length() - normalizedInput.length())))
                .filter(v -> Math.abs(
                        normalizePlateLookup(v.getPlateNumber()).length() - normalizedInput.length()) <= 2);
    }

    private String normalizePlateLookup(String plateNumber) {
        return plateNumber.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private boolean matchesBuilding(Reservation reservation, String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            return true;
        }
        if (reservation.getSlot() == null || reservation.getSlot().getZone() == null
                || reservation.getSlot().getZone().getFloor() == null
                || reservation.getSlot().getZone().getFloor().getBuilding() == null) {
            return false;
        }
        return buildingId.equals(reservation.getSlot().getZone().getFloor().getBuilding().getBuildingId());
    }

    private List<Reservation> findPendingReservationsByPlate(String plateNumber) {
        return resolveVehicleByPlate(plateNumber)
                .flatMap(vehicle -> reservationRepository.findFirstPendingByVehicleId(vehicle.getVehicleId()))
                .map(List::of)
                .orElseGet(List::of);
    }

    private ReservationResponse toReservationPreview(Reservation reservation) {
        ReservationResponse resp = new ReservationResponse();
        resp.setReservationId(reservation.getReservationId());
        resp.setReservationCode(reservation.getReservationCode());
        resp.setReservationStatus(reservation.getReservationStatus());
        resp.setReservationNote(reservation.getNote());
        resp.setReservationStart(reservation.getReservationStart());
        resp.setCreatedAt(reservation.getCreatedAt());

        if (reservation.getUser() != null) {
            resp.setUserId(reservation.getUser().getUserId());
            resp.setUsername(reservation.getUser().getUsername());
        }

        Vehicle vehicle = reservation.getVehicle();
        if (vehicle != null) {
            resp.setVehicleId(vehicle.getVehicleId());
            resp.setVehiclePlate(vehicle.getPlateNumber());
            resp.setVehicleColor(vehicle.getVehicleColor());
            resp.setVehicleBrand(vehicle.getBrand());
            resp.setVehicleModel(vehicle.getModel());
            resp.setVehicleImageUrl(vehicle.getImageUrl());
            if (vehicle.getVehicleType() != null) {
                resp.setVehicleTypeName(vehicle.getVehicleType().getTypeName());
                resp.setFloorVehicleTypeId(vehicle.getVehicleType().getVehicleTypeId());
                resp.setFloorVehicleTypeName(vehicle.getVehicleType().getTypeName());
                PricingPolicy policy = pricingService.getActivePolicy(vehicle.getVehicleType().getVehicleTypeId());
                if (policy != null) {
                    resp.setBasePrice(policy.getBasePrice());
                    resp.setHourlyRate(policy.getHourlyRate());
                    resp.setMaxHours(policy.getMaxHours());
                }
            }
        }

        ParkingSlot slot = reservation.getSlot();
        if (slot != null) {
            resp.setSlotId(slot.getSlotId());
            resp.setSlotName(slot.getSlotName());
            resp.setSlotStatus(slot.getSlotStatus());
            Zone zone = slot.getZone();
            if (zone != null) {
                resp.setZoneId(zone.getZoneId());
                resp.setZoneName(zone.getZoneName());
                resp.setZoneStatus(zone.getStatus());
                Floor floor = zone.getFloor();
                if (floor != null) {
                    resp.setFloorId(floor.getFloorId());
                    resp.setFloorName(floor.getFloorName());
                    resp.setFloorLevel(floor.getFloorLevel());
                    if (floor.getVehicleType() != null) {
                        resp.setFloorVehicleTypeId(floor.getVehicleType().getVehicleTypeId());
                        resp.setFloorVehicleTypeName(floor.getVehicleType().getTypeName());
                    }
                    Building building = floor.getBuilding();
                    if (building != null) {
                        resp.setBuildingId(building.getBuildingId());
                        resp.setBuildingName(building.getBuildingName());
                    }
                }
            }
        }

        ticketRepository.findByReservationReservationId(reservation.getReservationId())
                .ifPresent(ticket -> resp.setTicketCode(ticket.getTicketCode()));
        return resp;
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
        resp.setStatus(ps.getSessionStatus());
        resp.setParkingDuration(resolveParkingDurationMinutes(ps));
        resp.setEstimatedFee(ps.getEstimatedFee());

        if (vehicleType != null) {
            PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
            if (policy != null) {
                resp.setBasePrice(policy.getBasePrice());
                resp.setHourlyRate(policy.getHourlyRate());
            }
        }

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

    // ======================== QUICK CHECKIN FLOW ========================
    // Staff chỉ cần quét ảnh biển số — hệ thống tự OCR, tìm reservation / assign slot, tạo session.

    /**
     * Resolve plate number bằng OCR từ ảnh upload.
     * Throw BaseAPIException(OCR_FAILED) nếu ảnh rỗng hoặc không nhận diện được biển số.
     */
    public PlateRecognizerService.OcrResult recognizePlate(MultipartFile plateImage) {
        if (plateImage == null || plateImage.isEmpty()) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Cần cung cấp ảnh biển số (plateImage).");
        }

        PlateRecognizerService.OcrResult ocr = ocrService.recognizeFromUpload(plateImage);
        String plateNumber = ocr.plateNumber();
        if (plateNumber == null || ocr.confidence() < 0.3) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Không nhận diện được biển số từ ảnh. Vui lòng chụp lại rõ nét hơn.");
        }
        log.info("Quick checkin: OCR detected '{}' (confidence {})", plateNumber, ocr.confidence());
        return ocr;
    }

    private String resolvePlateNumber(QuickCheckinRequest req) {
        return recognizePlate(req.getPlateImage()).plateNumber();
    }

    /**
     * Quick checkin DRIVER: OCR biển số → tự tìm reservation PENDING/APPROVED → tạo session.
     * Staff không cần nhập ticketCode.
     */
    @Transactional
    public QuickCheckinResponse quickDriverCheckin(String staffEmail, QuickCheckinRequest req) {
        // 1. Staff phải được assign vào building này
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. Resolve plate number bằng OCR
        String plateNumber = resolvePlateNumber(req);

        // 3. Tìm reservation PENDING theo biển số trong building này
        String normalizedPlate = plateNumber.toUpperCase();
        List<Reservation> candidates = findPendingReservationsByPlate(normalizedPlate);
        Reservation matched = candidates.stream()
                .filter(r -> {
                    String bId = r.getSlot() != null && r.getSlot().getZone() != null
                            && r.getSlot().getZone().getFloor() != null
                            && r.getSlot().getZone().getFloor().getBuilding() != null
                            ? r.getSlot().getZone().getFloor().getBuilding().getBuildingId()
                            : null;
                    return req.getBuildingId().equals(bId);
                })
                .findFirst()
                .orElseThrow(() -> new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND,
                        "Không tìm thấy reservation nào cho biển số " + normalizedPlate + " tại building này. "
                                + "Vui lòng kiểm tra lại biển số hoặc chuyển sang chế độ Guest."));

        // 3b. Validate: không cho checkin sớm hơn reservationStart
        LocalDateTime reservationStart = matched.getReservationStart();
        if (LocalDateTime.now().isBefore(reservationStart)) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TOO_EARLY,
                    "Chưa đến giờ checkin. Reservation bắt đầu lúc " + reservationStart + ". Giờ hiện tại: " + LocalDateTime.now() + ".");
        }

        // 3c. Validate: biển số quét phải khớp với biển số đăng ký trong reservation
        Vehicle resVehicle = matched.getVehicle();
        if (resVehicle != null && resVehicle.getPlateNumber() != null) {
            String registeredPlate = resVehicle.getPlateNumber().toUpperCase();
            if (!normalizedPlate.equals(registeredPlate)) {
                throw new BaseAPIException(ErrorCode.PLATE_MISMATCH,
                        "Biển số quét (" + normalizedPlate + ") không khớp với biển số đăng ký (" + registeredPlate + "). "
                                + "Kiểm tra lại xe hoặc dùng chế độ Guest.");
            }
        }

        // 4. Validate reservation status
        String status = matched.getReservationStatus();
        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_APPROVED,
                    "Reservation không ở trạng thái PENDING (hiện tại: " + status + ")");
        }

        ParkingSlot slot = matched.getSlot();
        if (!"RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_RESERVED,
                    "Slot " + slot.getSlotName() + " không ở trạng thái RESERVED");
        }

        validateNoActiveSessionForPlate(normalizedPlate);

        // 5. Kiểm tra ticket — query ngược từ reservationId (Reservation không có field ticket)
        Ticket ticket = ticketRepository.findByReservationReservationId(matched.getReservationId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND,
                        "Reservation không có ticket"));
        if (ticket.getIsUsed()) {
            throw new BaseAPIException(ErrorCode.TICKET_ALREADY_USED,
                    "Vé đã được sử dụng trước đó. Mã: " + ticket.getTicketCode());
        }

        // 6. Tính pricing
        Vehicle vehicle = matched.getVehicle();
        VehicleType vehicleType = vehicle != null ? vehicle.getVehicleType() : null;
        PricingPolicy policy = null;
        BigDecimal basePrice = BigDecimal.ZERO;
        BigDecimal hourlyRate = BigDecimal.ZERO;
        BigDecimal estimatedFee = BigDecimal.ZERO;
        if (vehicleType != null) {
            policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
            if (policy != null) {
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
                estimatedFee = pricingService.calculateByPolicy(policy, 1);
            }
        }

        // 7. Tạo ParkingSession
        LocalDateTime now = LocalDateTime.now();
        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        ParkingSession session = new ParkingSession();
        session.setTicket(ticket);
        session.setReservation(matched);
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setParkingDuration(0);
        session.setSessionStatus("PENDING_PAYMENT");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setNote(req.getNote());
        session.setCheckinImageUrl(req.getCheckinImageUrl());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // 8. Cập nhật ticket
        ticket.setIsUsed(true);
        ticket.setStatus("USED");
        ticketRepository.save(ticket);

        // 9. Cập nhật reservation
        matched.setReservationStatus("CHECKED_IN");
        reservationRepository.save(matched);

        // 10. Cập nhật slot
        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        // 11. Build response
        QuickCheckinResponse resp = new QuickCheckinResponse();
        resp.setCheckinType("DRIVER");
        resp.setTicketCode(ticket.getTicketCode());
        resp.setSessionId(saved.getSessionId());
        resp.setPlateNumber(plateNumber);
        resp.setOcrConfidence(1.0);
        if (vehicle != null) {
            resp.setVehicleColor(vehicle.getVehicleColor());
            resp.setBrand(vehicle.getBrand());
            resp.setModel(vehicle.getModel());
        }
        if (vehicleType != null) {
            resp.setVehicleTypeId(vehicleType.getVehicleTypeId());
            resp.setVehicleTypeName(vehicleType.getTypeName());
        }
        resp.setCheckinTime(now);
        resp.setCheckinImageUrl(saved.getCheckinImageUrl());
        resp.setParkingDuration(0);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setEstimatedFee(estimatedFee);

        applyHierarchyQuick(resp, slot);

        return resp;
    }

    /**
     * Quick checkin GUEST: OCR biển số → tự assign slot trống → tạo session.
     * Staff không cần nhập slotId.
     */
    @Transactional
    public QuickCheckinResponse quickGuestCheckin(String staffEmail, QuickCheckinRequest req) {
        // [DEBUG] Log entry for quickGuestCheckin
        System.out.println("[DEBUG-6b654b] quickGuestCheckin called - staffEmail: " + staffEmail + ", buildingId: " + req.getBuildingId() + ", vehicleTypeId: " + req.getVehicleTypeId());

        // 1. Staff phải được assign vào building
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. Validate vehicleTypeId
        if (req.getVehicleTypeId() == null || req.getVehicleTypeId().isBlank()) {
            throw new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                    "vehicleTypeId là bắt buộc cho chế độ Guest");
        }
        VehicleType vehicleType = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "Không tìm thấy loại xe: " + req.getVehicleTypeId()));

        // 3. Resolve plate number bằng OCR
        String plateNumber = resolvePlateNumber(req);
        String normalizedPlate = plateNumber.toUpperCase();
        
        // [DEBUG] Log OCR result and validation
        System.out.println("[DEBUG-6b654b] OCR raw plate: '" + plateNumber + "', normalized: '" + normalizedPlate + "'");
        
        // 3b. CẤM Guest checkin nếu plate đã có reservation ACTIVE (PENDING, APPROVED, CHECKED_IN, etc)
        // → Đây là Driver, phải dùng quickDriverCheckin()
        // Lấy tất cả reservation theo plate và lọc trong service
        List<Reservation> existingReservations = resolveVehicleByPlate(normalizedPlate)
                .map(vehicle -> reservationRepository.findByVehicleVehicleId(vehicle.getVehicleId()))
                .orElseGet(List::of);
        List<Reservation> activeReservations = existingReservations.stream()
                .filter(r -> !List.of("COMPLETED", "CANCELLED", "EXPIRED").contains(r.getReservationStatus()))
                .toList();
        if (!activeReservations.isEmpty()) {
            Reservation r = activeReservations.get(0);
            System.out.println("[DEBUG-6b654b] GUEST checkin REJECTED - plate has ACTIVE reservation: " + r.getReservationCode() + ", status: " + r.getReservationStatus());
            throw new BaseAPIException(ErrorCode.RESERVATION_EXISTS_FOR_PLATE,
                    "Biển số " + normalizedPlate + " đã có reservation đang hoạt động (mã: " + r.getReservationCode() + ", trạng thái: " + r.getReservationStatus() + "). Vui lòng dùng chế độ DRIVER để checkin.");
        }
        
        // 3c. Validate không có active session (phòng trường hợp guest session trùng biển số)
        validateNoActiveSessionForPlate(normalizedPlate);
        System.out.println("[DEBUG-6b654b] validateNoActiveSessionForPlate PASSED - no active session found for plate: " + normalizedPlate);

        // 4. Tìm slot trống theo building + vehicleType (ưu tiên tầng thấp)
        ParkingSlot slot = parkingSlotRepository
                .findFirstAvailableByBuildingAndVehicleType(req.getBuildingId(), req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE,
                        "Không có slot trống nào cho loại xe " + vehicleType.getTypeName()
                                + " tại building này."));

        // 5. Tìm hoặc tạo Vehicle
        Vehicle vehicle = resolveVehicleByPlate(normalizedPlate)
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(normalizedPlate);
                    v.setVehicleType(vehicleType);
                    v.setStatus("ACTIVE");
                    return vehicleRepository.save(v);
                });

        // 6. Tính pricing
        PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
        BigDecimal basePrice = policy != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy != null ? policy.getHourlyRate() : BigDecimal.ZERO;
        BigDecimal estimatedFee = policy != null
                ? pricingService.calculateByPolicy(policy, 1) : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        // 7. Tạo ParkingSession (không có reservation)
        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setParkingDuration(0);
        session.setSessionStatus("PENDING_PAYMENT");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setGuestName(req.getGuestName());
        session.setGuestPhone(req.getGuestPhone());
        session.setNote(req.getNote());
        session.setCheckinImageUrl(req.getCheckinImageUrl());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // 8. Tạo guest ticket G-xxx
        Ticket guestTicket = new Ticket();
        guestTicket.setTicketCode(generateGuestTicketCode());
        guestTicket.setIsUsed(false);
        guestTicket.setIsLost(false);
        guestTicket.setStatus("ACTIVE");
        guestTicket.setIssuedAt(now);
        Ticket savedTicket = ticketRepository.save(guestTicket);

        saved.setTicket(savedTicket);
        saved = parkingSessionRepository.save(saved);

        // 9. Cập nhật slot
        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        // 10. Build response
        QuickCheckinResponse resp = new QuickCheckinResponse();
        resp.setCheckinType("GUEST");
        resp.setTicketCode(savedTicket.getTicketCode());
        resp.setSessionId(saved.getSessionId());
        resp.setPlateNumber(normalizedPlate);
        resp.setOcrConfidence(1.0);
        resp.setVehicleColor(vehicle.getVehicleColor());
        resp.setBrand(vehicle.getBrand());
        resp.setModel(vehicle.getModel());
        resp.setVehicleTypeId(vehicleType.getVehicleTypeId());
        resp.setVehicleTypeName(vehicleType.getTypeName());
        resp.setCheckinTime(now);
        resp.setCheckinImageUrl(saved.getCheckinImageUrl());
        resp.setParkingDuration(0);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setEstimatedFee(estimatedFee);

        applyHierarchyQuick(resp, slot);

        return resp;
    }

    private void validateNoActiveSessionForPlate(String plateNumber) {
        Optional<ParkingSession> existing = resolveVehicleByPlate(plateNumber)
                .flatMap(vehicle -> parkingSessionRepository.findAnyActiveSessionByVehicleId(vehicle.getVehicleId()));
        if (existing.isPresent()) {
            throw new BaseAPIException(ErrorCode.PLATE_ALREADY_PARKED,
                    "Biển số " + plateNumber + " đã đang đỗ trong bãi. Vui lòng checkout trước.");
        }
    }

    private void applyHierarchyQuick(QuickCheckinResponse resp, ParkingSlot slot) {
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

    // ======================== AUTO DETECT CHECKIN (DRIVER vs GUEST) ========================

    /**
     * Auto-detect checkin: Staff chỉ cần upload ảnh plate + buildingId.
     * Hệ thống tự detect:
     * - Plate có PENDING/APPROVED reservation → DRIVER flow
     * - Plate không có reservation → GUEST flow
     *
     * @param staffEmail Email của staff đang checkin
     * @param req Request chứa plateImage và buildingId (vehicleTypeId bắt buộc cho GUEST)
     * @return QuickCheckinResponse với checkinType = "DRIVER" hoặc "GUEST"
     */
    @Transactional
    public QuickCheckinResponse quickAutoCheckin(String staffEmail, QuickCheckinRequest req) {
        // 1. Staff phải được assign vào building
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. OCR đọc plate
        String plateNumber = resolvePlateNumber(req);
        String normalizedPlate = plateNumber.toUpperCase();

        System.out.println("[DEBUG-6b654b] quickAutoCheckin - OCR plate: '" + normalizedPlate + "', buildingId: " + req.getBuildingId());

        // 3. Tìm reservation theo plate
        List<Reservation> reservations = findPendingReservationsByPlate(normalizedPlate);

        // 4. Auto-detect: lọc reservation theo building
        Reservation matched = null;
        for (Reservation r : reservations) {
            if (r.getSlot() != null && r.getSlot().getZone() != null
                    && r.getSlot().getZone().getFloor() != null
                    && req.getBuildingId().equals(r.getSlot().getZone().getFloor().getBuilding().getBuildingId())) {
                matched = r;
                break;
            }
        }

        // 5. Xử lý theo loại
        if (matched != null) {
            // DRIVER flow - gọi quickDriverCheckin (nó sẽ validate lại)
            System.out.println("[DEBUG-6b654b] quickAutoCheckin - DETECTED DRIVER, reservationCode: " + matched.getReservationCode());
            return quickDriverCheckin(staffEmail, req);
        } else {
            // GUEST flow
            if (req.getVehicleTypeId() == null || req.getVehicleTypeId().isBlank()) {
                throw new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "vehicleTypeId là bắt buộc cho chế độ Guest");
            }
            System.out.println("[DEBUG-6b654b] quickAutoCheckin - DETECTED GUEST");
            return quickGuestCheckin(staffEmail, req);
        }
    }

    // ======================== END QUICK CHECKIN FLOW ========================

    /** Thời gian đỗ (phút): dùng giá trị đã lưu nếu đã checkout, còn không thì tính tới hiện tại. */
    private Integer resolveParkingDurationMinutes(ParkingSession session) {
        if (session.getCheckinTime() == null) {
            return session.getParkingDuration() != null ? session.getParkingDuration() : 0;
        }
        if (session.getCheckoutTime() != null) {
            return (int) Duration.between(session.getCheckinTime(), session.getCheckoutTime()).toMinutes();
        }
        if (session.getParkingDuration() != null && session.getParkingDuration() > 0) {
            return session.getParkingDuration();
        }
        return (int) Duration.between(session.getCheckinTime(), LocalDateTime.now()).toMinutes();
    }

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

    /**
     * Tính số phút đỗ xe cho session.
     * - DRIVER (có reservation): tính từ reservationStart + gracePeriod
     * - GUEST (không reservation): tính từ checkinTime
     */
    private long calculateParkingMinutes(ParkingSession session, LocalDateTime checkoutTime) {
        Reservation reservation = session.getReservation();
        LocalDateTime effectiveStart;

        if (reservation != null) {
            effectiveStart = reservation.getReservationStart();
            Integer gracePeriod = reservation.getGracePeriodMinutes();
            if (gracePeriod != null && gracePeriod > 0) {
                effectiveStart = effectiveStart.plusMinutes(gracePeriod);
            }
        } else {
            effectiveStart = session.getCheckinTime();
        }

        long minutes = Duration.between(effectiveStart, checkoutTime).toMinutes();
        return Math.max(0, minutes);
    }

}
