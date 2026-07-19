package fpt.swp391.parkingmanagement.service;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;

/**
 * Caching wrapper around VehicleTypeRepository. VehicleTypes are configured once
 * at startup and rarely change, so caching them removes several round-trips from
 * availability / reservation / session flows.
 */
@Service
@RequiredArgsConstructor
public class VehicleTypeCacheService {

    private final VehicleTypeRepository vehicleTypeRepository;

    @Cacheable(value = "vehicleTypes", key = "#vehicleTypeId")
    public Optional<VehicleType> findById(String vehicleTypeId) {
        return vehicleTypeRepository.findById(vehicleTypeId);
    }

    @Cacheable(value = "vehicleTypes", key = "'all'")
    public List<VehicleType> findAll() {
        return vehicleTypeRepository.findAll();
    }

    @CacheEvict(value = "vehicleTypes", allEntries = true)
    public void evictAll() {
        // Triggered when VehicleTypeDataInitializer / Manager changes vehicle types.
    }
}