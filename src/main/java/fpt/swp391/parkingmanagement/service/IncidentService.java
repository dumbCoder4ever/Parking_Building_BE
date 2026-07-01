package fpt.swp391.parkingmanagement.service;

import java.util.List;

import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;

public interface IncidentService {
    IncidentResponse createIncident(String staffEmail, IncidentRequest request);

    IncidentResponse updateIncidentStatus(String staffEmail, String incidentId, String status);

    IncidentResponse getIncident(String staffEmail, String incidentId);

    List<IncidentResponse> getAllIncidents(String staffEmail);

    List<IncidentResponse> getIncidentsBySession(String sessionId);

    long countByStatus(String status);
}