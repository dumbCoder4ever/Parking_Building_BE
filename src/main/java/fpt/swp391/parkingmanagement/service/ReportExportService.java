package fpt.swp391.parkingmanagement.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import fpt.swp391.parkingmanagement.dto.BuildingRevenueResponse;
import fpt.swp391.parkingmanagement.dto.DashboardStatsResponse;
import fpt.swp391.parkingmanagement.dto.OccupancyStatsResponse;
import fpt.swp391.parkingmanagement.dto.PeakHourAnalysisResponse;
import fpt.swp391.parkingmanagement.dto.PeakHourBucketResponse;
import fpt.swp391.parkingmanagement.dto.RevenueDashboardResponse;
import fpt.swp391.parkingmanagement.entity.Incident;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    public static final String TYPE_REVENUE = "REVENUE";
    public static final String TYPE_INCIDENT = "INCIDENT";
    public static final String TYPE_OCCUPANCY = "OCCUPANCY";
    public static final String TYPE_PEAK_HOUR = "PEAK_HOUR";
    public static final String TYPE_DRIVER_REPORT = "DRIVER_REPORT";

    public static final String FORMAT_EXCEL = "EXCEL";
    public static final String FORMAT_PDF = "PDF";

    private final RevenueDashboardService revenueDashboardService;
    private final DashboardStatsService dashboardStatsService;
    private final PeakHourService peakHourService;
    private final IncidentRepository incidentRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ExportFile export(
            String reportType,
            String format,
            String buildingId,
            LocalDate fromDay,
            LocalDate toDay) {
        String type = normalize(reportType);
        String fmt = normalize(format);
        LocalDate from = fromDay != null ? fromDay : LocalDate.now().minusDays(6);
        LocalDate to = toDay != null ? toDay : LocalDate.now();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        List<String> headers;
        List<List<String>> rows;
        String title;

        switch (type) {
            case TYPE_REVENUE -> {
                title = "Revenue Report";
                headers = List.of("Building ID", "Building Name", "Total Revenue", "Payment Count");
                RevenueDashboardResponse revenue = revenueDashboardService.getRevenueDashboard(fromDt, toDt);
                rows = new ArrayList<>();
                for (BuildingRevenueResponse b : revenue.getBuildings()) {
                    if (buildingId != null && !buildingId.isBlank()
                            && !buildingId.equals(b.getBuildingId())) {
                        continue;
                    }
                    rows.add(List.of(
                            nvl(b.getBuildingId()),
                            nvl(b.getBuildingName()),
                            b.getTotalRevenue() != null ? b.getTotalRevenue().toPlainString() : "0",
                            String.valueOf(b.getPaymentCount())));
                }
                rows.add(0, List.of(
                        "TOTAL",
                        "",
                        revenue.getTotalRevenue() != null ? revenue.getTotalRevenue().toPlainString() : "0",
                        String.valueOf(revenue.getTotalPaymentCount())));
            }
            case TYPE_INCIDENT -> {
                title = "Incident Report";
                headers = List.of("Incident ID", "Type", "Status", "Plate", "Ticket", "Description", "Created At");
                rows = new ArrayList<>();
                for (Incident i : incidentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromDt, toDt)) {
                    String plate = i.getSession() != null && i.getSession().getVehicle() != null
                            ? i.getSession().getVehicle().getPlateNumber() : "";
                    String ticket = i.getSession() != null && i.getSession().getTicket() != null
                            ? i.getSession().getTicket().getTicketCode() : "";
                    rows.add(List.of(
                            nvl(i.getIncidentId()),
                            nvl(i.getIncidentType()),
                            nvl(i.getStatus()),
                            nvl(plate),
                            nvl(ticket),
                            nvl(i.getDescription()),
                            i.getCreatedAt() != null ? i.getCreatedAt().toString() : ""));
                }
            }
            case TYPE_OCCUPANCY -> {
                title = "Occupancy Report";
                headers = List.of("Metric", "Value");
                DashboardStatsResponse stats = dashboardStatsService.getStats(from, to, blankToNull(buildingId));
                OccupancyStatsResponse occ = stats.getOccupancy();
                rows = List.of(
                        List.of("Total Slots", String.valueOf(occ != null ? occ.getTotalSlots() : 0)),
                        List.of("Available", String.valueOf(occ != null ? occ.getAvailableSlots() : 0)),
                        List.of("Occupied", String.valueOf(occ != null ? occ.getOccupiedSlots() : 0)),
                        List.of("Reserved", String.valueOf(occ != null ? occ.getReservedSlots() : 0)),
                        List.of("Pending Exit", String.valueOf(occ != null ? occ.getPendingExitSlots() : 0)),
                        List.of("Occupancy Rate", String.valueOf(occ != null ? occ.getOccupancyRate() : 0)));
            }
            case TYPE_PEAK_HOUR -> {
                title = "Peak Hour Report";
                headers = List.of("Hour", "Session Count", "Peak");
                PeakHourAnalysisResponse peak = peakHourService.analyze(blankToNull(buildingId), from, to);
                rows = new ArrayList<>();
                if (peak.getBuckets() != null) {
                    for (PeakHourBucketResponse b : peak.getBuckets()) {
                        rows.add(List.of(
                                String.valueOf(b.getHour()),
                                String.valueOf(b.getSessionCount()),
                                b.isPeak() ? "YES" : "NO"));
                    }
                }
            }
            case TYPE_DRIVER_REPORT -> {
                title = "Driver Report";
                headers = List.of("Note");
                rows = List.of(List.of("Driver Report entity is not implemented yet"));
            }
            default -> throw new BaseAPIException(ErrorCode.INVALID_REQUEST,
                    "Invalid reportType. Allowed: REVENUE, INCIDENT, OCCUPANCY, PEAK_HOUR, DRIVER_REPORT");
        }

        byte[] bytes;
        String contentType;
        String filename;
        try {
            if (FORMAT_EXCEL.equals(fmt)) {
                bytes = toExcel(title, headers, rows);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                filename = type.toLowerCase(Locale.ROOT) + "_" + from + "_" + to + ".xlsx";
            } else if (FORMAT_PDF.equals(fmt)) {
                bytes = toPdf(title + " (" + from + " → " + to + ")", headers, rows);
                contentType = "application/pdf";
                filename = type.toLowerCase(Locale.ROOT) + "_" + from + "_" + to + ".pdf";
            } else {
                throw new BaseAPIException(ErrorCode.INVALID_REQUEST, "Invalid format. Allowed: EXCEL, PDF");
            }
        } catch (IOException | DocumentException e) {
            throw new BaseAPIException(ErrorCode.INTERNAL_ERROR, "Failed to generate report: " + e.getMessage());
        }

        auditLogService.record(
                "REPORT_EXPORT",
                "REPORT",
                type,
                blankToNull(buildingId),
                null,
                fmt,
                "Exported " + type + " as " + fmt,
                null);

        return new ExportFile(filename, contentType, bytes);
    }

    private byte[] toExcel(String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(title.length() > 31 ? title.substring(0, 31) : title);
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(title);

            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            int r = 2;
            for (List<String> row : rows) {
                Row excelRow = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    excelRow.createCell(c).setCellValue(row.get(c));
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] toPdf(String title, List<String> headers, List<List<String>> rows) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        document.add(new Paragraph(title, titleFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, cellFont));
            cell.setBackgroundColor(new Color(230, 230, 230));
            table.addCell(cell);
        }
        for (List<String> row : rows) {
            for (String value : row) {
                table.addCell(new Phrase(value != null ? value : "", cellFont));
            }
            for (int i = row.size(); i < headers.size(); i++) {
                table.addCell(new Phrase("", cellFont));
            }
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BaseAPIException(ErrorCode.BAD_REQUEST, "reportType and format are required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    public record ExportFile(String filename, String contentType, byte[] content) {}
}
