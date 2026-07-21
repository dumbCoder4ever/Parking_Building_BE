package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentUpdateRequest {
    private String resolution;
    private String resolutionAction;
    private BigDecimal adjustedAmount;
    private String newSlotId;
}
