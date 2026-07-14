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
public class FloorDto {
    private String floorId;
    private Integer floorNumber;
    private String buildingId;
    private String floorStatus;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private List<ZoneSummaryDto> zones;
}
