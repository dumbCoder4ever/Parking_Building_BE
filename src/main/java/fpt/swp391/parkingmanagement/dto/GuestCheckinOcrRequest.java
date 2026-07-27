package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Guest checkin via OCR: staff scans a license plate image, the system detects the plate, auto-assigns a slot, and creates a session.")
public class GuestCheckinOcrRequest {

    @Schema(description = "License plate image. Required.")
    private MultipartFile plateImage;

    @Schema(description = "Building ID where the staff is working. Required.")
    @NotBlank(message = "buildingId is required")
    private String buildingId;

    @Schema(description = "Vehicle type (MOTORCYCLE, CAR, etc.). Required.")
    @NotBlank(message = "vehicleTypeId is required")
    private String vehicleTypeId;

    @Schema(description = "Vehicle color (optional).")
    private String vehicleColor;

    @Schema(description = "Vehicle brand (optional).")
    private String brand;

    @Schema(description = "Vehicle model (optional).")
    private String model;

    @Schema(description = "Guest name (optional).")
    private String guestName;

    @Schema(description = "Guest phone number (optional).")
    private String guestPhone;

    @Schema(description = "Note (optional).")
    private String note;

    @Schema(description = "Check-in vehicle image (optional). Upload image and store URL here.")
    private MultipartFile checkinImage;

    /** Set programmatically after Cloudinary upload */
    private String checkinVehicleImage;
}
