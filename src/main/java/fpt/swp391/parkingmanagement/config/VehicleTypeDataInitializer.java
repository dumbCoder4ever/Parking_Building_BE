package fpt.swp391.parkingmanagement.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import fpt.swp391.parkingmanagement.service.VehicleTypeSyncService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VehicleTypeDataInitializer implements ApplicationRunner {

    private final VehicleTypeSyncService vehicleTypeSyncService;

    @Override
    public void run(ApplicationArguments args) {
        vehicleTypeSyncService.ensureCanonicalTypes();
    }
}
