package fpt.swp391.parkingmanagement.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fpt.swp391.parkingmanagement.dto.ApiResponse;
import fpt.swp391.parkingmanagement.dto.IncidentRequest;
import fpt.swp391.parkingmanagement.dto.IncidentResponse;
import fpt.swp391.parkingmanagement.dto.IncidentUpdateRequest;
import fpt.swp391.parkingmanagement.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident Management", description = "APIs for parking incident tracking")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    @Operation(summary = "Create a new incident (staff)")
    public ResponseEntity<IncidentResponse> createIncident(Principal principal,
                                                           @RequestBody IncidentRequest request) {
        return ResponseEntity.ok(incidentService.createIncident(principal.getName(), request));
    }

    @PutMapping("/{incidentId}/status")
    @Operation(summary = "Update incident status (staff)")
    public ResponseEntity<IncidentResponse> updateStatus(Principal principal,
                                                        @PathVariable String incidentId,
                                                        @RequestParam String status,
                                                        @RequestBody(required = false) IncidentUpdateRequest body) {
        if (body != null) {
            return ResponseEntity.ok(incidentService.updateIncidentStatus(principal.getName(), incidentId, status, body));
        }
        return ResponseEntity.ok(incidentService.updateIncidentStatus(principal.getName(), incidentId, status));
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Get incident by ID")
    public ResponseEntity<IncidentResponse> getIncident(Principal principal,
                                                        @PathVariable String incidentId) {
        return ResponseEntity.ok(incidentService.getIncident(principal.getName(), incidentId));
    }

    @GetMapping
    @Operation(summary = "List all incidents (staff)")
    public ResponseEntity<List<IncidentResponse>> listAll(Principal principal) {
        return ResponseEntity.ok(incidentService.getAllIncidents(principal.getName()));
    }

    @GetMapping("/by-session/{sessionId}")
    @Operation(summary = "List incidents for a session")
    public ResponseEntity<List<IncidentResponse>> listBySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(incidentService.getIncidentsBySession(sessionId));
    }

    @GetMapping("/stats/count")
    @Operation(summary = "Count incidents by status (OPEN / RESOLVED / CANCELLED)")
    public ResponseEntity<Map<String, Object>> countByStatus(@RequestParam String status) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", status.toUpperCase());
        body.put("count", incidentService.countByStatus(status));
        return ResponseEntity.ok(body);
    }

    // ======================== DRIVER REPORT ENDPOINTS ========================

    @PostMapping("/driver")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Create a driver report",
            description = "Driver gui bao cao: mat ve, khong tim thay xe, sai phi, slot bi chiem")
    public ResponseEntity<ApiResponse<IncidentResponse>> createDriverReport(
            Authentication auth,
            @RequestBody IncidentRequest request) {
        IncidentResponse response = incidentService.createDriverReport(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok("Driver report submitted", response));
    }

    @GetMapping("/driver/me")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Get driver's own reports")
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> getMyReports(Authentication auth) {
        List<IncidentResponse> reports = incidentService.getDriverReports(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Driver reports retrieved", reports));
    }

    // ======================== STAFF MANAGE DRIVER REPORTS ========================

    @GetMapping("/driver/all")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    @Operation(summary = "List all driver reports (staff)")
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> listAllDriverReports() {
        List<IncidentResponse> reports = incidentService.getAllDriverReports();
        return ResponseEntity.ok(ApiResponse.ok("All driver reports retrieved", reports));
    }
}