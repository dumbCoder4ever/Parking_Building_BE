package fpt.swp391.parkingmanagement.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            List<ParkingSlot> reservedSlots = parkingSlotRepository.findBySlotStatusIgnoreCase("RESERVED");
            List<ParkingSlot> toFix = new ArrayList<>();

            if (!reservedSlots.isEmpty()) {
                Set<String> activeSlotIdSet = Set.copyOf(
                        reservationRepository.findActiveSlotIdsByStatuses(ACTIVE_RESERVATION_STATUSES));

                for (ParkingSlot slot : reservedSlots) {
                    if (!activeSlotIdSet.contains(slot.getSlotId())) {
                        slot.setSlotStatus("AVAILABLE");
                        toFix.add(slot);
                        log.info("Fixed orphaned slot: {} (was RESERVED but no active reservation)", slot.getSlotName());
                    }
                }
            }

            List<ParkingSlot> occupiedSlots = parkingSlotRepository.findBySlotStatusIgnoreCase("OCCUPIED");
            if (!occupiedSlots.isEmpty()) {
                List<String> occupiedSlotIds = occupiedSlots.stream()
                        .map(ParkingSlot::getSlotId)
                        .toList();

                Set<String> slotsWithActiveSession = parkingSessionRepository
                        .findSlotIdsBySlotIdInAndSessionStatusIn(
                                occupiedSlotIds,
                                List.of("ACTIVE", "PENDING_PAYMENT", "PENDING_EXIT"));

                for (ParkingSlot slot : occupiedSlots) {
                    if (!slotsWithActiveSession.contains(slot.getSlotId())) {
                        slot.setSlotStatus("AVAILABLE");
                        toFix.add(slot);
                        log.warn("Fixed orphaned OCCUPIED slot: {} (no active session found)", slot.getSlotName());
                    }
                }
            }

            if (!toFix.isEmpty()) {
                parkingSlotRepository.saveAll(toFix);
                log.info("SlotStatusSyncJob: Fixed {} orphaned slots", toFix.size());
            }
        } catch (Exception e) {
            log.error("Error in slot status sync job", e);
        }
    }
}
