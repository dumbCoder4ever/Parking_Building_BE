package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckinRequest {

    @NotBlank(message = "Ticket code is required")
    private String ticketCode;
    private String plateNumber;
    private String vehicleColor;
    private String vehicleTypeId;
    // Set programmatically after Cloudinary upload — not sent by client
    private String checkinImageUrl;
    private String guestName;
    private String guestPhone;
    private String note;
}
