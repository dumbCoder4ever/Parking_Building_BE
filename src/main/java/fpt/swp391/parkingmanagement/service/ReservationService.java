package fpt.swp391.parkingmanagement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.dto.CreateReservationRequest;
import fpt.swp391.parkingmanagement.dto.ReservationResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityDto;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Reservation;
import fpt.swp391.parkingmanagement.entity.Ticket;
import fpt.swp391.parkingmanagement.entity.User;
import fpt.swp391.parkingmanagement.entity.Vehicle;
import fpt.swp391.parkingmanagement.entity.VehicleType;
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

    public List<SlotAvailabilityDto> getAvailability() {
        List<SlotAvailabilityDto> result = new ArrayList<>();
        var floors = floorRepository.findAllByOrderByFloorLevelAsc();
        var vehicleTypes = vehicleTypeRepository.findAll();

        for (Floor f : floors) {
            for (VehicleType vt : vehicleTypes) {
                long cnt = parkingSlotRepository.countAvailableByFloorAndVehicleType(f.getFloorId(), vt.getVehicleTypeId());
                SlotAvailabilityDto dto = new SlotAvailabilityDto();
                dto.setFloorId(f.getFloorId());
                dto.setFloorName(f.getFloorName());
                dto.setVehicleTypeId(vt.getVehicleTypeId());
                dto.setVehicleTypeName(vt.getTypeName());
                dto.setAvailableCount(cnt);
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional
    public ReservationResponse createReservation(String email, CreateReservationRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VehicleType vt = vehicleTypeRepository.findById(req.getVehicleTypeId()).orElseThrow(() -> new RuntimeException("Vehicle type not found"));

        Vehicle vehicle = vehicleRepository.findByPlateNumber(req.getPlateNumber())
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setPlateNumber(req.getPlateNumber());
                    v.setVehicleColor(req.getVehicleColor());
                    v.setBrand(req.getBrand());
                    v.setModel(req.getModel());
                    v.setVehicleType(vt);
                    v.setUser(user);
                    return vehicleRepository.save(v);
                });

        var candidates = parkingSlotRepository.findAvailableByVehicleType(vt.getVehicleTypeId());
        if (candidates == null || candidates.isEmpty()) throw new RuntimeException("No available slot for this vehicle type");

        ParkingSlot slot = candidates.get(0);
        slot.setSlotStatus("RESERVED");
        parkingSlotRepository.save(slot);

        Reservation reservation = new Reservation();
        reservation.setReservationCode("RS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        reservation.setReservationStart(req.getReservationStart());
        reservation.setReservationEnd(req.getReservationEnd());
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
        resp.setSlotId(slot.getSlotId());
        resp.setSlotName(slot.getSlotName());
        resp.setTicketCode(ticket.getTicketCode());
        resp.setQrCode(ticket.getQrCode());

        return resp;
    }
}
