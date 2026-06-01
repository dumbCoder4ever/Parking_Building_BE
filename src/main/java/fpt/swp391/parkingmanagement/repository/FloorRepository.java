package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Floor;

public interface FloorRepository extends JpaRepository<Floor, String> {
    List<Floor> findAllByOrderByFloorLevelAsc();
}
