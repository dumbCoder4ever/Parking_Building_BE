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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotStatusSyncJob {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSessionRepository parkingSessionRepository;

    private static final List<String> ACTIVE_RESERVATION_STATUSES = List.of("PENDING", "APPROVED");

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void syncSlotStatusWithReservations() {
        log.debug("Running slot status sync job...");
        try {
            // Find all RESERVED slots
            List<ParkingSlot> reservedSlots = parkingSlotRepository.findBySlotStatusIgnoreCase("RESERVED");
            if (reservedSlots.isEmpty()) {
                return;
            }
            
            // FIX N+1: Batch-load active reservations chỉ lấy slotId thay vì load toàn bộ Reservation entity
            List<String> reservedSlotIds = reservedSlots.stream()
                    .map(ParkingSlot::getSlotId)
                    .toList();
            
            // Chỉ lấy projection slotId thay vì load full Reservation (tránh load relations không cần)
            List<String> activeSlotIds = reservationRepository.findActiveSlotIdsByStatuses(ACTIVE_RESERVATION_STATUSES);
            
            // Chuyển thành Set để lookup O(1)
            Set<String> activeSlotIdSet = Set.copyOf(activeSlotIds);
            
            int fixedCount = 0;
            for (ParkingSlot slot : reservedSlots) {
                String slotId = slot.getSlotId();
                
                // Nếu slot RESERVED không có reservation active -> orphaned
                if (!activeSlotIdSet.contains(slotId)) {
                    slot.setSlotStatus("AVAILABLE");
                    parkingSlotRepository.save(slot);
                    fixedCount++;
                    log.info("Fixed orphaned slot: {} (was RESERVED but no active reservation)", slot.getSlotName());
                }
            }

            if (fixedCount > 0) {
                log.info("SlotStatusSyncJob: Fixed {} orphaned RESERVED slots", fixedCount);
            }

            // Fix OCCUPIED slots that have no active session (orphaned check-in state)
            // FIX N+1: Batch-load active sessions cho tất cả OCCUPIED slots trong 1 query
            List<ParkingSlot> occupiedSlots = parkingSlotRepository.findBySlotStatusIgnoreCase("OCCUPIED");
            if (occupiedSlots.isEmpty()) {
                return;
            }
            
            List<String> occupiedSlotIds = occupiedSlots.stream()
                    .map(ParkingSlot::getSlotId)
                    .toList();
            Set<String> occupiedSlotIdSet = Set.copyOf(occupiedSlotIds);
            
            // Lấy tất cả active sessions cho các slot này trong 1 query
            Set<String> slotsWithActiveSession = parkingSessionRepository
                    .findSlotIdsBySlotIdInAndSessionStatusIn(
                            occupiedSlotIds,
                            List.of("ACTIVE", "PENDING_PAYMENT", "PENDING_EXIT"));
            
            int occupiedFixed = 0;
            for (ParkingSlot slot : occupiedSlots) {
                if (!slotsWithActiveSession.contains(slot.getSlotId())) {
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
