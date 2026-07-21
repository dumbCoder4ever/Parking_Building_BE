package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSystemConfigRequest {

    @NotBlank(message = "configValue is required")
    private String configValue;

    private String description;
}
