package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBuildingRuleRequest {

    @NotBlank(message = "ruleCode is required")
    private String ruleCode;

    @NotBlank(message = "title is required")
    private String title;

    private String description;

    private String ruleValue;

    /** ACTIVE or INACTIVE; default ACTIVE */
    private String status;
}
