package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityDto;
import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ReservationRepository;
import fpt.swp391.parkingmanagement.repository.TicketRepository;
import fpt.swp391.parkingmanagement.repository.UserRepository;
import fpt.swp391.parkingmanagement.repository.VehicleRepository;
import fpt.swp391.parkingmanagement.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final FloorRepository floorRepository;
    private final UserRepository userRepository;
    private final VehicleService vehicleService;

    public List<SlotAvailabilityDto> getAvailability() {
        List<SlotAvailabilityDto> result = new ArrayList<>();
        var floors = floorRepository.findAllByOrderByFloorLevelAsc();

        for (Floor f : floors) {
            if (f.getVehicleType() == null) {
                continue;
            }
            VehicleType vt = f.getVehicleType();
            var availableSlots = parkingSlotRepository.findAvailableByFloorAndVehicleType(
                    f.getFloorId(), vt.getVehicleTypeId());

            if (availableSlots.isEmpty()) {
                SlotAvailabilityDto dto = new SlotAvailabilityDto();
                applyHierarchy(dto, null, f);
                dto.setVehicleTypeId(vt.getVehicleTypeId());
                dto.setVehicleTypeName(vt.getTypeName());
                dto.setAvailableCount(0);
                result.add(dto);
                continue;
            }

            for (ParkingSlot slot : availableSlots) {
                SlotAvailabilityDto dto = new SlotAvailabilityDto();
                applyHierarchy(dto, slot, f);
                dto.setVehicleTypeId(vt.getVehicleTypeId());
                dto.setVehicleTypeName(vt.getTypeName());
                dto.setAvailableCount(1);
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional
    public ReservationResponse createReservation(String email, CreateReservationRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Vehicle vehicle = resolveVehicle(email, user, req);
        VehicleType vt = vehicle.getVehicleType();

        var candidates = parkingSlotRepository.findAvailableByVehicleType(vt.getVehicleTypeId());
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("No available slot for this vehicle type");
        }

        ParkingSlot slot = candidates.get(0);
        slot.setSlotStatus("RESERVED");
        parkingSlotRepository.save(slot);

        Reservation reservation = new Reservation();
        reservation.setReservationCode("RS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        reservation.setReservationStart(req.getReservationStart());
        reservation.setReservationEnd(req.getReservationEnd());
        reservation.setReservationStatus("PENDING");
        reservation.setSlot(slot);
        reservation.setUser(user);
        reservation.setVehicle(vehicle);
        reservation = reservationRepository.save(reservation);

        Ticket ticket = new Ticket();
        ticket.setReservation(reservation);
        ticket.setTicketCode("T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setQrCode(java.util.Base64.getEncoder().encodeToString(ticket.getTicketCode().getBytes()));
        ticket = ticketRepository.save(ticket);

        ReservationResponse resp = new ReservationResponse();
        resp.setReservationId(reservation.getReservationId());
        resp.setReservationCode(reservation.getReservationCode());
        resp.setReservationStart(reservation.getReservationStart());
        resp.setReservationEnd(reservation.getReservationEnd());
        applyHierarchy(resp, slot);
        resp.setTicketCode(ticket.getTicketCode());
        resp.setQrCode(ticket.getQrCode());

        return resp;
    }

    private Vehicle resolveVehicle(String email, User user, CreateReservationRequest req) {
        if (StringUtils.hasText(req.getVehicleId())) {
            return vehicleService.getOwnedActiveVehicle(email, req.getVehicleId());
        }

        if (!StringUtils.hasText(req.getPlateNumber())) {
            throw new RuntimeException("Plate number is required when vehicleId is not provided");
        }
        if (!StringUtils.hasText(req.getVehicleTypeId())) {
            throw new RuntimeException("Vehicle type id is required when vehicleId is not provided");
        }

        VehicleType vt = vehicleTypeRepository.findById(req.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Vehicle type not found"));

        return vehicleRepository.findByPlateNumberIgnoreCase(req.getPlateNumber())
                .map(existing -> {
                    if (!existing.getUser().getUserId().equals(user.getUserId())) {
                        throw new RuntimeException("Plate number belongs to another account");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(req.getPlateNumber().trim().toUpperCase());
                    v.setVehicleColor(req.getVehicleColor());
                    v.setBrand(req.getBrand());
                    v.setModel(req.getModel());
                    v.setVehicleType(vt);
                    v.setUser(user);
                    return vehicleRepository.save(v);
                });
    }

    private void applyHierarchy(SlotAvailabilityDto dto, ParkingSlot slot, Floor floor) {
        Floor resolvedFloor = floor;
        Zone zone = null;
        Building building = null;

        if (slot != null) {
            dto.setSlotId(slot.getSlotId());
            dto.setSlotName(slot.getSlotName());
            zone = slot.getZone();
        }

        if (zone != null) {
            dto.setZoneId(zone.getZoneId());
            dto.setZoneName(zone.getZoneName());
            if (resolvedFloor == null) {
                resolvedFloor = zone.getFloor();
            }
        }

        if (resolvedFloor != null) {
            dto.setFloorId(resolvedFloor.getFloorId());
            dto.setFloorName(resolvedFloor.getFloorName());
            building = resolvedFloor.getBuilding();
        }

        if (building != null) {
            dto.setBuildingId(building.getBuildingId());
            dto.setBuildingName(building.getBuildingName());
        }
    }

    private void applyHierarchy(ReservationResponse resp, ParkingSlot slot) {
        resp.setSlotId(slot.getSlotId());
        resp.setSlotName(slot.getSlotName());

        Zone zone = slot.getZone();
        if (zone != null) {
            resp.setZoneId(zone.getZoneId());
            resp.setZoneName(zone.getZoneName());

            Floor floor = zone.getFloor();
            if (floor != null) {
                resp.setFloorId(floor.getFloorId());
                resp.setFloorName(floor.getFloorName());

                Building building = floor.getBuilding();
                if (building != null) {
                    resp.setBuildingId(building.getBuildingId());
                    resp.setBuildingName(building.getBuildingName());
                }
            }
        }
    }
}
