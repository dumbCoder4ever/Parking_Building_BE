package fpt.swp391.parkingmanagement.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleTypeOptionResponse {

    private String vehicleTypeId;
    private String typeName;
    private String sizeCategory;
    private String description;
}
