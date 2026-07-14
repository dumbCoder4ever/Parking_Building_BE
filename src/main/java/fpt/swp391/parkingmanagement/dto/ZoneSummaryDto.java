package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneSummaryDto {
    private String zoneId;
    private String zoneName;
    private String zoneStatus;
    private SlotSummaryDto slotSummary;
}
