package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

    private static final String[] AVAILABILITY_INDEXES = {
            "CREATE INDEX idx_parking_slots_zone_status ON parking_slots(zone_id, slot_status)",
            "CREATE INDEX idx_parking_slots_status ON parking_slots(slot_status)",
            "CREATE INDEX idx_zones_floor ON zones(floor_id)",
            "CREATE INDEX idx_floors_building_vehicle ON floors(building_id, vehicle_type_id)",
            "CREATE INDEX idx_floors_vehicle ON floors(vehicle_type_id)"
    };

    private static final String[] PERFORMANCE_INDEXES = {
            // Availability / occupancy (same as availability set — safe to re-run)
            "CREATE INDEX idx_parking_slots_zone_status ON parking_slots(zone_id, slot_status)",
            "CREATE INDEX idx_parking_slots_status ON parking_slots(slot_status)",
            "CREATE INDEX idx_zones_floor ON zones(floor_id)",
            "CREATE INDEX idx_floors_building_vehicle ON floors(building_id, vehicle_type_id)",
            "CREATE INDEX idx_floors_vehicle ON floors(vehicle_type_id)",
            // Reservations
            "CREATE INDEX idx_res_status_start ON reservations(reservation_status, reservation_start)",
            "CREATE INDEX idx_res_slot_status ON reservations(slot_id, reservation_status)",
            "CREATE INDEX idx_res_user_created ON reservations(user_id, created_at)",
            "CREATE INDEX idx_res_vehicle_status ON reservations(vehicle_id, reservation_status)",
            // Parking sessions
            "CREATE INDEX idx_ps_vehicle_status_checkin ON parking_sessions(vehicle_id, session_status, checkin_time)",
            "CREATE INDEX idx_ps_vehicle_checkin ON parking_sessions(vehicle_id, checkin_time)",
            "CREATE INDEX idx_ps_slot_status ON parking_sessions(slot_id, session_status)",
            "CREATE INDEX idx_ps_reservation_checkin ON parking_sessions(reservation_id, checkin_time)",
            "CREATE INDEX idx_ps_status_checkin ON parking_sessions(session_status, checkin_time)",
            // Payments
            "CREATE INDEX idx_pay_status_created ON payments(payment_status, created_at)",
            "CREATE INDEX idx_pay_status_time ON payments(payment_status, payment_time)",
            "CREATE INDEX idx_pay_session_created ON payments(session_id, created_at)",
            // Vehicles / users / tickets
            "CREATE INDEX idx_vehicles_plate ON vehicles(plate_number)",
            "CREATE INDEX idx_tickets_reservation ON tickets(reservation_id)",
            "CREATE INDEX idx_vehicles_status_created ON vehicles(status, created_at)",
            "CREATE INDEX idx_users_role ON users(role)",
            // Incidents
            "CREATE INDEX idx_incidents_status_created ON incidents(status, created_at)"
    };

    @PostMapping("/migrate/add-availability-indexes")
    @Transactional
    public ApiResponse<String> addAvailabilityIndexes() {
        return ApiResponse.ok("Index migration completed", runIndexes(AVAILABILITY_INDEXES));
    }

    @PostMapping("/migrate/add-performance-indexes")
    @Transactional
    public ApiResponse<String> addPerformanceIndexes() {
        return ApiResponse.ok("Performance index migration completed", runIndexes(PERFORMANCE_INDEXES));
    }

    private String runIndexes(String[] sqls) {
        StringBuilder results = new StringBuilder();
        for (String sql : sqls) {
            try {
                entityManager.createNativeQuery(sql).executeUpdate();
                results.append("OK: ").append(sql).append("\n");
            } catch (Exception e) {
                results.append("SKIP (exists?): ").append(sql).append(" — ").append(e.getMessage()).append("\n");
            }
        }
        return results.toString();
    }
}
