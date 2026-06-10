package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateReservationStatusRequest {

    @NotBlank
    private String status;

    private String note;
}
