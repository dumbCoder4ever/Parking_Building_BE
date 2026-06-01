package fpt.swp391.parkingmanagement.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import fpt.swp391.parkingmanagement.dto.CreateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.UpdateVehicleRequest;
import fpt.swp391.parkingmanagement.dto.VehicleResponse;
import fpt.swp391.parkingmanagement.dto.VehicleTypeOptionResponse;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.exception.DuplicateResourceException;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
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
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    @Transactional(readOnly = true)
    public List<VehicleTypeOptionResponse> getVehicleTypeOptions() {
        return vehicleTypeRepository.findAll().stream()
                .map(vt -> VehicleTypeOptionResponse.builder()
                        .vehicleTypeId(vt.getVehicleTypeId())
                        .typeName(vt.getTypeName())
                        .sizeCategory(vt.getSizeCategory())
                        .description(vt.getDescription())
                        .build())
                .toList();
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
    public List<VehicleResponse> searchVehicles(String plateNumber) {
        List<Vehicle> vehicles = StringUtils.hasText(plateNumber)
                ? vehicleRepository.findByPlateNumberContainingIgnoreCaseOrderByCreatedAtDesc(
                        normalizeText(plateNumber))
                : vehicleRepository.findAllByOrderByCreatedAtDesc();
        return vehicles.stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(String vehicleId) {
        return VehicleResponse.from(findVehicle(vehicleId));
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
        return vehicleTypeRepository.findById(normalizeText(vehicleTypeId))
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found: " + vehicleTypeId));
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
        if (reservationRepository.existsByVehicleVehicleIdAndReservationStatusIn(
                vehicleId, ACTIVE_RESERVATION_STATUSES)) {
            throw new RuntimeException("Cannot delete vehicle with active reservations");
        }
        if (parkingSessionRepository.existsByVehicleVehicleIdAndSessionStatus(vehicleId, "ACTIVE")) {
            throw new RuntimeException("Cannot delete vehicle with an active parking session");
        }
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
}
