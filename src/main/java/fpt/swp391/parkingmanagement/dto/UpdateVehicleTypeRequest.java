package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Update a vehicle type")
public class UpdateVehicleTypeRequest {

    @NotBlank(message = "Type name is required")
    @Schema(example = "SUV")
    private String typeName;

    @Schema(example = "LARGE")
    private String sizeCategory;

    @Schema(example = "7-seat SUV")
    private String description;
}
