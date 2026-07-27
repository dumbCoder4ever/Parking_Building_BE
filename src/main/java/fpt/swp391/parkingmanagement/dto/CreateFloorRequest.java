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

    public static final String MOTORBIKE_TYPE_ID = "33333333-3333-3333-3333-333333333331";
    public static final String CAR_TYPE_ID = "33333333-3333-3333-3333-333333333332";
    public static final String SUV_TYPE_ID = "33333333-3333-3333-3333-333333333333";
    public static final String TRUCK_TYPE_ID = "33333333-3333-3333-3333-333333333334";

    @NotBlank(message = "Floor name is required")
    @Schema(example = "Motorcycle floor")
    private String floorName;

    @NotBlank(message = "Vehicle type id is required")
    @JsonAlias("vehicle_type_id")
    @Schema(
            description = "Vehicle type UUID from GET /api/vehicles/types. "
                    + "Do not use buildingId from the URL.",
            example = MOTORBIKE_TYPE_ID,
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
