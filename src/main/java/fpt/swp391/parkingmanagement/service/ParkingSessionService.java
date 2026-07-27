package fpt.swp391.parkingmanagement.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final NotificationService notificationService;
    private final BuildingRuleService buildingRuleService;
    private final AuditLogService auditLogService;
    private final ZoneStatusSyncService zoneStatusSyncService;

    private void checkStaffBuildingAssignment(String staffEmail, String buildingId) {
        String userId = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"))
                .getUserId();
        if (!buildingStaffRepository.existsByBuildingBuildingIdAndUserUserId(buildingId, userId)) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED, "You are not assigned to this building");
        }
    }

    private String resolveBuildingId(ParkingSlot slot) {
        return resolveBuilding(slot).getBuildingId();
    }

    private Building resolveBuilding(ParkingSlot slot) {
        if (slot == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND);
        Zone zone = slot.getZone();
        if (zone == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND, "Slot has no zone");
        Floor floor = zone.getFloor();
        if (floor == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND, "Zone has no floor");
        Building building = floor.getBuilding();
        if (building == null) throw new BaseAPIException(ErrorCode.SLOT_NOT_FOUND, "Floor has no building");
        return building;
    }

    @Transactional
    public ParkingSessionResponse checkin(String staffEmail, CheckinRequest req) {
        // [DEBUG] Log entry for checkin
        System.out.println("[DEBUG-6b654b] checkin called - staffEmail: " + staffEmail + ", ticketCode: " + req.getTicketCode());
        
        // FIX N+1: Use graph query to load reservation, vehicle, slot chain in one query
        Ticket ticket = ticketRepository.findByTicketCodeGraph(req.getTicketCode())
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
        if (!isCheckinEligibleStatus(resStatus)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_APPROVED);
        }

        if (req.getPlateNumber() != null && reservation.getVehicle() != null
                && reservation.getVehicle().getPlateNumber() != null) {
            if (!platesMatch(req.getPlateNumber(), reservation.getVehicle().getPlateNumber())) {
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
        session.setCheckinVehicleImage(req.getCheckinVehicleImage());
        User staff = userRepository.findByEmail(staffEmail).orElse(null);
        session.setCreatedBy(staff);

        // Update reservation status to CHECKED_IN to prevent auto-expiration job
        reservation.setReservationStatus("CHECKED_IN");
        reservationRepository.save(reservation);

        ParkingSession saved = parkingSessionRepository.save(session);

        ticket.setIsUsed(true);
        ticket.setStatus("USED");
        ticketRepository.save(ticket);

        zoneStatusSyncService.updateSlotStatus(slot, "OCCUPIED");

        // Send checkin notification to driver
        User driver = reservation.getUser();
        Ticket finalTicket = ticket;
        if (driver != null) {
            String buildingName = "";
            if (slot.getZone() != null && slot.getZone().getFloor() != null
                    && slot.getZone().getFloor().getBuilding() != null) {
                buildingName = slot.getZone().getFloor().getBuilding().getBuildingName();
            }
            notificationService.sendToUser(driver.getUsername(), "CHECKIN_CONFIRMED", Map.of(
                    "reservationCode", reservation.getReservationCode(),
                    "buildingName", buildingName,
                    "ticketCode", finalTicket.getTicketCode()
            ));
        }

        ParkingSessionResponse resp = new ParkingSessionResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setTicketCode(ticket.getTicketCode());
        resp.setVehiclePlate(vehicle != null ? vehicle.getPlateNumber() : null);
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckinVehicleImage(saved.getCheckinVehicleImage());
        resp.setParkingDuration(0);
        resp.setEstimatedFee(estimatedFee);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        if (policy != null && vehicle != null && vehicle.getVehicleType() != null) {
            resp.setVehicleTypeId(vehicle.getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(vehicle.getVehicleType().getTypeName());
        }
        applyHierarchy(resp, slot);

        auditLogService.record(
                "CHECKIN",
                "PARKING_SESSION",
                saved.getSessionId(),
                buildingId,
                null,
                "PENDING_PAYMENT",
                "Driver check-in ticket " + ticket.getTicketCode(),
                null);

        return resp;
    }

    @Transactional
    public CheckoutResponse checkout(String staffEmail, CheckoutRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        // FIX N+1: Use graph query to load slot->zone->floor->building, vehicle->vehicleType in one query
        Optional<ParkingSession> optSession = parkingSessionRepository
                .findActiveSessionGraphByTicketId(ticket.getTicketId());

        ParkingSession session = optSession.orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        // THEM: Kiem tra incident authorization - chi cho phep checkout neu incident da duoc resolve
        if (!Boolean.TRUE.equals(session.getIncidentAuthorized())) {
            if (session.getTicket() != null && Boolean.TRUE.equals(session.getTicket().getIsLost())) {
                throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                        "Session requires incident resolution before checkout. Driver needs to report a lost ticket incident first.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        session.setCheckoutTime(now);

        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = calculateParkingMinutes(session, now);
        int hours = Math.max(1, (int) Math.ceil(minutes / 60.0));

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
        session.setCheckoutVehicleImage(req.getCheckoutVehicleImage());

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
            zoneStatusSyncService.updateSlotStatus(slot, "AVAILABLE");
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
        resp.setCheckoutVehicleImage(saved.getCheckoutVehicleImage());
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

        // Send checkout notification to driver
        User driver = session.getReservation() != null ? session.getReservation().getUser() : null;
        Ticket sessionTicket = session.getTicket();
        if (driver != null) {
            String ticketCode = sessionTicket != null ? sessionTicket.getTicketCode() : "";
            notificationService.sendToUser(driver.getUsername(), "CHECKOUT_COMPLETED", Map.of(
                    "reservationCode", session.getReservation() != null ? session.getReservation().getReservationCode() : "",
                    "ticketCode", ticketCode,
                    "totalFee", total
            ));
        }

        auditLogService.record(
                "CHECKOUT",
                "PARKING_SESSION",
                saved.getSessionId(),
                buildingId,
                "ACTIVE",
                "COMPLETED",
                "Checkout completed, fee=" + total,
                null);

        return resp;
    }

    public EstimateResponse estimateFee(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        // FIX N+1: Use graph query to load session with all relationships
        Optional<ParkingSession> optSession = parkingSessionRepository
                .findActiveSessionGraphByTicketId(ticket.getTicketId());

        ParkingSession session = optSession.orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        long minutes = calculateParkingMinutes(session, now);
        int hours = Math.max(1, (int) Math.ceil(minutes / 60.0));

        PricingPolicy policy = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;

        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                if (session.getTotalFee() != null && session.getTotalFee().compareTo(BigDecimal.ZERO) > 0) {
                    total = session.getTotalFee();
                } else {
                    total = pricingService.calculateByPolicy(policy, hours);
                }
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
     * TÃ¬m {@code sessionId} cá»§a phiÃªn ACTIVE á»©ng vá»›i {@code ticketCode}.
     * Tráº£ vá» empty náº¿u khÃ´ng tÃ¬m tháº¥y ticket hoáº·c session khÃ´ng á»Ÿ tráº¡ng thÃ¡i ACTIVE.
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

        // FIX N+1: Use graph query to load all relationships in one query
        ParkingSession session = parkingSessionRepository.findByIdGraph(sessionId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        return processCheckout(session, staffEmail, checkoutImageUrl, paymentMethod);
    }

    /**
     * Driver checkout bằng sessionId (sau khi incident đã resolved).
     * Cho phép checkout không cần ticket nếu session.incidentAuthorized = true.
     */
    @Transactional
    public CheckoutResponse driverCheckoutBySession(String staffEmail, String sessionId, String checkoutImageUrl) {
        System.out.println("[DEBUG-6b654b] driverCheckoutBySession called - sessionId: " + sessionId);

        ParkingSession session = parkingSessionRepository.findByIdGraph(sessionId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        // Validate: phải được authorize qua incident resolution
        if (!Boolean.TRUE.equals(session.getIncidentAuthorized())) {
            // Kiểm tra có ticket không - nếu không thì yêu cầu resolve incident
            if (session.getTicket() == null || session.getTicket().getIsLost() == Boolean.TRUE) {
                throw new BaseAPIException(ErrorCode.BAD_REQUEST,
                        "Session is not authorized via incident resolution. The driver must request staff assistance first.");
            }
        }

        // Process checkout với CASH payment
        return processCheckout(session, staffEmail, checkoutImageUrl, "CASH");
    }

    private CheckoutResponse processCheckout(ParkingSession session, String staffEmail, String checkoutImageUrl, String paymentMethod) {
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
        int hours = Math.max(1, (int) Math.ceil(minutes / 60.0));

        PricingPolicy policy = null;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;

        if (session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            String vtId = session.getVehicle().getVehicleType().getVehicleTypeId();
            policy = pricingService.getActivePolicy(vtId);
            if (policy != null) {
                // Nếu đã có adjustedAmount (từ UPDATE_PAYMENT incident), dùng trực tiếp
                if (session.getTotalFee() != null && session.getTotalFee().compareTo(BigDecimal.ZERO) > 0) {
                    total = session.getTotalFee();
                } else {
                    total = pricingService.calculateByPolicy(policy, hours);
                }
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        String method = paymentMethod != null ? paymentMethod : "CASH";
        boolean electronicPayment = "VNPAY".equals(method) || "PAYOS".equals(method) || "MOMO".equals(method);

        session.setTotalFee(total);
        session.setParkingDuration((int) minutes);
        session.setCheckoutVehicleImage(checkoutImageUrl);
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
            zoneStatusSyncService.updateSlotStatus(slot, "PENDING_EXIT");
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
        resp.setCheckoutVehicleImage(saved.getCheckoutVehicleImage());
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

        assertNoActiveReservationForPlate(plateNumber);
        validateNoActiveSessionForPlate(plateNumber);

        ParkingSlot slot = parkingSlotRepository.findBySlotId(req.getSlotId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_FOUND));

        if (!"AVAILABLE".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE);
        }

        String buildingId = resolveBuildingId(slot);
        checkStaffBuildingAssignment(staffEmail, buildingId);

        VehicleType vehicleType = resolveVehicleTypeFromSlot(slot);
        Building building = resolveBuilding(slot);
        buildingRuleService.validateForEntry(building, vehicleType, LocalDateTime.now());

        // FIX N+1: fuzzy plate lookup để không tạo vehicle trùng khi format biển số khác nhau
        Vehicle vehicle = resolveVehicleByPlate(plateNumber)
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
        session.setCheckinVehicleImage(req.getCheckinVehicleImage());
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

        zoneStatusSyncService.updateSlotStatus(slot, "OCCUPIED");

        GuestCheckinResponse resp = new GuestCheckinResponse();
        resp.setSessionId(saved.getSessionId());
        resp.setTicketCode(savedTicket.getTicketCode());
        resp.setVehiclePlate(vehicle.getPlateNumber());
        resp.setVehicleTypeId(vehicleType.getVehicleTypeId());
        resp.setVehicleTypeName(vehicleType.getTypeName());
        resp.setCheckinTime(saved.getCheckinTime());
        resp.setCheckinVehicleImage(saved.getCheckinVehicleImage());
        resp.setStatus(saved.getSessionStatus());
        resp.setParkingDuration(0);
        resp.setEstimatedFee(estimatedFee);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        applyHierarchyGuest(resp, slot);

        auditLogService.record(
                "GUEST_CHECKIN",
                "PARKING_SESSION",
                saved.getSessionId(),
                buildingId,
                null,
                "PENDING_PAYMENT",
                "Guest check-in plate " + vehicle.getPlateNumber(),
                null);

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
        int hours = Math.max(1, (int) Math.ceil(minutes / 60.0));

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
        session.setCheckoutVehicleImage(req.getCheckoutVehicleImage());

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
            zoneStatusSyncService.updateSlotStatus(slot, "AVAILABLE");
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
        resp.setCheckoutVehicleImage(saved.getCheckoutVehicleImage());
        if (savedPayment != null) resp.setPaymentId(savedPayment.getPaymentId());
        if (policy != null && session.getVehicle() != null && session.getVehicle().getVehicleType() != null) {
            resp.setVehicleTypeId(session.getVehicle().getVehicleType().getVehicleTypeId());
            resp.setVehicleTypeName(session.getVehicle().getVehicleType().getTypeName());
        }
        if (slot != null) applyHierarchy(resp, slot);

        return resp;
    }

    // ======================== GUEST OCR CHECKIN ========================
    // Staff quÃ©t áº£nh biá»ƒn sá»‘ â†’ OCR nháº­n diá»‡n â†’ auto-assign slot â†’ táº¡o session.

    @Transactional
    public GuestCheckinResponse guestCheckinOcr(String staffEmail, GuestCheckinOcrRequest req) {
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        VehicleType vehicleType = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "Vehicle type not found: " + req.getVehicleTypeId()));

        // 1. OCR biá»ƒn sá»‘
        PlateRecognizerService.OcrResult ocr;
        try {
            ocr = ocrService.recognizeFromUpload(req.getPlateImage());
        } catch (Exception e) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Unable to read license plate image: " + e.getMessage());
        }
        String plateNumber = ocr.plateNumber();
        if (plateNumber == null || ocr.confidence() < 0.3) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Could not detect a license plate from the image. Please retake the photo or enter manually.");
        }
        final String finalPlateNumber = plateNumber.toUpperCase();

        // 2. Kiá»ƒm tra biá»ƒn sá»‘ Ä‘Ã£ cÃ³ session ACTIVE chÆ°a (khÃ´ng phÃ¢n biá»‡t driver/guest)
        Optional<ParkingSession> existingSession = parkingSessionRepository.findActiveGuestByPlateNumber(finalPlateNumber);
        if (existingSession.isPresent()) {
            ParkingSession dup = existingSession.get();
            String dupTicket = dup.getTicket() != null ? dup.getTicket().getTicketCode() : "N/A";
            throw new BaseAPIException(ErrorCode.PLATE_ALREADY_PARKED,
                    "Plate number " + finalPlateNumber + " is already parked. "
                            + "Ticket: " + dupTicket + ". Please checkout first.");
        }

        // 3. TÃ¬m slot trá»‘ng theo building + vehicleType (Æ°u tiÃªn táº§ng tháº¥p)
        ParkingSlot slot = parkingSlotRepository
                .findFirstAvailableByBuildingAndVehicleType(req.getBuildingId(), req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE,
                        "No available slots for vehicle type " + vehicleType.getTypeName()
                                + " at this building."));

        // 4. TÃ¬m hoáº·c táº¡o Vehicle - FIX N+1: use graph query
        Vehicle vehicle = vehicleRepository.findByPlateNumberGraph(finalPlateNumber)
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

        // 5. Cáº­p nháº­t thÃ´ng tin xe náº¿u cÃ³ thay Ä‘á»•i
        if (req.getVehicleColor() != null) vehicle.setVehicleColor(req.getVehicleColor());
        if (req.getBrand() != null) vehicle.setBrand(req.getBrand());
        if (req.getModel() != null) vehicle.setModel(req.getModel());
        vehicleRepository.save(vehicle);

        // 6. TÃ­nh pricing
        PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
        BigDecimal basePrice = policy != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy != null ? policy.getHourlyRate() : BigDecimal.ZERO;
        BigDecimal estimatedFee = policy != null
                ? pricingService.calculateByPolicy(policy, 1) : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        // 7. Táº¡o ParkingSession (khÃ´ng cÃ³ reservation)
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
        session.setCheckinVehicleImage(req.getCheckinVehicleImage());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // 8. Táº¡o guest ticket G-xxx
        Ticket guestTicket = new Ticket();
        guestTicket.setTicketCode(generateGuestTicketCode());
        guestTicket.setIsUsed(false);
        guestTicket.setIsLost(false);
        guestTicket.setStatus("ACTIVE");
        guestTicket.setIssuedAt(now);
        Ticket savedTicket = ticketRepository.save(guestTicket);

        saved.setTicket(savedTicket);
        saved = parkingSessionRepository.save(saved);

        // 9. Cáº­p nháº­t slot
        zoneStatusSyncService.updateSlotStatus(slot, "OCCUPIED");

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
        resp.setCheckinVehicleImage(saved.getCheckinVehicleImage());
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
    // Staff quÃ©t áº£nh biá»ƒn sá»‘ â†’ OCR nháº­n diá»‡n â†’ validate vs ticketCode â†’ checkout.

    @Transactional
    public CheckoutResponse guestCheckoutOcr(String staffEmail, GuestCheckoutOcrRequest req) {
        PlateRecognizerService.OcrResult ocr = recognizePlate(req.getPlateImage());
        return guestCheckoutOcr(staffEmail, req.getTicketCode(), req.getCheckoutVehicleImage(), req.getPaymentMethod(), ocr.plateNumber().toUpperCase());
    }

    @Transactional
    public CheckoutResponse guestCheckoutOcr(String staffEmail, CheckoutRequest checkoutRequest, String scannedPlate) {
        return guestCheckoutOcr(staffEmail, checkoutRequest.getTicketCode(), checkoutRequest.getCheckoutVehicleImage(), checkoutRequest.getPaymentMethod(), scannedPlate);
    }

    @Transactional
    public CheckoutResponse guestCheckoutOcr(String staffEmail, String ticketCode, String checkoutImageUrl, String paymentMethod, String scannedPlate) {
        // 1. Validate ticket tá»“n táº¡i
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        // 2. TÃ¬m session ACTIVE theo ticket - FIX N+1: use graph query
        ParkingSession session = parkingSessionRepository
                .findActiveSessionGraphByTicketId(ticket.getTicketId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "No active guest session found for ticket: " + ticketCode));

        // 3. Verify Ä‘Ã¢y lÃ  guest session (khÃ´ng cÃ³ reservation)
        if (session.getReservation() != null) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST,
                    "This is a driver session with a reservation. Guest checkout flow cannot be used.");
        }

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        // 4. Validate: biá»ƒn sá»‘ quÃ©t pháº£i khá»›p vá»›i biá»ƒn sá»‘ trong session
        Vehicle sessionVehicle = session.getVehicle();
        if (sessionVehicle == null) {
            throw new BaseAPIException(ErrorCode.VEHICLE_NOT_FOUND,
                    "Session has no vehicle information.");
        }
        String sessionPlate = sessionVehicle.getPlateNumber().toUpperCase();
        if (!scannedPlate.equals(sessionPlate)) {
            throw new BaseAPIException(ErrorCode.PLATE_MISMATCH,
                    "Scanned plate (" + scannedPlate + ") does not match registered plate (" + sessionPlate
                            + "). Verify the vehicle or use manual search.");
        }

        // 6. TÃ­nh phÃ­
        LocalDateTime now = LocalDateTime.now();
        if (session.getCheckinTime() == null) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TIME_MISSING);
        }
        long minutes = calculateParkingMinutes(session, now);
        int hours = Math.max(1, (int) Math.ceil(minutes / 60.0));

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
        session.setCheckoutVehicleImage(checkoutImageUrl);

        Payment savedPayment = null;
        if (electronicPayment) {
            if (!"PAID".equalsIgnoreCase(session.getPaymentStatus())) {
                throw new BaseAPIException(ErrorCode.PAYMENT_NOT_COMPLETED,
                        "Electronic payment is not completed.");
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
            zoneStatusSyncService.updateSlotStatus(slot, "AVAILABLE");
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
        resp.setCheckoutVehicleImage(saved.getCheckoutVehicleImage());
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
     * Tra cá»©u nhanh biá»ƒn sá»‘ cho mÃ n staff check-in:
     * 1) reservation PENDING/APPROVED (driver)
     * 2) guest session ACTIVE (walk-in Ä‘Ã£ check-in)
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

        // FIX N+1: Use graph query to load vehicle with vehicleType
        Optional<Vehicle> exact = vehicleRepository.findByPlateNumberGraph(trimmed);
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

    private boolean platesMatch(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizePlateLookup(left).equals(normalizePlateLookup(right));
    }

    private boolean isCheckinEligibleStatus(String status) {
        return "PENDING".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status);
    }

    private void assertNoActiveReservationForPlate(String plateNumber) {
        List<Reservation> existingReservations = resolveVehicleByPlate(plateNumber)
                .map(vehicle -> reservationRepository.findByVehicleVehicleId(vehicle.getVehicleId()))
                .orElseGet(List::of);
        List<Reservation> activeReservations = existingReservations.stream()
                .filter(r -> !List.of("COMPLETED", "CANCELLED", "EXPIRED").contains(r.getReservationStatus()))
                .toList();
        if (!activeReservations.isEmpty()) {
            Reservation r = activeReservations.get(0);
            System.out.println("[DEBUG-6b654b] GUEST checkin REJECTED - plate has ACTIVE reservation: "
                    + r.getReservationCode() + ", status: " + r.getReservationStatus());
            throw new BaseAPIException(ErrorCode.RESERVATION_EXISTS_FOR_PLATE,
                    "Plate number " + plateNumber + " already has an active reservation (code: "
                            + r.getReservationCode() + ", status: " + r.getReservationStatus()
                            + "). Please use DRIVER mode to check in.");
        }
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
        Optional<Vehicle> vehicleOpt = resolveVehicleByPlate(plateNumber);
        if (vehicleOpt.isPresent()) {
            return reservationRepository.findFirstPendingByVehicleId(vehicleOpt.get().getVehicleId())
                    .map(List::of)
                    .orElseGet(List::of);
        }
        // Fallback: query trực tiếp theo biển số chuẩn hóa (phòng vehicle resolve miss)
        String normalized = normalizePlateLookup(plateNumber);
        if (normalized.isBlank()) {
            return List.of();
        }
        return reservationRepository.findPendingByNormalizedPlateNumber(normalized);
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
        resp.setCheckinVehicleImage(ps.getCheckinVehicleImage());
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
    // Staff chá»‰ cáº§n quÃ©t áº£nh biá»ƒn sá»‘ â€” há»‡ thá»‘ng tá»± OCR, tÃ¬m reservation / assign slot, táº¡o session.

    /**
     * Resolve plate number báº±ng OCR tá»« áº£nh upload.
     * Throw BaseAPIException(OCR_FAILED) náº¿u áº£nh rá»—ng hoáº·c khÃ´ng nháº­n diá»‡n Ä‘Æ°á»£c biá»ƒn sá»‘.
     */
    public PlateRecognizerService.OcrResult recognizePlate(MultipartFile plateImage) {
        if (plateImage == null || plateImage.isEmpty()) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "License plate image (plateImage) is required.");
        }

        PlateRecognizerService.OcrResult ocr = ocrService.recognizeFromUpload(plateImage);
        String plateNumber = ocr.plateNumber();
        if (plateNumber == null || ocr.confidence() < 0.3) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Could not detect a license plate from the image. Please retake a clearer photo.");
        }
        log.info("Quick checkin: OCR detected '{}' (confidence {})", plateNumber, ocr.confidence());
        return ocr;
    }

    private String resolvePlateNumber(QuickCheckinRequest req) {
        if (req.getPlateNumber() != null && !req.getPlateNumber().isBlank()) {
            String normalized = req.getPlateNumber().trim().toUpperCase();
            log.info("Quick checkin: using provided plate '{}'", normalized);
            return normalized;
        }
        String fromOcr = recognizePlate(req.getPlateImage()).plateNumber();
        req.setPlateNumber(fromOcr);
        return fromOcr;
    }

    /**
     * Quick checkin DRIVER: OCR biá»ƒn sá»‘ â†’ tá»± tÃ¬m reservation PENDING/APPROVED â†’ táº¡o session.
     * Staff khÃ´ng cáº§n nháº­p ticketCode.
     */
    @Transactional
    public QuickCheckinResponse quickDriverCheckin(String staffEmail, QuickCheckinRequest req) {
        // 1. Staff pháº£i Ä‘Æ°á»£c assign vÃ o building nÃ y
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. Resolve plate number báº±ng OCR
        String plateNumber = resolvePlateNumber(req);

        // 3. TÃ¬m reservation PENDING theo biá»ƒn sá»‘ trong building nÃ y
        String normalizedPlate = plateNumber.toUpperCase();
        List<Reservation> candidates = findPendingReservationsByPlate(normalizedPlate);
        Reservation matched = candidates.stream()
                .filter(r -> matchesBuilding(r, req.getBuildingId()))
                .findFirst()
                .orElseThrow(() -> new BaseAPIException(ErrorCode.RESERVATION_NOT_FOUND,
                        "No reservation found for plate " + normalizedPlate + " at this building. "
                                + "Please verify the plate number or switch to Guest mode."));

        // 3b. Validate: khÃ´ng cho checkin sá»›m hÆ¡n reservationStart
        LocalDateTime reservationStart = matched.getReservationStart();
        if (LocalDateTime.now().isBefore(reservationStart)) {
            throw new BaseAPIException(ErrorCode.CHECKIN_TOO_EARLY,
                    "Too early to check in. Reservation starts at " + reservationStart + ". Current time: " + LocalDateTime.now() + ".");
        }

        // 3c. Validate: biá»ƒn sá»‘ quÃ©t pháº£i khá»›p vá»›i biá»ƒn sá»‘ Ä‘Äƒng kÃ½ trong reservation
        Vehicle resVehicle = matched.getVehicle();
        if (resVehicle != null && resVehicle.getPlateNumber() != null) {
            String registeredPlate = resVehicle.getPlateNumber().toUpperCase();
            if (!normalizedPlate.equals(registeredPlate)) {
                throw new BaseAPIException(ErrorCode.PLATE_MISMATCH,
                        "Scanned plate (" + normalizedPlate + ") does not match registered plate (" + registeredPlate + "). "
                                + "Verify the vehicle or use Guest mode.");
            }
        }

        // 4. Validate reservation status
        String status = matched.getReservationStatus();
        if (!isCheckinEligibleStatus(status)) {
            throw new BaseAPIException(ErrorCode.RESERVATION_NOT_APPROVED,
                    "Reservation is not in PENDING status (current: " + status + ")");
        }

        ParkingSlot slot = matched.getSlot();
        if (!"RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_RESERVED,
                    "Slot " + slot.getSlotName() + " is not in RESERVED status");
        }

        validateNoActiveSessionForPlate(normalizedPlate);

        // 5. Kiá»ƒm tra ticket â€” query ngÆ°á»£c tá»« reservationId (Reservation khÃ´ng cÃ³ field ticket)
        Ticket ticket = ticketRepository.findByReservationReservationId(matched.getReservationId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND,
                        "Reservation has no ticket"));
        if (ticket.getIsUsed()) {
            throw new BaseAPIException(ErrorCode.TICKET_ALREADY_USED,
                    "Ticket was already used previously. Code: " + ticket.getTicketCode());
        }

        // 6. TÃ­nh pricing
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

        // 7. Táº¡o ParkingSession
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
        session.setCheckinVehicleImage(req.getCheckinVehicleImage());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // 8. Cáº­p nháº­t ticket
        ticket.setIsUsed(true);
        ticket.setStatus("USED");
        ticketRepository.save(ticket);

        // 9. Cáº­p nháº­t reservation
        matched.setReservationStatus("CHECKED_IN");
        reservationRepository.save(matched);

        // 10. Cáº­p nháº­t slot
        zoneStatusSyncService.updateSlotStatus(slot, "OCCUPIED");

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
        resp.setCheckinVehicleImage(saved.getCheckinVehicleImage());
        resp.setParkingDuration(0);
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setEstimatedFee(estimatedFee);

        applyHierarchyQuick(resp, slot);

        return resp;
    }

    /**
     * Quick checkin GUEST: OCR biá»ƒn sá»‘ â†’ tá»± assign slot trá»‘ng â†’ táº¡o session.
     * Staff khÃ´ng cáº§n nháº­p slotId.
     */
    @Transactional
    public QuickCheckinResponse quickGuestCheckin(String staffEmail, QuickCheckinRequest req) {
        return quickGuestCheckin(staffEmail, req, resolvePlateNumber(req));
    }

    private QuickCheckinResponse quickGuestCheckin(String staffEmail, QuickCheckinRequest req, String plateNumber) {
        // [DEBUG] Log entry for quickGuestCheckin
        System.out.println("[DEBUG-6b654b] quickGuestCheckin called - staffEmail: " + staffEmail + ", buildingId: " + req.getBuildingId() + ", vehicleTypeId: " + req.getVehicleTypeId());

        // 1. Staff pháº£i Ä‘Æ°á»£c assign vÃ o building
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. Validate vehicleTypeId
        if (req.getVehicleTypeId() == null || req.getVehicleTypeId().isBlank()) {
            throw new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                    "vehicleTypeId is required for Guest mode");
        }
        VehicleType vehicleType = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "Vehicle type not found: " + req.getVehicleTypeId()));

        // 3. Plate number already resolved by caller (OCR / request)
        String normalizedPlate = plateNumber.toUpperCase();
        
        // [DEBUG] Log OCR result and validation
        System.out.println("[DEBUG-6b654b] OCR raw plate: '" + plateNumber + "', normalized: '" + normalizedPlate + "'");
        
        // 3b. Cáº¤M Guest checkin náº¿u plate Ä‘Ã£ cÃ³ reservation ACTIVE (PENDING, APPROVED, CHECKED_IN, etc)
        // â†’ ÄÃ¢y lÃ  Driver, pháº£i dÃ¹ng quickDriverCheckin()
        // Láº¥y táº¥t cáº£ reservation theo plate vÃ  lá»c trong service
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
                    "Plate number " + normalizedPlate + " already has an active reservation (code: " + r.getReservationCode() + ", status: " + r.getReservationStatus() + "). Please use DRIVER mode to check in.");
        }
        
        // 3c. Validate khÃ´ng cÃ³ active session (phÃ²ng trÆ°á»ng há»£p guest session trÃ¹ng biá»ƒn sá»‘)
        validateNoActiveSessionForPlate(normalizedPlate);
        System.out.println("[DEBUG-6b654b] validateNoActiveSessionForPlate PASSED - no active session found for plate: " + normalizedPlate);

        // 4. TÃ¬m slot trá»‘ng theo building + vehicleType (Æ°u tiÃªn táº§ng tháº¥p)
        ParkingSlot slot = parkingSlotRepository
                .findFirstAvailableByBuildingAndVehicleType(req.getBuildingId(), req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE,
                        "No available slots for vehicle type " + vehicleType.getTypeName()
                                + " at this building."));

        // 5. TÃ¬m hoáº·c táº¡o Vehicle - FIX N+1: use graph query
        Vehicle vehicle = resolveVehicleByPlate(normalizedPlate)
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(normalizedPlate);
                    v.setVehicleType(vehicleType);
                    v.setStatus("ACTIVE");
                    return vehicleRepository.save(v);
                });

        // 6. TÃ­nh pricing
        PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
        BigDecimal basePrice = policy != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy != null ? policy.getHourlyRate() : BigDecimal.ZERO;
        BigDecimal estimatedFee = policy != null
                ? pricingService.calculateByPolicy(policy, 1) : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        User staff = userRepository.findByEmail(staffEmail).orElse(null);

        // 7. Táº¡o ParkingSession (khÃ´ng cÃ³ reservation)
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
        session.setCheckinVehicleImage(req.getCheckinVehicleImage());
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // 8. Táº¡o guest ticket G-xxx
        Ticket guestTicket = new Ticket();
        guestTicket.setTicketCode(generateGuestTicketCode());
        guestTicket.setIsUsed(false);
        guestTicket.setIsLost(false);
        guestTicket.setStatus("ACTIVE");
        guestTicket.setIssuedAt(now);
        Ticket savedTicket = ticketRepository.save(guestTicket);

        saved.setTicket(savedTicket);
        saved = parkingSessionRepository.save(saved);

        // 9. Cáº­p nháº­t slot
        zoneStatusSyncService.updateSlotStatus(slot, "OCCUPIED");

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
        resp.setCheckinVehicleImage(saved.getCheckinVehicleImage());
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
                    "Plate number " + plateNumber + " is already parked. Please checkout first.");
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
     * Auto-detect checkin: Staff chá»‰ cáº§n upload áº£nh plate + buildingId.
     * Há»‡ thá»‘ng tá»± detect:
     * - Plate cÃ³ PENDING/APPROVED reservation â†’ DRIVER flow
     * - Plate khÃ´ng cÃ³ reservation â†’ GUEST flow
     *
     * @param staffEmail Email cá»§a staff Ä‘ang checkin
     * @param req Request chá»©a plateImage vÃ  buildingId (vehicleTypeId báº¯t buá»™c cho GUEST)
     * @return QuickCheckinResponse vá»›i checkinType = "DRIVER" hoáº·c "GUEST"
     */
    @Transactional
    public QuickCheckinResponse quickAutoCheckin(String staffEmail, QuickCheckinRequest req) {
        // 1. Staff pháº£i Ä‘Æ°á»£c assign vÃ o building
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. Resolve plate once; downstream driver/guest flows reuse req.plateNumber (no second OCR)
        String plateNumber = resolvePlateNumber(req);
        String normalizedPlate = plateNumber.toUpperCase();

        System.out.println("[DEBUG-6b654b] quickAutoCheckin - plate: '" + normalizedPlate + "', buildingId: " + req.getBuildingId());

        // 3. TÃ¬m reservation theo plate
        List<Reservation> reservations = findPendingReservationsByPlate(normalizedPlate);

        // 4. Auto-detect: lá»c reservation theo building
        Reservation matched = null;
        for (Reservation r : reservations) {
            if (r.getSlot() != null && r.getSlot().getZone() != null
                    && r.getSlot().getZone().getFloor() != null
                    && req.getBuildingId().equals(r.getSlot().getZone().getFloor().getBuilding().getBuildingId())) {
                matched = r;
                break;
            }
        }

        // 5. Xá»­ lÃ½ theo loáº¡i
        if (matched != null) {
            // DRIVER flow - gá»i quickDriverCheckin (nÃ³ sáº½ validate láº¡i)
            System.out.println("[DEBUG-6b654b] quickAutoCheckin - DETECTED DRIVER, reservationCode: " + matched.getReservationCode());
            return quickDriverCheckin(staffEmail, req);
        } else {
            // GUEST flow
            if (req.getVehicleTypeId() == null || req.getVehicleTypeId().isBlank()) {
                throw new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "vehicleTypeId is required for Guest mode");
            }
            System.out.println("[DEBUG-6b654b] quickAutoCheckin - DETECTED GUEST");
            return quickGuestCheckin(staffEmail, req, normalizedPlate);
        }
    }

    // ======================== END QUICK CHECKIN FLOW ========================

    /** Thá»i gian Ä‘á»— (phÃºt): dÃ¹ng giÃ¡ trá»‹ Ä‘Ã£ lÆ°u náº¿u Ä‘Ã£ checkout, cÃ²n khÃ´ng thÃ¬ tÃ­nh tá»›i hiá»‡n táº¡i. */
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
     * TÃ­nh sá»‘ phÃºt Ä‘á»— xe cho session.
     * - DRIVER (cÃ³ reservation): tÃ­nh tá»« reservationStart + gracePeriod
     * - GUEST (khÃ´ng reservation): tÃ­nh tá»« checkinTime
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