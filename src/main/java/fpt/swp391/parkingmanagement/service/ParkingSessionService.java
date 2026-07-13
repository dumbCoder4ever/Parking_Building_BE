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
import fpt.swp391.parkingmanagement.dto.CheckinResponse;
import fpt.swp391.parkingmanagement.dto.CheckoutRequest;
import fpt.swp391.parkingmanagement.dto.CheckoutResponse;
import fpt.swp391.parkingmanagement.dto.EstimateResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckinOcrRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckinResponse;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutOcrRequest;
import fpt.swp391.parkingmanagement.dto.GuestCheckoutRequest;
import fpt.swp391.parkingmanagement.dto.ParkingSessionResponse;
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
import fpt.swp391.parkingmanagement.util.PlateNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * Chống duplicate check-in: nếu biển số đang có session ACTIVE hoặc PENDING_PAYMENT
     * (cả DRIVER lẫn GUEST) thì ném PLATE_ALREADY_PARKED.
     * Áp dụng cho cả 2 luồng unifiedDriverCheckin / unifiedGuestCheckin.
     */
    private void validateNoActiveSessionForPlate(String plateNumber) {
        String normalized = PlateNormalizer.normalize(plateNumber);
        Optional<ParkingSession> existingOpt = parkingSessionRepository.findActiveByPlateNumber(normalized);

        // #region agent log
        if (existingOpt.isPresent()) {
            ParkingSession existing = existingOpt.get();
            String existingPlateFromVehicle = existing.getVehicle() != null ? existing.getVehicle().getPlateNumber() : null;
            String existingPlateFromReservation = (existing.getReservation() != null && existing.getReservation().getVehicle() != null)
                    ? existing.getReservation().getVehicle().getPlateNumber() : null;
            debugLog("validateNoActiveSessionForPlate:FOUND", "Existing ACTIVE/PENDING_PAYMENT session found for plate",
                new String[]{"queryPlate", "sessionId", "sessionStatus", "existingPlateFromVehicle", "existingPlateFromReservation", "slotId", "reservationId"},
                new String[]{normalized, existing.getSessionId(), existing.getSessionStatus(),
                        String.valueOf(existingPlateFromVehicle), String.valueOf(existingPlateFromReservation),
                        existing.getSlot() != null ? existing.getSlot().getSlotId() : "null",
                        existing.getReservation() != null ? existing.getReservation().getReservationId() : "null"},
                new String[]{"H1", "H3", "H4"});
        } else {
            debugLog("validateNoActiveSessionForPlate:NOT_FOUND", "No existing ACTIVE/PENDING_PAYMENT session for plate",
                new String[]{"queryPlate"}, new String[]{normalized}, new String[]{"H1"});
        }
        // #endregion

        existingOpt.ifPresent(existing -> {
            throw new BaseAPIException(ErrorCode.PLATE_ALREADY_PARKED,
                    "Vehicle " + normalized + " already has an active parking session (sessionId="
                            + existing.getSessionId()
                            + ", status=" + existing.getSessionStatus() + ")");
        });
    }

    /**
     * Unified check-in (theo script §3):
     *   1. Validate staff đang ở building này.
     *   2. OCR biển số từ ảnh upload.
     *   3. Chống duplicate: nếu biển số đã có session ACTIVE/PENDING_PAYMENT → 409 PLATE_ALREADY_PARKED.
     *   4. Tìm reservation PENDING/APPROVED theo biển số + building → DRIVER.
     *      Không có reservation → GUEST.
     *   5. Set sessionStatus = "PENDING_PAYMENT" (sẽ chuyển ACTIVE sau khi payment webhook về).
     */
    @Transactional
    public CheckinResponse unifiedCheckin(String staffEmail, CheckinRequest req) {
        // 1. Validate staff đang ở building
        checkStaffBuildingAssignment(staffEmail, req.getBuildingId());

        // 2. OCR biển số
        String plateNumber = resolvePlateNumber(req.getPlateImage());
        String normalizedPlate = PlateNormalizer.normalize(plateNumber);

        // #region agent log
        debugLog("unifiedCheckin:afterOCR", "OCR result", new String[]{"plateNumber", "buildingId", "vehicleTypeId"}, new String[]{normalizedPlate, String.valueOf(req.getBuildingId()), String.valueOf(req.getVehicleTypeId())}, new String[]{"H1", "H3", "H4"});
        // #endregion

        // 3. Chống duplicate (bước 2 trong script §3)
        validateNoActiveSessionForPlate(normalizedPlate);

        // 4. Tìm reservation PENDING/APPROVED theo biển số trong building này
        List<Reservation> candidates = reservationRepository.findPendingByPlateNumber(normalizedPlate);
        Optional<Reservation> matched = candidates.stream()
                .filter(r -> {
                    String bId = r.getSlot() != null && r.getSlot().getZone() != null
                            && r.getSlot().getZone().getFloor() != null
                            && r.getSlot().getZone().getFloor().getBuilding() != null
                            ? r.getSlot().getZone().getFloor().getBuilding().getBuildingId()
                            : null;
                    return req.getBuildingId().equals(bId);
                })
                .filter(r -> {
                    String status = r.getReservationStatus();
                    return "PENDING".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status);
                })
                .findFirst();

        // 5. Phân nhánh Driver/Guest
        if (matched.isPresent()) {
            return unifiedDriverCheckin(matched.get(), normalizedPlate);
        }
        return unifiedGuestCheckin(req, normalizedPlate);
    }

    /**
     * Driver check-in: dùng reservation đã match.
     * Validate biển số khớp, slot RESERVED, ticket chưa dùng.
     * Set sessionStatus = PENDING_PAYMENT.
     */
    private CheckinResponse unifiedDriverCheckin(Reservation reservation, String normalizedPlate) {
        Vehicle resVehicle = reservation.getVehicle();
        if (resVehicle != null && resVehicle.getPlateNumber() != null) {
            String registeredPlate = resVehicle.getPlateNumber().toUpperCase();
            if (!normalizedPlate.equals(registeredPlate)) {
                throw new BaseAPIException(ErrorCode.PLATE_MISMATCH,
                        "Biển số quét (" + normalizedPlate + ") không khớp với biển số đăng ký ("
                                + registeredPlate + ").");
            }
        }

        ParkingSlot slot = reservation.getSlot();
        if (!"RESERVED".equalsIgnoreCase(slot.getSlotStatus())) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_RESERVED,
                    "Slot " + slot.getSlotName() + " không ở trạng thái RESERVED");
        }

        Ticket ticket = ticketRepository.findByReservationReservationId(reservation.getReservationId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND,
                        "Reservation không có ticket"));
        if (Boolean.TRUE.equals(ticket.getIsUsed())) {
            throw new BaseAPIException(ErrorCode.TICKET_ALREADY_USED,
                    "Vé đã được sử dụng trước đó. Mã: " + ticket.getTicketCode());
        }

        Vehicle vehicle = reservation.getVehicle();
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

        // Tạo ParkingSession (PENDING_PAYMENT — sẽ lên ACTIVE khi payment webhook về)
        LocalDateTime now = LocalDateTime.now();
        User staff = null;

        ParkingSession session = new ParkingSession();
        session.setTicket(ticket);
        session.setReservation(reservation);
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setSessionStatus("PENDING_PAYMENT");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setCreatedBy(staff);

        // #region agent log
        debugLog("233", "unifiedCheckin: driver session CREATED with PENDING_PAYMENT status",
                new String[]{"ticketCode", "reservationId", "slotId", "vehicleId", "sessionStatus", "paymentStatus"},
                new String[]{ticket != null ? String.valueOf(ticket.getTicketCode()) : "null",
                        reservation != null ? String.valueOf(reservation.getReservationId()) : "null",
                        slot != null ? String.valueOf(slot.getSlotId()) : "null",
                        vehicle != null ? String.valueOf(vehicle.getVehicleId()) : "null",
                        "PENDING_PAYMENT", "UNPAID"},
                new String[]{"H3"});
        // #endregion

        ParkingSession saved = parkingSessionRepository.save(session);

        ticket.setIsUsed(true);
        ticket.setStatus("USED");
        ticketRepository.save(ticket);

        reservation.setReservationStatus("CHECKED_IN");
        reservationRepository.save(reservation);

        slot.setSlotStatus("OCCUPIED");
        parkingSlotRepository.save(slot);

        CheckinResponse resp = new CheckinResponse();
        resp.setCheckinType("DRIVER");
        resp.setTicketCode(ticket.getTicketCode());
        resp.setSessionId(saved.getSessionId());
        resp.setPlateNumber(normalizedPlate);
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
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setEstimatedFee(estimatedFee);
        resp.setSessionStatus("PENDING_PAYMENT");
        resp.setPaymentStatus("UNPAID");
        applyHierarchyQuickToCheckin(resp, slot);
        return resp;
    }

    /**
     * Guest check-in: tự assign slot trống theo building + vehicleType.
     * Set sessionStatus = PENDING_PAYMENT.
     */
    private CheckinResponse unifiedGuestCheckin(CheckinRequest req, String normalizedPlate) {
        if (req.getVehicleTypeId() == null || req.getVehicleTypeId().isBlank()) {
            throw new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                    "vehicleTypeId là bắt buộc cho luồng Guest");
        }
        VehicleType vehicleType = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND,
                        "Không tìm thấy loại xe: " + req.getVehicleTypeId()));

        // Ưu tiên tầng thấp, rồi slotName ASC
        List<ParkingSlot> availableSlots = parkingSlotRepository
                .findAvailableByBuildingAndVehicleType(req.getBuildingId(), req.getVehicleTypeId());
        if (availableSlots.isEmpty()) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE,
                    "Không có slot trống nào cho loại xe " + vehicleType.getTypeName()
                            + " tại building này.");
        }
        ParkingSlot slot = availableSlots.get(0);

        Vehicle vehicle = vehicleRepository.findByPlateNumberIgnoreCase(normalizedPlate)
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(normalizedPlate);
                    v.setVehicleType(vehicleType);
                    v.setStatus("ACTIVE");
                    if (req.getVehicleColor() != null && !req.getVehicleColor().isBlank()) {
                        v.setVehicleColor(req.getVehicleColor());
                    }
                    if (req.getBrand() != null && !req.getBrand().isBlank()) {
                        v.setBrand(req.getBrand());
                    }
                    if (req.getModel() != null && !req.getModel().isBlank()) {
                        v.setModel(req.getModel());
                    }
                    return vehicleRepository.save(v);
                });

        PricingPolicy policy = pricingService.getActivePolicy(vehicleType.getVehicleTypeId());
        BigDecimal basePrice = policy != null ? policy.getBasePrice() : BigDecimal.ZERO;
        BigDecimal hourlyRate = policy != null ? policy.getHourlyRate() : BigDecimal.ZERO;
        BigDecimal estimatedFee = policy != null
                ? pricingService.calculateByPolicy(policy, 1) : BigDecimal.ZERO;

        LocalDateTime now = LocalDateTime.now();
        User staff = null;

        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setSlot(slot);
        session.setCheckinTime(now);
        session.setSessionStatus("PENDING_PAYMENT");
        session.setPaymentStatus("UNPAID");
        session.setEstimatedFee(estimatedFee);
        session.setCreatedBy(staff);

        ParkingSession saved = parkingSessionRepository.save(session);

        // Sinh guest ticket G-xxx
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

        CheckinResponse resp = new CheckinResponse();
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
        resp.setBasePrice(basePrice);
        resp.setHourlyRate(hourlyRate);
        resp.setEstimatedFee(estimatedFee);
        resp.setSessionStatus("PENDING_PAYMENT");
        resp.setPaymentStatus("UNPAID");
        applyHierarchyQuickToCheckin(resp, slot);
        return resp;
    }

    /**
     * Copy zone/floor/building info từ ParkingSlot sang CheckinResponse.
     */
    private void applyHierarchyQuickToCheckin(CheckinResponse resp, ParkingSlot slot) {
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

    @Transactional
    public CheckoutResponse checkout(String staffEmail, CheckoutRequest req) {
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        // #region agent log
        debugLog("398", "checkout legacy: ticket found, searching session with status ACTIVE/PENDING_PAYMENT",
                new String[]{"ticketCode", "ticketId"},
                new String[]{String.valueOf(req.getTicketCode()), String.valueOf(ticket.getTicketId())},
                new String[]{"H4"});
        // #endregion
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
            // #region agent log
            debugLog("560", "findSessionIdByTicketCode: no session for ticket",
                    new String[]{"ticketCode"}, new String[]{String.valueOf(ticketCode)},
                    new String[]{"H1"});
            // #endregion
            return Optional.empty();
        }
        ParkingSession session = sessionOpt.get();
        // #region agent log
        debugLog("564", "findSessionIdByTicketCode: session found, checking status",
                new String[]{"ticketCode", "sessionId", "sessionStatus", "paymentStatus"},
                new String[]{String.valueOf(ticketCode), String.valueOf(session.getSessionId()),
                        String.valueOf(session.getSessionStatus()), String.valueOf(session.getPaymentStatus())},
                new String[]{"H1"});
        // #endregion
        String currentStatus = session.getSessionStatus();
        boolean accepted = "ACTIVE".equalsIgnoreCase(currentStatus)
                || "PENDING_PAYMENT".equalsIgnoreCase(currentStatus);
        if (!accepted) {
            // #region agent log
            debugLog("566", "findSessionIdByTicketCode: REJECTED — session not in ACTIVE/PENDING_PAYMENT",
                    new String[]{"ticketCode", "sessionId", "actualStatus"},
                    new String[]{String.valueOf(ticketCode), String.valueOf(session.getSessionId()),
                        String.valueOf(currentStatus)},
                    new String[]{"H1"});
            // #endregion
            return Optional.empty();
        }
        return Optional.ofNullable(session.getSessionId());
    }

    @Transactional
    public CheckoutResponse confirmExitAndCheckout(String staffEmail, String sessionId, String paymentMethod) {
        ParkingSession session = parkingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.SESSION_NOT_FOUND));

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        // #region agent log
        debugLog("576", "confirmExitAndCheckout: found session, checking ACTIVE",
                new String[]{"sessionId", "sessionStatus", "paymentStatus"},
                new String[]{sessionId, String.valueOf(session.getSessionStatus()),
                        String.valueOf(session.getPaymentStatus())},
                new String[]{"H2"});
        // #endregion
        String currentStatus = session.getSessionStatus();
        boolean checkoutable = "ACTIVE".equalsIgnoreCase(currentStatus)
                || "PENDING_PAYMENT".equalsIgnoreCase(currentStatus);
        if (!checkoutable) {
            // #region agent log
            debugLog("626", "confirmExitAndCheckout: THROW SESSION_NOT_FOUND — not ACTIVE/PENDING_PAYMENT",
                    new String[]{"sessionId", "actualStatus"},
                    new String[]{sessionId, String.valueOf(currentStatus)},
                    new String[]{"H2"});
            // #endregion
            throw new BaseAPIException(ErrorCode.SESSION_NOT_FOUND, "Session is not active");
        }
        // #region agent log
        debugLog("634", "confirmExitAndCheckout: PASS guard — session is checkoutable",
                new String[]{"sessionId", "actualStatus", "wasTransitioned"},
                new String[]{sessionId, String.valueOf(currentStatus),
                        String.valueOf("PENDING_PAYMENT".equalsIgnoreCase(currentStatus))},
                new String[]{"H2"});
        // #endregion

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
        if ("PENDING_PAYMENT".equalsIgnoreCase(session.getSessionStatus())) {
            // #region agent log
            debugLog("664", "confirmExitAndCheckout: transition PENDING_PAYMENT → ACTIVE",
                    new String[]{"sessionId"},
                    new String[]{sessionId},
                    new String[]{"H2"});
            // #endregion
            session.setSessionStatus("ACTIVE");
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
        String plateNumber = PlateNormalizer.normalize(req.getPlateNumber());

        vehicleRepository.findByPlateNumberIgnoreCase(plateNumber).ifPresent(vehicle -> {
            if (parkingSessionRepository.existsByVehicleVehicleIdAndSessionStatus(vehicle.getVehicleId(), "ACTIVE")) {
                throw new BaseAPIException(ErrorCode.GUEST_ALREADY_PARKING,
                        "Vehicle " + plateNumber + " already has an active parking session");
            }
        });

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
        session.setSessionStatus("ACTIVE");
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
        String plateNumber = PlateNormalizer.normalize(req.getPlateNumber());

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
        final String finalPlateNumber = PlateNormalizer.normalize(plateNumber);

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
        List<ParkingSlot> availableSlots = parkingSlotRepository
                .findAvailableByBuildingAndVehicleType(req.getBuildingId(), req.getVehicleTypeId());
        if (availableSlots.isEmpty()) {
            throw new BaseAPIException(ErrorCode.SLOT_NOT_AVAILABLE,
                    "Không có slot trống nào cho loại xe " + vehicleType.getTypeName()
                            + " tại building này.");
        }
        ParkingSlot slot = availableSlots.get(0);

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
        // 1. Validate ticket tồn tại
        Ticket ticket = ticketRepository.findByTicketCode(req.getTicketCode())
                .orElseThrow(() -> new BaseAPIException(ErrorCode.TICKET_NOT_FOUND));

        // 2. Tìm session ACTIVE theo ticket
        ParkingSession session = parkingSessionRepository
                .findByTicketTicketIdAndSessionStatus(ticket.getTicketId(), "ACTIVE")
                .orElseThrow(() -> new BaseAPIException(ErrorCode.GUEST_SESSION_NOT_FOUND,
                        "Không tìm thấy session ACTIVE cho ticket: " + req.getTicketCode()));

        // 3. Verify đây là guest session (không có reservation)
        if (session.getReservation() != null) {
            throw new BaseAPIException(ErrorCode.INVALID_REQUEST,
                    "Đây là session của driver có reservation. Không dùng được luồng guest checkout.");
        }

        String buildingId = resolveBuildingId(session.getSlot());
        checkStaffBuildingAssignment(staffEmail, buildingId);

        // 4. OCR biển số lúc xe ra
        PlateRecognizerService.OcrResult ocr;
        try {
            ocr = ocrService.recognizeFromUpload(req.getPlateImage());
        } catch (Exception e) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Không thể đọc ảnh biển số: " + e.getMessage());
        }
        String scannedPlate = ocr.plateNumber();
        if (scannedPlate == null || ocr.confidence() < 0.3) {
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "Không nhận diện được biển số từ ảnh. Vui lòng chụp lại hoặc nhập tay.");
        }
        scannedPlate = scannedPlate.toUpperCase();

        // 5. Validate: biển số quét phải khớp với biển số trong session
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
        long minutes = Duration.between(session.getCheckinTime(), now).toMinutes();
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

        String paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH";
        boolean electronicPayment = "VNPAY".equals(paymentMethod) || "PAYOS".equals(paymentMethod) || "MOMO".equals(paymentMethod);

        session.setCheckoutTime(now);
        session.setTotalFee(total);
        session.setParkingDuration(hours);
        session.setSessionStatus("COMPLETED");
        session.setCheckoutImageUrl(req.getCheckoutImageUrl());

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
    private String resolvePlateNumber(MultipartFile plateImage) {
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
        return plateNumber;
    }

    // ======================== END QUICK CHECKIN FLOW ========================

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

    // #region agent log
    private static void debugLog(String location, String message,
                                 String[] dataKeys, String[] dataValues, String[] hypothesisIds) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"sessionId\":\"6b654b\",");
            sb.append("\"id\":\"log_").append(System.currentTimeMillis()).append("_").append(location.hashCode()).append("\",");
            sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
            sb.append("\"location\":\"ParkingSessionService.java:").append(location).append("\",");
            sb.append("\"message\":\"").append(escapeJson(message)).append("\",");
            sb.append("\"data\":{");
            for (int i = 0; i < dataKeys.length && i < dataValues.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(dataKeys[i])).append("\":\"")
                        .append(escapeJson(dataValues[i])).append("\"");
            }
            sb.append("},");
            sb.append("\"runId\":\"post-fix\",");
            sb.append("\"hypothesisId\":\"").append(String.join(",", hypothesisIds)).append("\"");
            sb.append("}");
            String jsonLine = sb.toString();
            java.nio.file.Path logPath = java.nio.file.Paths.get("debug-6b654b.log");
            java.nio.file.Files.writeString(logPath, jsonLine + "\n",
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    // #endregion

}
