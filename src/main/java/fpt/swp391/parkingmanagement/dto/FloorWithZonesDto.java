package fpt.swp391.parkingmanagement.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorWithZonesDto {
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String floorStatus;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private List<ZoneSummaryDto> zones;
}
