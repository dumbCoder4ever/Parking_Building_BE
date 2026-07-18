package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;

import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleResponse {

    private String vehicleId;
    private String userId;
    private String username;
    private String ownerFullName;
    private String plateNumber;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private String vehicleColor;
    private String brand;
    private String model;
    private String imageUrl;
    private String status;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String checkinVehicleImage;
    private String checkoutVehicleImage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VehicleResponse from(Vehicle vehicle) {
        VehicleType vehicleType = vehicle.getVehicleType();
        return VehicleResponse.builder()
                .vehicleId(vehicle.getVehicleId())
                .userId(vehicle.getUser() != null ? vehicle.getUser().getUserId() : null)
                .username(vehicle.getUser() != null ? vehicle.getUser().getUsername() : null)
                .ownerFullName(vehicle.getUser() != null ? vehicle.getUser().getFullName() : null)
                .plateNumber(vehicle.getPlateNumber())
                .vehicleTypeId(vehicleType != null ? vehicleType.getVehicleTypeId() : null)
                .vehicleTypeName(vehicleType != null ? vehicleType.getTypeName() : null)
                .vehicleColor(vehicle.getVehicleColor())
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .imageUrl(vehicle.getImageUrl())
                .status(vehicle.getStatus())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }

    public VehicleResponse withParkingTimes(LocalDateTime checkInTime, LocalDateTime checkOutTime,
            String checkinVehicleImage, String checkoutVehicleImage) {
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.checkinVehicleImage = checkinVehicleImage;
        this.checkoutVehicleImage = checkoutVehicleImage;
        return this;
    }
}
