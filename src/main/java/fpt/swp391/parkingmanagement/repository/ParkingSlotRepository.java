package fpt.swp391.parkingmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fpt.swp391.parkingmanagement.entity.ParkingSlot;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, String> {

    @Query("select ps from ParkingSlot ps join ps.zone z join z.vehicleType vt where vt.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    List<ParkingSlot> findAvailableByVehicleType(@Param("vehicleTypeId") String vehicleTypeId);

    @Query("select ps from ParkingSlot ps join ps.zone z join z.floor f join z.vehicleType vt where f.floorId = :floorId and vt.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    List<ParkingSlot> findAvailableByFloorAndVehicleType(@Param("floorId") String floorId, @Param("vehicleTypeId") String vehicleTypeId);

    @Query("select count(ps) from ParkingSlot ps join ps.zone z join z.floor f join z.vehicleType vt where f.floorId = :floorId and vt.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    long countAvailableByFloorAndVehicleType(@Param("floorId") String floorId, @Param("vehicleTypeId") String vehicleTypeId);

    @Query("select ps from ParkingSlot ps join ps.zone z join z.vehicleType vt where vt.vehicleTypeId = :vehicleTypeId and ps.slotStatus = 'AVAILABLE'")
    Optional<ParkingSlot> findFirstAvailableByVehicleType(@Param("vehicleTypeId") String vehicleTypeId);
}
