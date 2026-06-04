package fpt.swp391.parkingmanagement.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.DriverSummaryResponse;
import fpt.swp391.parkingmanagement.dto.VehicleResponse;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.exception.ResourceNotFoundException;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManagerDriverService {

    private static final String DRIVER_ROLE = "ROLE_DRIVER";

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public List<DriverSummaryResponse> getAllDrivers() {
        return userRepository.findByRoleOrderByFullNameAsc(DRIVER_ROLE).stream()
                .map(DriverSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getDriverVehicles(String userId) {
        return getDriverVehicles(findDriverById(userId));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getDriverVehiclesByUsername(String username) {
        return getDriverVehicles(findDriverByUsername(username));
    }

    private List<VehicleResponse> getDriverVehicles(User driver) {
        return vehicleRepository.findByUserUserIdOrderByCreatedAtDesc(driver.getUserId()).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    private User findDriverById(String userId) {
        return userRepository.findById(userId)
                .filter(this::isDriver)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + userId));
    }

    private User findDriverByUsername(String username) {
        return userRepository.findByUsername(username.trim())
                .filter(this::isDriver)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + username));
    }

    private boolean isDriver(User user) {
        return DRIVER_ROLE.equals(user.getRole());
    }
}
