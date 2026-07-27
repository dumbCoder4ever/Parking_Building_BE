package fpt.swp391.parkingmanagement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update floor information including vehicle type.")
public class UpdateFloorRequest {

    @NotBlank(message = "Floor name is required")
    @Schema(example = "Motorcycle floor")
    private String floorName;

    @NotBlank(message = "Vehicle type id is required")
    @JsonAlias("vehicle_type_id")
    @Schema(
            description = "Vehicle type UUID from GET /api/vehicles/types.",
            example = CreateFloorRequest.MOTORBIKE_TYPE_ID,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String vehicleTypeId;

    @NotNull(message = "Max capacity is required")
    @Min(value = 1, message = "Floor max capacity must be at least 1")
    @Schema(example = "50")
    private Integer maxCapacity;
}
