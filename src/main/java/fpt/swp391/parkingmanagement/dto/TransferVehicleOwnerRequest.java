package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TransferVehicleOwnerRequest {

    @NotBlank(message = "New owner userId is required")
    private String newUserId;
}
