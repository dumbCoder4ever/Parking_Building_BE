package fpt.swp391.parkingmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import fpt.swp391.parkingmanagement.dto.PlateDuplicateInfo;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class FptAiOcrService {

    private final WebClient fptAiWebClient;
    private final ParkingSessionRepository parkingSessionRepository;

    @Value("${fpt.ai.api.key:c0WXiFmRoccngoLy80ndy0JzAhcidoDK}")
    private String apiKey;

    @Value("${fpt.ai.api.url:https://api.fpt.ai/vision/ocr/vn}")
    private String apiUrl;

    // Pattern cho biển số VN: 30A-123.45, 51H-999.99, 30A1-23456
    private static final Pattern VN_PLATE_PATTERN =
            Pattern.compile("\\b\\d{2}[A-Z]{1,2}[\\s.\\-]?\\d{3,5}(?:[\\.\\-]?\\d{2})?\\b");

    /**
     * OCR từ URL ảnh
     */
    public OcrResult recognizeFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("imageUrl is required");
        }

        log.info("FPT.AI OCR processing URL: {}", imageUrl);

        try {
            // Gửi request với URL ảnh
            String responseJson = fptAiWebClient.post()
                    .uri(apiUrl)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("url", imageUrl))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("FPT.AI raw response: {}", responseJson);
            return parseJsonResponse(responseJson, "url:" + imageUrl, apiUrl);

        } catch (Exception e) {
            log.error("FPT.AI OCR failed for URL: {}", imageUrl, e);
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        }
    }

    /**
     * OCR từ file upload (multipart/form-data)
     */
    public OcrResult recognizeFromUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty");
        }

        log.info("FPT.AI OCR processing upload: {} ({} bytes), apiUrl={}",
                file.getOriginalFilename(), file.getSize(), apiUrl);

        try {
            byte[] fileBytes = file.getBytes();
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "image.jpg";

            // Xác định content type từ file
            MediaType mediaType = MediaType.IMAGE_JPEG;
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("png")) {
                    mediaType = MediaType.IMAGE_PNG;
                } else if (contentType.contains("gif")) {
                    mediaType = MediaType.IMAGE_GIF;
                }
            }

            // Dùng MultipartBodyBuilder chuẩn Spring
            org.springframework.http.client.MultipartBodyBuilder bodyBuilder =
                    new org.springframework.http.client.MultipartBodyBuilder();
            bodyBuilder.part("image", fileBytes)
                    .header("Content-Disposition",
                            "form-data; name=\"image\"; filename=\"" + filename + "\"")
                    .contentType(mediaType);

            String responseJson = fptAiWebClient.post()
                    .uri(apiUrl)
                    .header("api-key", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(bodyBuilder.build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("FPT.AI raw response: {}", responseJson);
            return parseJsonResponse(responseJson, "upload:" + filename, apiUrl);

        } catch (IOException e) {
            log.error("Failed to read file bytes for OCR upload: {}",
                    file.getOriginalFilename(), e);
            throw new RuntimeException("OCR failed: cannot read uploaded file: " + e.getMessage(), e);
        } catch (Exception e) {
            String keyPrefix = apiKey != null && apiKey.length() >= 8
                    ? apiKey.substring(0, 8) + "***" : "***";
            log.error("FPT.AI OCR failed for upload: apiUrl={}, key={}",
                    apiUrl, keyPrefix, e);
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        }
    }

    private OcrResult parseJsonResponse(String jsonResponse, String source, String apiUrl) {
        long start = System.currentTimeMillis();

        if (jsonResponse == null || jsonResponse.isBlank()) {
            log.warn("FPT.AI returned empty response for {} via {}", source, apiUrl);
            return buildEmptyResult();
        }

        try {
            // Parse JSON response tay vì response format có thể khác
            // Response format: {"status": 200, "message": "...", "data": [...]}

            // Tìm text trong response
            List<String> texts = new ArrayList<>();
            double avgConfidence = 0;
            int textCount = 0;

            // Đơn giản: tìm tất cả "text" trong JSON
            java.util.regex.Pattern textPattern =
                java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = textPattern.matcher(jsonResponse);
            while (m.find()) {
                String text = m.group(1);
                if (text != null && !text.isBlank() && text.length() > 1) {
                    texts.add(text);
                }
            }

            if (texts.isEmpty()) {
                log.warn("No text found in FPT.AI response for {}", source);
                return buildEmptyResult();
            }

            // Ghép tất cả text lại
            StringBuilder rawText = new StringBuilder();
            for (String text : texts) {
                rawText.append(text).append(" ");
            }

            String raw = rawText.toString().trim();
            String normalized = normalize(raw);
            List<String> candidates = extractPlateCandidates(normalized);
            String plateNumber = pickBest(candidates);

            // Ước tính confidence
            double finalConfidence = estimateConfidence(plateNumber, candidates);

            long elapsed = System.currentTimeMillis() - start;
            log.info("FPT.AI OCR result ({}ms) [{}]: plate={}, confidence={}, candidates={}",
                    elapsed, source, plateNumber, finalConfidence, candidates);

            return OcrResult.builder()
                    .rawText(raw)
                    .normalizedText(normalized)
                    .plateNumber(plateNumber)
                    .candidates(candidates)
                    .confidence(finalConfidence)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse FPT.AI response: {}", jsonResponse, e);
            return buildEmptyResult();
        }
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> extractPlateCandidates(String text) {
        List<String> result = new ArrayList<>();
        Matcher m = VN_PLATE_PATTERN.matcher(text);
        while (m.find()) {
            String cleaned = m.group().replaceAll("[\\s]", "").replace('.', '-');
            if (cleaned.length() >= 6) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private String pickBest(List<String> candidates) {
        if (candidates.isEmpty()) return null;
        return candidates.stream()
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
    }

    private double estimateConfidence(String plateNumber, List<String> candidates) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return 0.0;
        }

        double baseConfidence = 0.7;
        double candidatesBonus = candidates.size() > 1 ? 0.1 : 0;
        double formatBonus = isValidPlateFormat(plateNumber) ? 0.1 : 0;

        return Math.min(1.0, baseConfidence + candidatesBonus + formatBonus);
    }

    private boolean isValidPlateFormat(String plate) {
        return plate.matches("\\d{2}[A-Z]{1,2}[\\-]?\\d{3,5}([\\.\\-]\\d{2})?");
    }

    private OcrResult buildEmptyResult() {
        return OcrResult.builder()
                .rawText("")
                .normalizedText("")
                .plateNumber(null)
                .candidates(List.of())
                .confidence(0.0)
                .build();
    }

    /**
     * Tìm active session theo biển số
     */
    public Optional<PlateDuplicateInfo> findActiveSessionByPlate(String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return Optional.empty();
        }
        return parkingSessionRepository.findActiveByPlateNumber(plateNumber)
                .map(this::toPlateDuplicateInfo);
    }

    private PlateDuplicateInfo toPlateDuplicateInfo(ParkingSession ps) {
        String ticketCode = null;
        if (ps.getTicket() != null) {
            ticketCode = ps.getTicket().getTicketCode();
        }
        String buildingName = null;
        String slotName = null;
        if (ps.getSlot() != null) {
            slotName = ps.getSlot().getSlotName();
            if (ps.getSlot().getZone() != null
                    && ps.getSlot().getZone().getFloor() != null
                    && ps.getSlot().getZone().getFloor().getBuilding() != null) {
                buildingName = ps.getSlot().getZone().getFloor().getBuilding().getBuildingName();
            }
        }
        return new PlateDuplicateInfo(
                ps.getVehicle() != null ? ps.getVehicle().getPlateNumber() : null,
                ps.getSessionId(),
                ticketCode,
                ps.getCheckinTime(),
                ps.getReservation() == null,
                buildingName,
                slotName
        );
    }

    // ========== Inner DTOs ==========

    public record OcrResult(
            String rawText,
            String normalizedText,
            String plateNumber,
            List<String> candidates,
            double confidence,
            PlateDuplicateInfo duplicateActiveSession
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String rawText;
            private String normalizedText;
            private String plateNumber;
            private List<String> candidates = new ArrayList<>();
            private double confidence;
            private PlateDuplicateInfo duplicateActiveSession;

            public Builder rawText(String v) { this.rawText = v; return this; }
            public Builder normalizedText(String v) { this.normalizedText = v; return this; }
            public Builder plateNumber(String v) { this.plateNumber = v; return this; }
            public Builder candidates(List<String> v) { this.candidates = v; return this; }
            public Builder confidence(double v) { this.confidence = v; return this; }
            public Builder duplicateActiveSession(PlateDuplicateInfo v) { this.duplicateActiveSession = v; return this; }

            public OcrResult build() {
                return new OcrResult(
                        rawText,
                        normalizedText,
                        plateNumber,
                        candidates == null ? List.of() : candidates,
                        confidence,
                        duplicateActiveSession
                );
            }
        }
    }
}
