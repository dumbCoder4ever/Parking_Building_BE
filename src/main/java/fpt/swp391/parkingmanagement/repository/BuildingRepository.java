package fpt.swp391.parkingmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Building;

public interface BuildingRepository extends JpaRepository<Building, String> {
}
