package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Quick checkin: staff scans a license plate image. The system OCRs the plate, finds a reservation (driver) or auto-assigns a slot (guest), then creates a session.")
public class QuickCheckinRequest {

    @Schema(description = "License plate image. Required unless plateNumber is already provided from a prior OCR step.")
    private MultipartFile plateImage;

    @Schema(description = "Pre-recognized plate number from FE OCR preview. When set, check-in skips a second OCR call.")
    private String plateNumber;

    @Schema(description = "Building ID where the staff is working. Required. Used to auto-assign slots (guest) and validate reservations (driver).")
    @NotBlank(message = "buildingId is required")
    private String buildingId;

    @Schema(description = "Vehicle type for guest checkin when no reservation is found. Required if the plate has no reservation.")
    private String vehicleTypeId;

    private String vehicleColor;
    private String guestName;
    private String guestPhone;
    private String note;

    @Schema(description = "Optional force mode: GUEST skips driver auto-detect when staff chose Guest Walk-in on FE.")
    private String mode;

    /** Set programmatically after Cloudinary upload — not sent by client */
    private String checkinVehicleImage;
}
