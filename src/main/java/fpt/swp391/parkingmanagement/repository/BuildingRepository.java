package fpt.swp391.parkingmanagement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fpt.swp391.parkingmanagement.entity.Building;

public interface BuildingRepository extends JpaRepository<Building, String> {
    List<Building> findByStatusIgnoreCaseOrderByBuildingNameAsc(String status);
 }

