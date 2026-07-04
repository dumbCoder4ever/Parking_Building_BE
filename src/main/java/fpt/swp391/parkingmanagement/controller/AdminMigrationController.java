package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMigrationController {

    @PersistenceContext
    private EntityManager entityManager;

    @PostMapping("/migrate/add-availability-indexes")
    public ApiResponse<String> addAvailabilityIndexes() {
        String[] sqls = {
                "CREATE INDEX idx_parking_slots_zone_status ON parking_slots(zone_id, slot_status)",
                "CREATE INDEX idx_parking_slots_status ON parking_slots(slot_status)",
                "CREATE INDEX idx_zones_floor ON zones(floor_id)",
                "CREATE INDEX idx_floors_building_vehicle ON floors(building_id, vehicle_type_id)",
                "CREATE INDEX idx_floors_vehicle ON floors(vehicle_type_id)"
        };

        StringBuilder results = new StringBuilder();
        for (String sql : sqls) {
            try {
                entityManager.createNativeQuery(sql).executeUpdate();
                results.append("OK: ").append(sql).append("\n");
            } catch (Exception e) {
                results.append("SKIP (exists?): ").append(sql).append(" — ").append(e.getMessage()).append("\n");
            }
        }

        return ApiResponse.ok("Index migration completed", results.toString());
    }
}
