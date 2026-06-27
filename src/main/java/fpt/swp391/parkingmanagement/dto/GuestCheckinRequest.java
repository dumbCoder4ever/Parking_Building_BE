package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GuestCheckinRequest {

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleTypeId;

    private String vehicleColor;
    private String brand;
    private String model;

    @NotBlank(message = "Slot ID is required")
    private String slotId;

    private String guestName;
    private String guestPhone;
    private String note;

    /** Set programmatically after Cloudinary upload */
    private String checkinImageUrl;
}
