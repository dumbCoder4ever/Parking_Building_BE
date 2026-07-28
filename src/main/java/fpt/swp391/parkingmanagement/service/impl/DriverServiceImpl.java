package fpt.swp391.parkingmanagement.service.impl;

import fpt.swp391.parkingmanagement.dto.*;
import fpt.swp391.parkingmanagement.entity.*;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.*;
import fpt.swp391.parkingmanagement.service.CloudinaryService;
import fpt.swp391.parkingmanagement.service.DriverService;
import fpt.swp391.parkingmanagement.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final PaymentRepository paymentRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final PricingService pricingService;
    private final CloudinaryService cloudinaryService;

    @Override
    public DriverProfileResponse getDriverProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        int totalVehicles = vehicleRepository.countByUserUserId(user.getUserId());
        int totalReservations = reservationRepository.countByUserUserId(user.getUserId());
        int totalSessions = parkingSessionRepository.countByReservationUserUserId(user.getUserId());

        return DriverProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .totalVehicles(totalVehicles)
                .totalReservations(totalReservations)
                .totalSessions(totalSessions)
                .build();
    }

    @Override
    @Transactional
    public DriverProfileResponse updateDriverProfile(String email, DriverProfileResponse request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        userRepository.save(user);
        return getDriverProfile(email);
    }

    @Override
    public List<VehicleResponse> getMyVehicles(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        return vehicleRepository.findByUserUserId(user.getUserId())
                .stream()
                .map(VehicleResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VehicleResponse addVehicle(String email, VehicleRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        if (vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw new BaseAPIException(ErrorCode.VEHICLE_ALREADY_EXISTS);
        }

        Vehicle vehicle = Vehicle.builder()
                .user(user)
                .plateNumber(request.getPlateNumber().toUpperCase())
                .vehicleColor(request.getVehicleColor())
                .brand(request.getBrand())
                .model(request.getModel())
                .status("ACTIVE")
                .build();

        if (request.getVehicleTypeId() != null) {
            VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                    .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND));
            vehicle.setVehicleType(vehicleType);
        }

        if (request.getImage() != null && !request.getImage().isEmpty()) {
            vehicle.setImageUrl(cloudinaryService.uploadVehicleImage(request.getImage()));
        }

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponse.from(saved);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(String email, String vehicleId, VehicleRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_NOT_FOUND));

        if (!vehicle.getUser().getUserId().equals(user.getUserId())) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED);
        }

        if (request.getPlateNumber() != null) {
            String newPlate = request.getPlateNumber().toUpperCase();
            if (!newPlate.equals(vehicle.getPlateNumber()) && 
                vehicleRepository.existsByPlateNumber(newPlate)) {
                throw new BaseAPIException(ErrorCode.VEHICLE_ALREADY_EXISTS);
            }
            vehicle.setPlateNumber(newPlate);
        }

        if (request.getVehicleColor() != null) {
            vehicle.setVehicleColor(request.getVehicleColor());
        }
        if (request.getBrand() != null) {
            vehicle.setBrand(request.getBrand());
        }
        if (request.getModel() != null) {
            vehicle.setModel(request.getModel());
        }
        if (request.getVehicleTypeId() != null) {
            VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                    .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_TYPE_NOT_FOUND));
            vehicle.setVehicleType(vehicleType);
        }
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            vehicle.setImageUrl(cloudinaryService.uploadVehicleImage(request.getImage()));
        }

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteVehicle(String email, String vehicleId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.VEHICLE_NOT_FOUND));

        if (!vehicle.getUser().getUserId().equals(user.getUserId())) {
            throw new BaseAPIException(ErrorCode.UNAUTHORIZED);
        }

        vehicle.setStatus("DELETED");
        vehicleRepository.save(vehicle);
    }

    @Override
    public List<DriverSessionHistoryResponse> getMyParkingHistory(String email, int limit) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        return parkingSessionRepository.findByUserIdOrderByCheckInTimeDesc(user.getUserId(), org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(this::mapToSessionHistory)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DriverCurrentSessionsResponse getMyCurrentSessions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        List<ParkingSession> sessions = parkingSessionRepository.findAllActiveByUserId(user.getUserId());
        if (sessions.isEmpty()) {
            throw new BaseAPIException(ErrorCode.SESSION_NOT_FOUND, "No active parking session found");
        }

        LocalDateTime now = LocalDateTime.now();
        List<DriverCurrentSessionResponse> sessionResponses = sessions.stream()
                .map(session -> mapToCurrentSession(session, now))
                .collect(Collectors.toList());

        return DriverCurrentSessionsResponse.builder()
                .totalActiveSessions(sessionResponses.size())
                .sessions(sessionResponses)
                .build();
    }

    private DriverCurrentSessionResponse mapToCurrentSession(ParkingSession session, LocalDateTime now) {
        long minutes = session.getCheckinTime() != null
                ? Duration.between(session.getCheckinTime(), now).toMinutes()
                : 0;
        int parkingMinutes = (int) Math.max(0, minutes);
        int parkingHours = Math.max(1, (int) Math.ceil(parkingMinutes / 60.0));

        Reservation reservation = session.getReservation();
        // Walk-in session: vehicle từ session.vehicle (reservation = null)
        // Reservation session: vehicle từ reservation.vehicle
        Vehicle vehicle = session.getVehicle() != null ? session.getVehicle() : (reservation != null ? reservation.getVehicle() : null);
        LocalDateTime reservationStart = reservation != null ? reservation.getReservationStart() : null;

        String buildingId = null, buildingName = null, floorId = null, floorName = null,
               zoneId = null, zoneName = null, slotId = null, slotName = null;
        if (session.getSlot() != null) {
            slotId = session.getSlot().getSlotId();
            slotName = session.getSlot().getSlotName();
            if (session.getSlot().getZone() != null) {
                zoneId = session.getSlot().getZone().getZoneId();
                zoneName = session.getSlot().getZone().getZoneName();
                if (session.getSlot().getZone().getFloor() != null) {
                    floorId = session.getSlot().getZone().getFloor().getFloorId();
                    floorName = session.getSlot().getZone().getFloor().getFloorName();
                    if (session.getSlot().getZone().getFloor().getBuilding() != null) {
                        buildingId = session.getSlot().getZone().getFloor().getBuilding().getBuildingId();
                        buildingName = session.getSlot().getZone().getFloor().getBuilding().getBuildingName();
                    }
                }
            }
        }

        BigDecimal estimatedFee = BigDecimal.ZERO;
        BigDecimal basePrice = null;
        BigDecimal hourlyRate = null;
        String vehicleTypeId = null, vehicleTypeName = null;

        if (vehicle != null && vehicle.getVehicleType() != null) {
            vehicleTypeId = vehicle.getVehicleType().getVehicleTypeId();
            vehicleTypeName = vehicle.getVehicleType().getTypeName();
            estimatedFee = pricingService.calculateFee(vehicleTypeId, parkingHours);
            
            var policy = pricingService.getActivePolicy(vehicleTypeId);
            if (policy != null) {
                basePrice = policy.getBasePrice();
                hourlyRate = policy.getHourlyRate();
            }
        }

        return DriverCurrentSessionResponse.builder()
                .sessionId(session.getSessionId())
                .ticketCode(session.getTicket() != null ? session.getTicket().getTicketCode() : null)
                .buildingId(buildingId)
                .buildingName(buildingName)
                .floorId(floorId)
                .floorName(floorName)
                .zoneId(zoneId)
                .zoneName(zoneName)
                .slotId(slotId)
                .slotName(slotName)
                .vehiclePlate(vehicle != null ? vehicle.getPlateNumber() : null)
                .vehicleColor(vehicle != null ? vehicle.getVehicleColor() : null)
                .vehicleBrand(vehicle != null ? vehicle.getBrand() : null)
                .vehicleModel(vehicle != null ? vehicle.getModel() : null)
                .vehicleTypeId(vehicleTypeId)
                .vehicleTypeName(vehicleTypeName)
                .checkinTime(session.getCheckinTime())
                .currentTime(now)
                .parkingMinutes(parkingMinutes)
                .parkingHours(parkingHours)
                .reservationStart(reservationStart)
                .sessionStatus(session.getSessionStatus())
                .paymentStatus(session.getPaymentStatus())
                .basePrice(basePrice)
                .hourlyRate(hourlyRate)
                .estimatedFee(estimatedFee)
                .build();
    }

    @Override
    public List<PaymentResponse> getMyPaymentHistory(String email, int limit) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        return paymentRepository.findByUserIdOrderByPaymentTimeDesc(user.getUserId(), org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(PaymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public DriverStatsResponse getDriverStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseAPIException(ErrorCode.USER_NOT_FOUND));

        int totalReservations = reservationRepository.countByUserUserId(user.getUserId());
        int totalVehicles = vehicleRepository.countByUserUserId(user.getUserId());
        int totalCompletedSessions = parkingSessionRepository.countCompletedByUserId(user.getUserId());
        int totalActiveSessions = parkingSessionRepository.countActiveByUserId(user.getUserId());

        Double totalSpent = paymentRepository.sumTotalAmountByUserId(user.getUserId());
        Long totalMinutes = parkingSessionRepository.sumDurationMinutesByUserId(user.getUserId());

        var firstReservation = reservationRepository.findFirstByUserUserIdOrderByCreatedAtAsc(user.getUserId()).orElse(null);
        var lastSession = parkingSessionRepository.findLastByUserIdOrderByCheckInTimeDesc(user.getUserId()).orElse(null);

        double avgDuration = totalCompletedSessions > 0 && totalMinutes != null 
                ? (double) totalMinutes / totalCompletedSessions 
                : 0;

        return DriverStatsResponse.builder()
                .totalReservations(totalReservations)
                .totalCompletedSessions(totalCompletedSessions)
                .totalActiveSessions(totalActiveSessions)
                .totalVehicles(totalVehicles)
                .totalSpent(totalSpent != null ? totalSpent : 0)
                .totalHoursParked(totalMinutes != null ? (int) (totalMinutes / 60) : 0)
                .firstReservationDate(firstReservation != null ? firstReservation.getCreatedAt() : null)
                .lastSessionDate(lastSession != null ? lastSession.getCheckinTime() : null)
                .averageSessionDurationMinutes(avgDuration)
                .build();
    }

    private DriverSessionHistoryResponse mapToSessionHistory(ParkingSession session) {
        Reservation reservation = session.getReservation();
        Vehicle vehicle = reservation != null ? reservation.getVehicle() : null;

        String buildingName = null, floorName = null, slotName = null;
        if (session.getSlot() != null) {
            slotName = session.getSlot().getSlotName();
            if (session.getSlot().getZone() != null) {
                floorName = session.getSlot().getZone().getFloor() != null 
                        ? session.getSlot().getZone().getFloor().getFloorName() : null;
                if (session.getSlot().getZone().getFloor() != null &&
                    session.getSlot().getZone().getFloor().getBuilding() != null) {
                    buildingName = session.getSlot().getZone().getFloor().getBuilding().getBuildingName();
                }
            }
        }

        Long durationMinutes = null;
        if (session.getCheckinTime() != null && session.getCheckoutTime() != null) {
            durationMinutes = Duration.between(session.getCheckinTime(), session.getCheckoutTime()).toMinutes();
        } else if (session.getCheckinTime() != null) {
            durationMinutes = Duration.between(session.getCheckinTime(), java.time.LocalDateTime.now()).toMinutes();
        }

        return DriverSessionHistoryResponse.builder()
                .sessionId(session.getSessionId())
                .reservationCode(reservation != null ? reservation.getReservationCode() : null)
                .buildingName(buildingName)
                .floorName(floorName)
                .slotName(slotName)
                .vehiclePlate(vehicle != null ? vehicle.getPlateNumber() : null)
                .vehicleColor(vehicle != null ? vehicle.getVehicleColor() : null)
                .vehicleBrand(vehicle != null ? vehicle.getBrand() : null)
                .checkInTime(session.getCheckinTime())
                .checkOutTime(session.getCheckoutTime())
                .durationMinutes(durationMinutes)
                .status(session.getSessionStatus())
                .paymentStatus(session.getPaymentStatus())
                .totalFee(session.getTotalFee() != null ? session.getTotalFee().doubleValue() : null)
                .entryType(null)
                .build();
    }

}
