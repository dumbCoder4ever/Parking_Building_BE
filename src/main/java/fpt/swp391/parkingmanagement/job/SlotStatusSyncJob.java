package fpt.swp391.parkingmanagement.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotStatusSyncJob {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void syncSlotStatusWithReservations() {
        log.debug("Running slot status sync job...");
        try {
            // Find all RESERVED slots
            List<ParkingSlot> reservedSlots = parkingSlotRepository.findBySlotStatusIgnoreCase("RESERVED");

            // Get all active reservation slot IDs
            List<Reservation> activeReservations = reservationRepository.findAll().stream()
                    .filter(r -> "PENDING".equals(r.getReservationStatus()) || "APPROVED".equals(r.getReservationStatus()))
                    .toList();
            
            Set<String> activeSlotIds = activeReservations.stream()
                    .filter(r -> r.getSlot() != null && r.getSlot().getSlotId() != null)
                    .map(r -> r.getSlot().getSlotId())
                    .collect(Collectors.toSet());

            int fixedCount = 0;
            for (ParkingSlot slot : reservedSlots) {
                String slotId = slot.getSlotId();
                
                // Check if slot still has an active reservation
                boolean hasActiveReservation = activeSlotIds.contains(slotId);
                
                // Check if there's a valid active reservation for this slot
                if (!hasActiveReservation) {
                    // Check by querying directly
                    var activeResForSlot = reservationRepository
                            .findFirstBySlotSlotIdAndReservationStatusInOrderByCreatedAtDesc(
                                    slotId, List.of("PENDING", "APPROVED"));
                    
                    if (activeResForSlot.isEmpty()) {
                        // No active reservation found - slot should be AVAILABLE
                        slot.setSlotStatus("AVAILABLE");
                        parkingSlotRepository.save(slot);
                        fixedCount++;
                        log.info("Fixed orphaned slot: {} (was RESERVED but no active reservation)", slot.getSlotName());
                    }
                }
            }

            if (fixedCount > 0) {
                log.info("SlotStatusSyncJob: Fixed {} orphaned RESERVED slots", fixedCount);
            }

            // Fix OCCUPIED slots that have no active session (orphaned check-in state)
            List<ParkingSlot> occupiedSlots = parkingSlotRepository.findBySlotStatusIgnoreCase("OCCUPIED");
            int occupiedFixed = 0;
            for (ParkingSlot slot : occupiedSlots) {
                boolean hasActiveSession = parkingSessionRepository
                        .findCurrentBySlotId(slot.getSlotId())
                        .isPresent();
                if (!hasActiveSession) {
                    slot.setSlotStatus("AVAILABLE");
                    parkingSlotRepository.save(slot);
                    occupiedFixed++;
                    log.warn("Fixed orphaned OCCUPIED slot: {} (no active session found)", slot.getSlotName());
                }
            }
            if (occupiedFixed > 0) {
                log.info("SlotStatusSyncJob: Fixed {} orphaned OCCUPIED slots", occupiedFixed);
            }
        } catch (Exception e) {
            log.error("Error in slot status sync job", e);
        }
    }
}
