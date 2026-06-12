package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {

    @NotBlank(message = "Plate number is required")
    @Pattern(regexp = "^[0-9]{2}[A-Z]-[0-9]{4,5}$", message = "Plate number must be in format XX-XXXXX or XX-XXXX")
    private String plateNumber;

    @NotBlank(message = "Vehicle type id is required")
    private String vehicleTypeId;

    private String vehicleColor;

    private String brand;

    private String model;
}
