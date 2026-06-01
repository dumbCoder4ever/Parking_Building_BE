package fpt.swp391.parkingmanagement.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CreateFloorRequest;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleTypeSyncService {

    private final VehicleTypeRepository vehicleTypeRepository;
    private final EntityManager entityManager;

    @Transactional
    public void ensureCanonicalTypes() {
        ensureType(
                CreateFloorRequest.MOTORBIKE_TYPE_ID,
                "Motorbike",
                "SMALL",
                "Standard motorbike");
        ensureType(
                CreateFloorRequest.CAR_TYPE_ID,
                "Car",
                "MEDIUM",
                "4-seat or 7-seat car");
    }

    private void ensureType(String canonicalId, String typeName, String sizeCategory, String description) {
        if (vehicleTypeRepository.findById(canonicalId).isPresent()) {
            return;
        }

        Optional<VehicleType> existing = vehicleTypeRepository.findByTypeNameIgnoreCase(typeName);
        if (existing.isPresent()) {
            String oldId = existing.get().getVehicleTypeId();
            if (!oldId.equals(canonicalId)) {
                reassignVehicleTypeId(existing.get(), canonicalId);
                log.info("Synced vehicle type '{}' to canonical id {} (was {})", typeName, canonicalId, oldId);
            }
            return;
        }

        insertType(canonicalId, typeName, sizeCategory, description);
        log.info("Inserted vehicle type '{}' with id {}", typeName, canonicalId);
    }

    private void reassignVehicleTypeId(VehicleType existing, String canonicalId) {
        String oldId = existing.getVehicleTypeId();

        VehicleType canonical = new VehicleType();
        canonical.setVehicleTypeId(canonicalId);
        canonical.setTypeName(existing.getTypeName());
        canonical.setSizeCategory(existing.getSizeCategory());
        canonical.setDescription(existing.getDescription());
        vehicleTypeRepository.saveAndFlush(canonical);

        updateReferences(oldId, canonicalId);
        vehicleTypeRepository.deleteById(oldId);
    }

    private void updateReferences(String oldId, String newId) {
        entityManager.createNativeQuery(
                        "UPDATE floors SET vehicle_type_id = :newId WHERE vehicle_type_id = :oldId")
                .setParameter("newId", newId)
                .setParameter("oldId", oldId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "UPDATE vehicles SET vehicle_type_id = :newId WHERE vehicle_type_id = :oldId")
                .setParameter("newId", newId)
                .setParameter("oldId", oldId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "UPDATE pricing_policies SET vehicle_type_id = :newId WHERE vehicle_type_id = :oldId")
                .setParameter("newId", newId)
                .setParameter("oldId", oldId)
                .executeUpdate();
    }

    private void insertType(String vehicleTypeId, String typeName, String sizeCategory, String description) {
        VehicleType vehicleType = new VehicleType();
        vehicleType.setVehicleTypeId(vehicleTypeId);
        vehicleType.setTypeName(typeName);
        vehicleType.setSizeCategory(sizeCategory);
        vehicleType.setDescription(description);
        vehicleTypeRepository.save(vehicleType);
    }
}
