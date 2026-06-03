package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class CheckinRequest {
    private String ticketCode;
    private String qrCode;
    private String plateNumber;
    private String vehicleColor;
    private String vehicleTypeId;
}
