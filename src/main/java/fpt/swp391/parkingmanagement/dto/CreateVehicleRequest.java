package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateVehicleRequest {

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotBlank(message = "Vehicle type id is required")
    private String vehicleTypeId;

    private String vehicleColor;
    private String brand;
    private String model;

    /** Optional vehicle photo uploaded by driver (multipart). */
    private MultipartFile image;
}
