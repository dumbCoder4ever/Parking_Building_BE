package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GuestCheckinRequest {

    /** Set programmatically from OCR result */
    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotBlank(message = "Slot ID is required")
    private String slotId;

    private String note;

    /** Set programmatically after Cloudinary upload */
    private String checkinImageUrl;
}
