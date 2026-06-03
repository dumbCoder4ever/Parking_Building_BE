package fpt.swp391.parkingmanagement.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.applyAddFloorsSlots", havingValue = "true")
public class DbSeedRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DbSeedRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        Path sqlPath = Path.of("docs", "db", "add_floors_slots.sql");
        if (!Files.exists(sqlPath)) {
            System.err.println("SQL file not found: " + sqlPath.toAbsolutePath());
            System.exit(2);
        }

        String content = Files.readString(sqlPath);
        String[] statements = content.split(";");
        int executed = 0;
        for (String st : statements) {
            String s = st.trim();
            if (s.isEmpty()) continue;
            // skip SQL comment-only lines
            if (s.startsWith("--")) continue;
            try {
                jdbcTemplate.execute(s);
                executed++;
            } catch (Exception ex) {
                System.err.println("Failed to execute statement: " + s);
                ex.printStackTrace();
            }
        }

        Integer totalSlots = 0;
        try {
            totalSlots = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_slots", Integer.class);
        } catch (Exception ex) {
            System.err.println("Unable to query parking_slots count: " + ex.getMessage());
        }

        System.out.println("Applied add_floors_slots.sql; statements attempted: " + executed + ", total parking_slots: " + totalSlots);

        // print detailed counts per floor/zone
        try {
            System.out.println("\nSlots by floor and zone:");
            var rows = jdbcTemplate.queryForList(
                    "SELECT f.floor_level, f.floor_name, z.zone_name, COUNT(ps.slot_id) AS slots " +
                            "FROM floors f " +
                            "JOIN zones z ON z.floor_id = f.floor_id " +
                            "LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id " +
                            "GROUP BY f.floor_level, f.floor_name, z.zone_name " +
                            "ORDER BY f.floor_level, z.zone_name");
            for (var r : rows) {
                System.out.println(String.format("Floor %s (%s) - %s : %s slots", r.get("floor_level"), r.get("floor_name"), r.get("zone_name"), r.get("slots")));
            }
        } catch (Exception ex) {
            System.err.println("Unable to query slots by floor/zone: " + ex.getMessage());
        }
        // Attempt to ensure vehicle types, zones and expected number of slots exist
        try {
            System.out.println("\nInspecting vehicle types, floors and zones...");
            var vtypes = jdbcTemplate.queryForList("SELECT vehicle_type_id, type_name FROM vehicle_types");
            System.out.println("vehicle_types:");
            for (var vt : vtypes) {
                System.out.println(" - " + vt.get("vehicle_type_id") + " : " + vt.get("type_name"));
            }

            // Ensure Car and Motorbike types exist
            String carTypeId = null;
            try {
                carTypeId = jdbcTemplate.queryForObject("SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Car' LIMIT 1", String.class);
            } catch (Exception ignore) {}
            if (carTypeId == null) {
                jdbcTemplate.update("INSERT INTO vehicle_types (type_name) VALUES ('Car')");
                carTypeId = jdbcTemplate.queryForObject("SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Car' LIMIT 1", String.class);
                System.out.println("Inserted vehicle_type Car id=" + carTypeId);
            }

            String motorTypeId = null;
            try {
                motorTypeId = jdbcTemplate.queryForObject("SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Motorbike' LIMIT 1", String.class);
            } catch (Exception ignore) {}
            if (motorTypeId == null) {
                jdbcTemplate.update("INSERT INTO vehicle_types (type_name) VALUES ('Motorbike')");
                motorTypeId = jdbcTemplate.queryForObject("SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Motorbike' LIMIT 1", String.class);
                System.out.println("Inserted vehicle_type Motorbike id=" + motorTypeId);
            }

            // Find main building (fallback to first building if exact name not found)
            String buildingId = null;
            String preferredName = "Main Parking Building";
            try {
                buildingId = jdbcTemplate.queryForObject("SELECT building_id FROM buildings WHERE building_name = ? LIMIT 1", new Object[]{preferredName}, String.class);
            } catch (Exception ignore) {}

            if (buildingId == null) {
                System.out.println("Preferred building 'Main Parking Building' not found — listing existing buildings and using the first one as fallback.");
                var bld = jdbcTemplate.queryForList("SELECT building_id, building_name FROM buildings");
                if (bld != null && !bld.isEmpty()) {
                    var first = bld.get(0);
                    Object idObj = first.get("building_id");
                    if (idObj != null) {
                        buildingId = idObj.toString();
                    }
                    System.out.println("Using building as fallback: " + first.get("building_name") + " (id=" + buildingId + ")");
                } else {
                    System.err.println("No building found in DB. Aborting automated slot seeding.");
                }
            }
            if (buildingId == null) {
                // cannot proceed
            } else {
                for (int level = 1; level <= 4; level++) {
                    String floorId = null;
                    try {
                        floorId = jdbcTemplate.queryForObject("SELECT floor_id FROM floors WHERE building_id = ? AND floor_level = ?", new Object[]{buildingId, level}, String.class);
                    } catch (Exception ignore) {}
                    if (floorId == null) {
                        // create floor if missing
                        jdbcTemplate.update("INSERT INTO floors (building_id, floor_name, floor_level, max_capacity, status) VALUES (?, ?, ?, ?, 'ACTIVE')", buildingId, "Floor " + level, level, 30);
                        floorId = jdbcTemplate.queryForObject("SELECT floor_id FROM floors WHERE building_id = ? AND floor_level = ?", new Object[]{buildingId, level}, String.class);
                        System.out.println("Inserted Floor " + level + " id=" + floorId);
                    }

                    // Ensure Car zone
                    String carZoneName = "Zone-Car-F" + level;
                    String carZoneId = null;
                    try {
                        carZoneId = jdbcTemplate.queryForObject("SELECT zone_id FROM zones WHERE floor_id = ? AND zone_name = ?", new Object[]{floorId, carZoneName}, String.class);
                    } catch (Exception ignore) {}
                    if (carZoneId == null) {
                        jdbcTemplate.update("INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status) VALUES (?, ?, ?, ?, 'ACTIVE')", floorId, carTypeId, carZoneName, 10);
                        carZoneId = jdbcTemplate.queryForObject("SELECT zone_id FROM zones WHERE floor_id = ? AND zone_name = ?", new Object[]{floorId, carZoneName}, String.class);
                        System.out.println("Inserted zone " + carZoneName + " id=" + carZoneId);
                    }

                    // Ensure Motorbike zone
                    String motorZoneName = "Zone-Motorbike-F" + level;
                    String motorZoneId = null;
                    try {
                        motorZoneId = jdbcTemplate.queryForObject("SELECT zone_id FROM zones WHERE floor_id = ? AND zone_name = ?", new Object[]{floorId, motorZoneName}, String.class);
                    } catch (Exception ignore) {}
                    if (motorZoneId == null) {
                        jdbcTemplate.update("INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status) VALUES (?, ?, ?, ?, 'ACTIVE')", floorId, motorTypeId, motorZoneName, 20);
                        motorZoneId = jdbcTemplate.queryForObject("SELECT zone_id FROM zones WHERE floor_id = ? AND zone_name = ?", new Object[]{floorId, motorZoneName}, String.class);
                        System.out.println("Inserted zone " + motorZoneName + " id=" + motorZoneId);
                    }

                    // Insert missing Car slots up to 10
                    for (int n = 1; n <= 10; n++) {
                        String slotName = String.format("C-F%d-%02d", level, n);
                        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_slots WHERE zone_id = ? AND slot_name = ?", new Object[]{carZoneId, slotName}, Integer.class);
                        if (cnt == null || cnt == 0) {
                            try {
                                jdbcTemplate.update("INSERT INTO parking_slots (zone_id, slot_name) VALUES (?, ?)", carZoneId, slotName);
                            } catch (Exception e) {
                                System.err.println("Failed to insert slot " + slotName + ": " + e.getMessage());
                            }
                        }
                    }

                    // Insert missing Motorbike slots up to 20
                    for (int n = 1; n <= 20; n++) {
                        String slotName = String.format("M-F%d-%02d", level, n);
                        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_slots WHERE zone_id = ? AND slot_name = ?", new Object[]{motorZoneId, slotName}, Integer.class);
                        if (cnt == null || cnt == 0) {
                            try {
                                jdbcTemplate.update("INSERT INTO parking_slots (zone_id, slot_name) VALUES (?, ?)", motorZoneId, slotName);
                            } catch (Exception e) {
                                System.err.println("Failed to insert slot " + slotName + ": " + e.getMessage());
                            }
                        }
                    }
                }

                // Recalculate occupancy and building totals
                jdbcTemplate.update("UPDATE floors f SET f.current_occupancy = (SELECT COUNT(ps.slot_id) FROM parking_slots ps JOIN zones z ON ps.zone_id = z.zone_id WHERE z.floor_id = f.floor_id)");
                jdbcTemplate.update("UPDATE buildings b SET b.total_floors = GREATEST(b.total_floors, 4) WHERE b.building_name = 'Main Parking Building'");

                // Re-query totals and print final summary
                try {
                    Integer newTotal = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM parking_slots", Integer.class);
                    System.out.println("\nAfter seeding: total parking_slots = " + newTotal);
                } catch (Exception ignore) {}

                System.out.println("Final slots by floor and zone:");
                var finalRows = jdbcTemplate.queryForList(
                        "SELECT f.floor_level, f.floor_name, z.zone_name, COUNT(ps.slot_id) AS slots " +
                                "FROM floors f " +
                                "JOIN zones z ON z.floor_id = f.floor_id " +
                                "LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id " +
                                "GROUP BY f.floor_level, f.floor_name, z.zone_name " +
                                "ORDER BY f.floor_level, z.zone_name");
                for (var r : finalRows) {
                    System.out.println(String.format("Floor %s (%s) - %s : %s slots", r.get("floor_level"), r.get("floor_name"), r.get("zone_name"), r.get("slots")));
                }

                // ---------- Additional fixes: normalize slot_status and deduplicate vehicle_types ----------
                try {
                    System.out.println("\nApplying normalization and dedup fixes...");

                    int normalized = jdbcTemplate.update("UPDATE parking_slots SET slot_status = 'AVAILABLE' WHERE slot_status IS NULL OR slot_status NOT IN ('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE')");
                    System.out.println("Normalized parking_slots.slot_status rows: " + normalized);

                    try {
                        jdbcTemplate.execute("ALTER TABLE parking_slots MODIFY COLUMN slot_status ENUM('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE'");
                        System.out.println("Ensured parking_slots.slot_status is NOT NULL with DEFAULT 'AVAILABLE'");
                    } catch (Exception e) {
                        System.err.println("ALTER parking_slots failed: " + e.getMessage());
                    }

                    var dupTypes = jdbcTemplate.queryForList("SELECT type_name, COUNT(*) cnt FROM vehicle_types GROUP BY type_name HAVING COUNT(*) > 1");
                    if (dupTypes == null || dupTypes.isEmpty()) {
                        System.out.println("No duplicate vehicle_types found.");
                    } else {
                        for (var dt : dupTypes) {
                            String typeName = (String) dt.get("type_name");
                            System.out.println("Deduping vehicle_type: " + typeName);
                            String canonical = null;
                            try {
                                canonical = jdbcTemplate.queryForObject("SELECT vehicle_type_id FROM vehicle_types WHERE type_name = ? ORDER BY created_at LIMIT 1", new Object[]{typeName}, String.class);
                            } catch (Exception ignore) {}
                            if (canonical == null) continue;
                            var dupIds = jdbcTemplate.queryForList("SELECT vehicle_type_id FROM vehicle_types WHERE type_name = ? AND vehicle_type_id <> ?", new Object[]{typeName, canonical});
                            for (var row : dupIds) {
                                String dupId = (String) row.get("vehicle_type_id");
                                jdbcTemplate.update("UPDATE zones SET vehicle_type_id = ? WHERE vehicle_type_id = ?", canonical, dupId);
                                jdbcTemplate.update("UPDATE vehicles SET vehicle_type_id = ? WHERE vehicle_type_id = ?", canonical, dupId);
                                jdbcTemplate.update("DELETE FROM vehicle_types WHERE vehicle_type_id = ?", dupId);
                                System.out.println("Removed duplicate vehicle_type id " + dupId + " and relinked references to " + canonical);
                            }
                        }
                    }

                    // Recalc occupancy after fixes
                    jdbcTemplate.update("UPDATE floors f JOIN (SELECT f.floor_id AS fid, COUNT(ps.slot_id) AS cnt FROM floors f LEFT JOIN zones z ON z.floor_id = f.floor_id LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id GROUP BY f.floor_id) t ON t.fid = f.floor_id SET f.current_occupancy = t.cnt WHERE f.floor_id IS NOT NULL");

                    var vtypesAfter = jdbcTemplate.queryForList("SELECT vehicle_type_id, type_name FROM vehicle_types");
                    System.out.println("\nvehicle_types after dedupe:");
                    for (var vt : vtypesAfter) {
                        System.out.println(" - " + vt.get("vehicle_type_id") + " : " + vt.get("type_name"));
                    }
                } catch (Exception ex) {
                    System.err.println("Automated fixes failed: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            System.err.println("Automated seeding failed: " + ex.getMessage());
            ex.printStackTrace();
        }

        // exit after applying so bootRun doesn't keep running
        System.exit(0);
    }
}
