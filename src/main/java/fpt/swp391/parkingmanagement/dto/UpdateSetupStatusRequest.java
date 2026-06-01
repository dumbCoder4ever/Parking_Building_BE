package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSetupStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
