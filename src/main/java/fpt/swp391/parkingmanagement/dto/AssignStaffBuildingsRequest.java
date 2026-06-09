package fpt.swp391.parkingmanagement.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(description = "Assign a staff user to multiple buildings")
public class AssignStaffBuildingsRequest {

    @NotEmpty(message = "At least one building id is required")
    @Schema(description = "Building ids to assign. Replaces existing assignments for this staff.")
    private List<String> buildingIds;
}
