package fpt.swp391.parkingmanagement.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateVehicleRequest {

    private String plateNumber;
    private String vehicleTypeId;
    private String vehicleColor;
    private String brand;
    private String model;

    /** Optional new vehicle photo (multipart). Replaces existing imageUrl when provided. */
    private MultipartFile image;
}
