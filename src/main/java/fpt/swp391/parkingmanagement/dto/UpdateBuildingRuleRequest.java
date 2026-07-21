package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateBuildingRuleRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    private String ruleValue;

    @NotBlank(message = "status is required")
    private String status;
}
