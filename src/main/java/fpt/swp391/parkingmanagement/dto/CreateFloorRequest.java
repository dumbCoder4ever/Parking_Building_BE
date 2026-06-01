package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFloorRequest {

    @NotBlank(message = "Floor name is required")
    private String floorName;

    @NotNull(message = "Floor level is required")
    @Min(value = 1, message = "Floor level must be at least 1")
    private Integer floorLevel;

    @NotNull(message = "Max capacity is required")
    @Min(value = 1, message = "Floor max capacity must be at least 1")
    private Integer maxCapacity;
}
