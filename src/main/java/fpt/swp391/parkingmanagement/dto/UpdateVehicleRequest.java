package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class UpdateVehicleRequest {

    private String plateNumber;
    private String vehicleTypeId;
    private String vehicleColor;
    private String brand;
    private String model;
}
