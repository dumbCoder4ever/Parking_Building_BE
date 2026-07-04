-- Fix slow query: /api/buildings/available
-- Root cause: aggregateSlotCounts JOIN 5 bảng không có index
-- Add composite indexes to accelerate zone-slot-vehicle joins

-- parking_slots: index for zone join + status filter (most critical)
CREATE INDEX IF NOT EXISTS idx_parking_slots_zone_status
    ON parking_slots(zone_id, slot_status);

-- parking_slots: index for status-only queries (e.g. dashboard counts)
CREATE INDEX IF NOT EXISTS idx_parking_slots_status
    ON parking_slots(slot_status);

-- zones: index for floor join
CREATE INDEX IF NOT EXISTS idx_zones_floor
    ON zones(floor_id);

-- floors: composite index for building + vehicle type joins
CREATE INDEX IF NOT EXISTS idx_floors_building_vehicle
    ON floors(building_id, vehicle_type_id);

-- floors: index for vehicle type alone (needed by aggregateSlotCounts filter)
CREATE INDEX IF NOT EXISTS idx_floors_vehicle
    ON floors(vehicle_type_id);
