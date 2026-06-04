package fpt.swp391.parkingmanagement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Create a floor. Each floor is assigned exactly one vehicle type.")
public class CreateFloorRequest {

    @NotBlank(message = "Floor name is required")
    @Schema(example = "Tầng xe máy")
    private String floorName;

    @NotBlank(message = "Vehicle type id is required")
    @JsonAlias("vehicle_type_id")
    @Schema(
            description = "Vehicle type for this floor. Get IDs from GET /api/manager/setup/vehicle-types",
            example = "00000000-0000-0000-0000-000000000001",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String vehicleTypeId;

    @NotNull(message = "Floor level is required")
    @Min(value = 1, message = "Floor level must be at least 1")
    @Schema(example = "1")
    private Integer floorLevel;

    @NotNull(message = "Max capacity is required")
    @Min(value = 1, message = "Floor max capacity must be at least 1")
    @Schema(example = "50")
    private Integer maxCapacity;
}
