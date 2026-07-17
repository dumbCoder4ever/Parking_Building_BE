package fpt.swp391.parkingmanagement.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import fpt.swp391.parkingmanagement.dto.CreateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.CreateVehicleTypeRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleTypeRequest;
import fpt.swp391.parkingmanagement.dto.VehicleResponse;
import fpt.swp391.parkingmanagement.dto.VehicleTypeOptionResponse;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.VehicleRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final Set<String> DRIVER_VEHICLE_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> MANAGER_VEHICLE_STATUSES = Set.of("ACTIVE", "INACTIVE", "BLOCKED");
    private static final Set<String> ACTIVE_RESERVATION_STATUSES = Set.of("PENDING", "APPROVED");

    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleTypeCacheService vehicleTypeCacheService;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final FloorRepository floorRepository;
    private final PricingPolicyRepository pricingPolicyRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<VehicleTypeOptionResponse> getVehicleTypeOptions() {
        return vehicleTypeCacheService.findAll().stream()
                .map(this::toVehicleTypeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleTypeOptionResponse getVehicleType(String vehicleTypeId) {
        return toVehicleTypeResponse(findVehicleType(vehicleTypeId));
    }

    @Transactional
    public VehicleTypeOptionResponse createVehicleType(CreateVehicleTypeRequest request) {
        String typeName = normalizeText(request.getTypeName());
        if (vehicleTypeRepository.existsByTypeNameIgnoreCase(typeName)) {
            throw new DuplicateResourceException("Vehicle type already exists: " + typeName);
        }

        VehicleType vehicleType = new VehicleType();
        vehicleType.setTypeName(typeName);
        vehicleType.setSizeCategory(normalizeText(request.getSizeCategory()));
        vehicleType.setDescription(normalizeText(request.getDescription()));
        VehicleTypeOptionResponse response = toVehicleTypeResponse(vehicleTypeRepository.save(vehicleType));
        vehicleTypeCacheService.evictAll();
        return response;
    }

    @Transactional
    public VehicleTypeOptionResponse updateVehicleType(String vehicleTypeId, UpdateVehicleTypeRequest request) {
        VehicleType vehicleType = findVehicleType(vehicleTypeId);
        String typeName = normalizeText(request.getTypeName());

        if (vehicleTypeRepository.existsByTypeNameIgnoreCaseAndVehicleTypeIdNot(typeName, vehicleTypeId)) {
            throw new DuplicateResourceException("Vehicle type already exists: " + typeName);
        }

        vehicleType.setTypeName(typeName);
        vehicleType.setSizeCategory(normalizeText(request.getSizeCategory()));
        vehicleType.setDescription(normalizeText(request.getDescription()));
        VehicleTypeOptionResponse response = toVehicleTypeResponse(vehicleTypeRepository.save(vehicleType));
        vehicleTypeCacheService.evictAll();
        return response;
    }

    @Transactional
    public void deleteVehicleType(String vehicleTypeId) {
        VehicleType vehicleType = findVehicleType(vehicleTypeId);
        validateVehicleTypeNotInUse(vehicleTypeId);
        vehicleTypeRepository.delete(vehicleType);
        vehicleTypeCacheService.evictAll();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(String email) {
        User user = findUserByEmail(email);
        return vehicleRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getMyVehicle(String email, String vehicleId) {
        User user = findUserByEmail(email);
        return VehicleResponse.from(findOwnedVehicle(user.getUserId(), vehicleId));
    }

    @Transactional
    public VehicleResponse createMyVehicle(String email, CreateVehicleRequest request) {
        User user = findUserByEmail(email);
        String plateNumber = normalizePlateNumber(request.getPlateNumber());

        if (vehicleRepository.existsByPlateNumberIgnoreCase(plateNumber)) {
            throw new DuplicateResourceException("Plate number already registered: " + plateNumber);
        }

        VehicleType vehicleType = findVehicleType(request.getVehicleTypeId());

        Vehicle vehicle = new Vehicle();
        vehicle.setUser(user);
        vehicle.setVehicleType(vehicleType);
        vehicle.setPlateNumber(plateNumber);
        vehicle.setVehicleColor(normalizeText(request.getVehicleColor()));
        vehicle.setBrand(normalizeText(request.getBrand()));
        vehicle.setModel(normalizeText(request.getModel()));
        vehicle.setStatus("ACTIVE");

        if (request.getImage() != null && !request.getImage().isEmpty()) {
            vehicle.setImageUrl(cloudinaryService.uploadVehicleImage(request.getImage()));
        }

        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse updateMyVehicle(String email, String vehicleId, UpdateVehicleRequest request) {
        User user = findUserByEmail(email);
        Vehicle vehicle = findOwnedVehicle(user.getUserId(), vehicleId);

        if (StringUtils.hasText(request.getPlateNumber())) {
            String plateNumber = normalizePlateNumber(request.getPlateNumber());
            if (vehicleRepository.existsByPlateNumberIgnoreCaseAndVehicleIdNot(plateNumber, vehicleId)) {
                throw new DuplicateResourceException("Plate number already registered: " + plateNumber);
            }
            vehicle.setPlateNumber(plateNumber);
        }

        if (StringUtils.hasText(request.getVehicleTypeId())) {
            vehicle.setVehicleType(findVehicleType(request.getVehicleTypeId()));
        }

        if (request.getVehicleColor() != null) {
            vehicle.setVehicleColor(normalizeText(request.getVehicleColor()));
        }
        if (request.getBrand() != null) {
            vehicle.setBrand(normalizeText(request.getBrand()));
        }
        if (request.getModel() != null) {
            vehicle.setModel(normalizeText(request.getModel()));
        }
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            vehicle.setImageUrl(cloudinaryService.uploadVehicleImage(request.getImage()));
        }
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse updateMyVehicleStatus(String email, String vehicleId, String status) {
        User user = findUserByEmail(email);
        Vehicle vehicle = findOwnedVehicle(user.getUserId(), vehicleId);
        vehicle.setStatus(validateStatus(status, DRIVER_VEHICLE_STATUSES));
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void deleteMyVehicle(String email, String vehicleId) {
        User user = findUserByEmail(email);
        Vehicle vehicle = findOwnedVehicle(user.getUserId(), vehicleId);
        ensureVehicleCanBeRemoved(vehicleId);
        vehicleRepository.delete(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> searchVehicles(
            String plateNumber,
            String status,
            String userId,
            String username,
            String ownerFullName,
            String vehicleTypeId,
            Boolean parked,
            LocalDateTime checkInFrom,
            LocalDateTime checkInTo) {
        List<Vehicle> vehicles = vehicleRepository.searchForManager(
                normalizeOptionalText(plateNumber),
                normalizeOptionalStatus(status),
                normalizeOptionalText(userId),
                normalizeOptionalText(username),
                normalizeOptionalText(ownerFullName),
                normalizeOptionalText(vehicleTypeId),
                parked,
                checkInFrom,
                checkInTo);

        if (vehicles.isEmpty()) {
            return List.of();
        }

        List<String> vehicleIds = vehicles.stream().map(Vehicle::getVehicleId).toList();
        Map<String, ParkingSession> activeByVehicle = parkingSessionRepository
                .findActiveByVehicleIds(vehicleIds)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getVehicle().getVehicleId(),
                        Function.identity(),
                        (a, b) -> a.getCheckinTime() != null
                                && b.getCheckinTime() != null
                                && a.getCheckinTime().isAfter(b.getCheckinTime()) ? a : b));

        Map<String, ParkingSession> latestByVehicle = Map.of();
        List<String> missingLatestIds = vehicleIds.stream()
                .filter(id -> !activeByVehicle.containsKey(id))
                .toList();
        if (!missingLatestIds.isEmpty()) {
            latestByVehicle = parkingSessionRepository.findLatestByVehicleIds(missingLatestIds).stream()
                    .collect(Collectors.toMap(
                            s -> s.getVehicle().getVehicleId(),
                            Function.identity(),
                            (a, b) -> a));
        }

        List<VehicleResponse> result = new ArrayList<>(vehicles.size());
        for (Vehicle vehicle : vehicles) {
            result.add(toManagerVehicleResponse(
                    vehicle,
                    activeByVehicle.get(vehicle.getVehicleId()),
                    latestByVehicle.get(vehicle.getVehicleId())));
        }
        return result;
    }

    @Transactional
    public VehicleResponse transferVehicleOwner(String vehicleId, String newUserId) {
        Vehicle vehicle = findVehicle(vehicleId);
        User newOwner = userRepository.findById(normalizeText(newUserId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + newUserId));

        if (newOwner.getRole() == null || !newOwner.getRole().toUpperCase().contains("DRIVER")) {
            throw new RuntimeException("New owner must be a driver account");
        }

        if (vehicle.getUser() != null && vehicle.getUser().getUserId().equals(newOwner.getUserId())) {
            return toManagerVehicleResponse(vehicle);
        }

        ensureVehicleCanBeTransferred(vehicleId);
        vehicle.setUser(newOwner);
        return toManagerVehicleResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse updateVehicleStatus(String vehicleId, String status) {
        Vehicle vehicle = findVehicle(vehicleId);
        vehicle.setStatus(validateStatus(status, MANAGER_VEHICLE_STATUSES));
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public Vehicle getOwnedActiveVehicle(String email, String vehicleId) {
        User user = findUserByEmail(email);
        Vehicle vehicle = findOwnedVehicle(user.getUserId(), vehicleId);
        if (!"ACTIVE".equals(vehicle.getStatus())) {
            throw new RuntimeException("Vehicle is not active");
        }
        return vehicle;
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private VehicleType findVehicleType(String vehicleTypeId) {
        return vehicleTypeCacheService.findById(normalizeText(vehicleTypeId))
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found: " + vehicleTypeId));
    }

    private void validateVehicleTypeNotInUse(String vehicleTypeId) {
        if (floorRepository.existsByVehicleTypeVehicleTypeId(vehicleTypeId)) {
            throw new RuntimeException("Cannot delete vehicle type that is assigned to floors");
        }
        if (vehicleRepository.existsByVehicleTypeVehicleTypeId(vehicleTypeId)) {
            throw new RuntimeException("Cannot delete vehicle type that is used by registered vehicles");
        }
        if (pricingPolicyRepository.existsByVehicleTypeVehicleTypeId(vehicleTypeId)) {
            throw new RuntimeException("Cannot delete vehicle type that is used by pricing policies");
        }
    }

    private VehicleTypeOptionResponse toVehicleTypeResponse(VehicleType vehicleType) {
        return VehicleTypeOptionResponse.builder()
                .vehicleTypeId(vehicleType.getVehicleTypeId())
                .typeName(vehicleType.getTypeName())
                .sizeCategory(vehicleType.getSizeCategory())
                .description(vehicleType.getDescription())
                .build();
    }

    private Vehicle findVehicle(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private Vehicle findOwnedVehicle(String userId, String vehicleId) {
        return vehicleRepository.findByVehicleIdAndUserUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
    }

    private void ensureVehicleCanBeRemoved(String vehicleId) {
        ensureVehicleCanBeTransferred(vehicleId);
    }

    private void ensureVehicleCanBeTransferred(String vehicleId) {
        if (reservationRepository.existsByVehicleVehicleIdAndReservationStatusIn(
                vehicleId, ACTIVE_RESERVATION_STATUSES)) {
            throw new RuntimeException("Cannot transfer vehicle with active reservations");
        }
        if (parkingSessionRepository.existsByVehicleVehicleIdAndSessionStatus(vehicleId, "ACTIVE")) {
            throw new RuntimeException("Cannot transfer vehicle with an active parking session");
        }
    }

    private VehicleResponse toManagerVehicleResponse(Vehicle vehicle) {
        var activeSession = parkingSessionRepository.findActiveByVehicleId(vehicle.getVehicleId());
        var latestSession = activeSession.isEmpty()
                ? parkingSessionRepository.findLatestByVehicleId(vehicle.getVehicleId()).orElse(null)
                : null;
        return toManagerVehicleResponse(vehicle, activeSession.orElse(null), latestSession);
    }

    private VehicleResponse toManagerVehicleResponse(
            Vehicle vehicle, ParkingSession activeSession, ParkingSession latestSession) {
        VehicleResponse response = VehicleResponse.from(vehicle);
        if (activeSession != null) {
            return response.withParkingTimes(
                    activeSession.getCheckinTime(), null,
                    activeSession.getCheckinImageUrl(), null);
        }
        if (latestSession != null) {
            return response.withParkingTimes(
                    latestSession.getCheckinTime(), latestSession.getCheckoutTime(),
                    latestSession.getCheckinImageUrl(), latestSession.getCheckoutImageUrl());
        }
        return response;
    }

    private String validateStatus(String status, Set<String> allowedValues) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || !allowedValues.contains(normalized)) {
            throw new RuntimeException("Invalid status. Allowed values: " + String.join(", ", allowedValues));
        }
        return normalized;
    }

    private String normalizePlateNumber(String value) {
        return normalizeText(value).toUpperCase();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? normalizeText(value) : null;
    }

    private String normalizeOptionalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return validateStatus(status, MANAGER_VEHICLE_STATUSES);
    }
}
