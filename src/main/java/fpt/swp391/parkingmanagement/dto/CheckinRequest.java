package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckinRequest {

    @NotBlank(message = "Ticket code is required")
    private String ticketCode;

    private String qrCode;
    private String plateNumber;
    private String vehicleColor;
    private String vehicleTypeId;

    @NotBlank(message = "Building ID is required for staff check-in")
    private String buildingId;
}
