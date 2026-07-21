package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyVehicleResponse {
    private String incidentId;
    private String verificationResult;
    private String sessionPlateNumber;
    private String sessionTicketCode;
    private String providedPlateNumber;
    private String providedTicketCode;
    private String message;
}