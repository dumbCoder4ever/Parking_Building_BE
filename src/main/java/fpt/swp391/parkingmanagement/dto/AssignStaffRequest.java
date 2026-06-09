package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Assign a staff user to a building")
public class AssignStaffRequest {

    @NotBlank(message = "User id is required")
    @Schema(description = "Staff user id (ROLE_STAFF)")
    private String userId;
}
