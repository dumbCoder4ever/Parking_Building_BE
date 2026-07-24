package fpt.swp391.parkingmanagement.service;

import java.util.List;

import fpt.swp391.parkingmanagement.dto.AvailableSlotResponse;
import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.dto.IncidentUpdateRequest;
import fpt.swp391.parkingmanagement.dto.LatestReservationResponse;
import fpt.swp391.parkingmanagement.dto.SessionEvidenceResponse;
import fpt.swp391.parkingmanagement.dto.SlotAvailabilityCheckResponse;
import fpt.swp391.parkingmanagement.dto.VerifyVehicleRequest;
import fpt.swp391.parkingmanagement.dto.VerifyVehicleResponse;

public interface IncidentService {
    IncidentResponse createIncident(String staffEmail, IncidentRequest request);

    IncidentResponse updateIncidentStatus(String staffEmail, String incidentId, String status);

    IncidentResponse updateIncidentStatus(String staffEmail, String incidentId, String status, IncidentUpdateRequest request);

    IncidentResponse getIncident(String staffEmail, String incidentId);

    List<IncidentResponse> getAllIncidents(String staffEmail);

    List<IncidentResponse> getIncidentsBySession(String sessionId);

    long countByStatus(String status);

    List<IncidentResponse> getAllDriverReports(String staffEmail, String buildingId);

    IncidentResponse createDriverReport(String driverEmail, IncidentRequest request);

    List<IncidentResponse> getDriverReports(String driverEmail);

    IncidentResponse createSystemIncident(String sessionId, String incidentType, String description);

    // Vehicle verification for DRIVER_LOST_TICKET
    VerifyVehicleResponse verifyVehicleOwnership(String incidentId, VerifyVehicleRequest request, String staffEmail);

    // Slot availability check for reassignment
    SlotAvailabilityCheckResponse checkSlotAvailabilityForReassignment(String incidentId, String newSlotId, String staffEmail);

    // Session-based evidence (primary) for staff incident handling
    SessionEvidenceResponse getSessionEvidenceForIncident(String incidentId, String staffEmail);

    // Latest reservation evidence cho staff xem (4 flow incident) — supplementary
    LatestReservationResponse getLatestReservationForIncident(String incidentId, String staffEmail);

    // Danh sach slot trong cung floor voi reservation moi nhat (DRIVER_SLOT_OCCUPIED)
    List<AvailableSlotResponse> getAvailableSlotsForReassign(String incidentId, String staffEmail);
}