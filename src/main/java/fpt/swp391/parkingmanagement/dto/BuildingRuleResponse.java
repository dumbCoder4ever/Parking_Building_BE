package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingRuleResponse {
    private String ruleId;
    private String buildingId;
    private String ruleCode;
    private String title;
    private String description;
    private String ruleValue;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
