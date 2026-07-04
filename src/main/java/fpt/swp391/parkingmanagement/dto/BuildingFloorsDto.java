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
public class BuildingFloorsDto {
    private String buildingId;
    private List<FloorWithZonesDto> floors;
}
