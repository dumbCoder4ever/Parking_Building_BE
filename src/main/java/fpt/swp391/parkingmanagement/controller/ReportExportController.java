package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.service.ReportExportService;
import fpt.swp391.parkingmanagement.service.ReportExportService.ExportFile;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/manager/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ReportExportController {

    private final ReportExportService reportExportService;

    @Operation(
            summary = "Export report (Excel/PDF)",
            description = "reportType: REVENUE | INCIDENT | OCCUPANCY | PEAK_HOUR | DRIVER_REPORT. "
                    + "format: EXCEL | PDF.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String reportType,
            @RequestParam(defaultValue = "EXCEL") String format,
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDay) {
        ExportFile file = reportExportService.export(reportType, format, buildingId, fromDay, toDay);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
