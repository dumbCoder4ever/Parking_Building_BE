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
    private String checkinImageUrl;
}
