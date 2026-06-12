package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update zone information and adjust the number of parking slots.")
public class UpdateZoneRequest {

    @NotBlank(message = "Zone name is required")
    @Schema(example = "Khu A")
    private String zoneName;

    @NotNull(message = "Zone max capacity is required")
    @Min(value = 1, message = "Zone max capacity must be at least 1")
    @Schema(description = "Target number of slots in this zone.", example = "20")
    private Integer maxCapacity;

    @Schema(
            description = "Prefix for new slots when increasing capacity (e.g. A -> A-1, A-2). "
                    + "If omitted, the prefix is derived from existing slots.",
            example = "A")
    private String slotPrefix;
}
