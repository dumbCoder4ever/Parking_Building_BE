package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, String> {
    List<Zone> findByFloorFloorId(String floorId);

    @EntityGraph(attributePaths = {"floor", "floor.building", "floor.vehicleType"})
    @EntityGraph(attributePaths = {"floor", "floor.building"})
    List<Zone> findByFloorFloorIdOrderByZoneNameAsc(String floorId);

    boolean existsByFloorFloorIdAndZoneNameIgnoreCase(String floorId, String zoneName);

    boolean existsByFloorFloorIdAndZoneNameIgnoreCaseAndZoneIdNot(
            String floorId, String zoneName, String zoneId);

    long countByFloorFloorId(String floorId);
}
