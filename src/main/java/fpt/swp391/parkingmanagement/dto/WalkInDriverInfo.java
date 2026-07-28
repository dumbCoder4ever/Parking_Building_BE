package fpt.swp391.parkingmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin xe + driver cho trường hợp lookup walk-in driver (không có reservation).")
public class WalkInDriverInfo {

    private String vehicleId;
    private String userId;
    private String plateNumber;
    private String brand;
    private String model;
    private String vehicleColor;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private String driverFullName;
    private String driverPhone;
    private String driverEmail;
}