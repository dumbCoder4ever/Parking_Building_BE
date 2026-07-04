package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.Building;

public interface BuildingRepository extends JpaRepository<Building, String> {
    List<Building> findByStatusIgnoreCaseOrderByBuildingNameAsc(String status);

    Optional<Building> findByBuildingId(String buildingId);

    /**
     * Filter active buildings by name and/or address.
     * Used by GET /api/buildings/available endpoint.
     */
    @Query("select b from Building b where b.status = 'ACTIVE' "
            + "and (:name is null or lower(b.buildingName) like lower(concat('%', :name, '%'))) "
            + "and (:address is null or lower(b.address) like lower(concat('%', :address, '%'))) "
            + "order by b.buildingName asc")
    List<Building> findActiveBuildings(@Param("name") String name, @Param("address") String address);
}

