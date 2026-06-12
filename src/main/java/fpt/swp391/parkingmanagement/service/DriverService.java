package fpt.swp391.parkingmanagement.service;

import fpt.swp391.parkingmanagement.dto.*;

import java.util.List;

public interface DriverService {

    DriverProfileResponse getDriverProfile(String email);

    DriverProfileResponse updateDriverProfile(String email, DriverProfileResponse request);

    List<VehicleResponse> getMyVehicles(String email);

    VehicleResponse addVehicle(String email, VehicleRequest request);

    VehicleResponse updateVehicle(String email, String vehicleId, VehicleRequest request);

    void deleteVehicle(String email, String vehicleId);

    List<DriverSessionHistoryResponse> getMyParkingHistory(String email, int limit);

    List<PaymentResponse> getMyPaymentHistory(String email, int limit);

    DriverStatsResponse getDriverStats(String email);
}
