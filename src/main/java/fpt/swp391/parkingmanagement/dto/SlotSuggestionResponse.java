package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotSuggestionResponse {
    private String slotId;
    private String slotName;
    private String zoneId;
    private String zoneName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private double score;
    private String reason;
}
