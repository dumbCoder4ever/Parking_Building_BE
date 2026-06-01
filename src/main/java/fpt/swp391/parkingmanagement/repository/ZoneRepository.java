package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, String> {
    List<Zone> findByFloorFloorId(String floorId);
}
