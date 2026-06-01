package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateZoneRequest {

    @NotBlank(message = "Zone name is required")
    private String zoneName;

    @NotNull(message = "Zone max capacity is required")
    @Min(value = 1, message = "Zone max capacity must be at least 1")
    private Integer maxCapacity;

    @NotBlank(message = "Slot prefix is required")
    private String slotPrefix;
}
