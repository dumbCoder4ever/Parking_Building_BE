package fpt.swp391.parkingmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import fpt.swp391.parkingmanagement.dto.PlateDuplicateInfo;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.exception.BaseAPIException;
import fpt.swp391.parkingmanagement.exception.ErrorCode;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlateRecognizerService {

    private final WebClient plateRecognizerWebClient;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        String masked = apiKey == null ? "<null>"
                : apiKey.isEmpty() ? "<empty>"
                : apiKey.length() >= 8 ? apiKey.substring(0, 8) + "***" : "***";
        // #region agent log
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("c:/Users/Admin/Downloads/BE/debug-72f91e.log"),
                    java.util.Collections.singletonList(String.format(
                            "{\"sessionId\":\"72f91e\",\"location\":\"PlateRecognizerService.init\",\"message\":\"loaded config\",\"data\":{\"apiKeyMasked\":\"%s\",\"apiKeyLength\":%d,\"apiUrl\":\"%s\"},\"timestamp\":%d,\"hypothesisId\":\"H1\"}\n",
                            masked, apiKey == null ? -1 : apiKey.length(),
                            apiUrl.replace("\\", "\\\\"), System.currentTimeMillis())),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        // #endregion
        log.info("PlateRecognizer config loaded: apiKey={}, apiUrl={}", masked, apiUrl);
    }

    @Value("${plate-recognizer.api-key:}")
    private String apiKey;

    @Value("${plate-recognizer.api-url:https://api.platerecognizer.com/v1/plate-reader/}")
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

        log.info("Plate Recognizer OCR processing URL: {}", imageUrl);

        try {
            String responseJson = plateRecognizerWebClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Token " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"url\": \"" + imageUrl + "\"}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("Plate Recognizer raw response: {}", responseJson);
            return parseJsonResponse(responseJson, "url:" + imageUrl);

        } catch (Exception e) {
            log.error("Plate Recognizer OCR failed for URL: {}", imageUrl, e);
            throw new BaseAPIException(ErrorCode.OCR_FAILED, "OCR failed: " + e.getMessage());
        }
    }

    /**
     * OCR từ file upload (multipart/form-data)
     */
    public OcrResult recognizeFromUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty");
        }

        log.info("Plate Recognizer OCR processing upload: {} ({} bytes), apiUrl={}",
                file.getOriginalFilename(), file.getSize(), apiUrl);

        try {
            byte[] fileBytes = file.getBytes();
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "image.jpg";

            MediaType mediaType = MediaType.IMAGE_JPEG;
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("png")) {
                    mediaType = MediaType.IMAGE_PNG;
                } else if (contentType.contains("gif")) {
                    mediaType = MediaType.IMAGE_GIF;
                }
            }

            org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> parts =
                    new org.springframework.util.LinkedMultiValueMap<>();
            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(fileBytes) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    };
            org.springframework.http.HttpHeaders partHeaders = new org.springframework.http.HttpHeaders();
            partHeaders.setContentType(mediaType);
            parts.add("upload", new org.springframework.http.HttpEntity<>(resource, partHeaders));

            String maskedKey = apiKey != null && apiKey.length() >= 8
                    ? apiKey.substring(0, 8) + "***" : "***";
            String authHeaderPreview = "Token " + maskedKey;

            // #region agent log
            StringBuilder probe = new StringBuilder();
            probe.append("{\"sessionId\":\"72f91e\",\"location\":\"PlateRecognizerService.upload.beforeCall\",\"message\":\"about to POST\",\"data\":");
            probe.append("{\"apiUrl\":\"").append(apiUrl.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",");
            probe.append("\"authHeader\":\"").append(authHeaderPreview).append("\",");
            probe.append("\"fileName\":\"").append(filename.replace("\"", "\\\"")).append("\",");
            probe.append("\"fileSize\":").append(fileBytes.length).append(",");
            probe.append("\"contentType\":\"").append(mediaType.toString()).append("\"},");
            probe.append("\"timestamp\":").append(System.currentTimeMillis()).append(",\"hypothesisId\":\"H2\"}\n");
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("c:/Users/Admin/Downloads/BE/debug-72f91e.log"),
                        java.util.Collections.singletonList(probe.toString()),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
            // #endregion

            String responseJson = plateRecognizerWebClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Token " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::isError, clientResponse -> {
                        // #region agent log
                        StringBuilder errLog = new StringBuilder();
                        errLog.append("{\"sessionId\":\"72f91e\",\"location\":\"PlateRecognizerService.upload.errorStatus\",\"message\":\"HTTP error status\",\"data\":");
                        errLog.append("{\"statusCode\":").append(clientResponse.statusCode().value()).append(",");
                        errLog.append("\"reasonPhrase\":\"").append(clientResponse.statusCode().toString()).append("\"},");
                        errLog.append("\"timestamp\":").append(System.currentTimeMillis()).append(",\"hypothesisId\":\"H4\"}\n");
                        try {
                            java.nio.file.Files.write(java.nio.file.Paths.get("c:/Users/Admin/Downloads/BE/debug-72f91e.log"),
                                    java.util.Collections.singletonList(errLog.toString()),
                                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        } catch (Exception ignored) {}
                        // #endregion
                        return clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    // #region agent log
                                    StringBuilder bodyLog = new StringBuilder();
                                    bodyLog.append("{\"sessionId\":\"72f91e\",\"location\":\"PlateRecognizerService.upload.errorBody\",\"message\":\"error response body\",\"data\":");
                                    bodyLog.append("{\"bodyPreview\":\"").append(body.substring(0, Math.min(500, body.length())).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\"},");
                                    bodyLog.append("\"timestamp\":").append(System.currentTimeMillis()).append(",\"hypothesisId\":\"H5\"}\n");
                                    try {
                                        java.nio.file.Files.write(java.nio.file.Paths.get("c:/Users/Admin/Downloads/BE/debug-72f91e.log"),
                                                java.util.Collections.singletonList(bodyLog.toString()),
                                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                                    } catch (Exception ignored) {}
                                    // #endregion
                                    return reactor.core.publisher.Mono.error(new org.springframework.web.reactive.function.client.WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            clientResponse.statusCode().toString(),
                                            clientResponse.headers().asHttpHeaders(),
                                            body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                            java.nio.charset.StandardCharsets.UTF_8));
                                });
                    })
                    .bodyToMono(String.class)
                    .block();

            log.debug("Plate Recognizer raw response: {}", responseJson);
            return parseJsonResponse(responseJson, "upload:" + filename);

        } catch (IOException e) {
            log.error("Failed to read file bytes for OCR upload: {}",
                    file.getOriginalFilename(), e);
            throw new BaseAPIException(ErrorCode.OCR_FAILED,
                    "OCR failed: cannot read uploaded file: " + e.getMessage());
        } catch (Exception e) {
            String maskedKey = apiKey != null && apiKey.length() >= 8
                    ? apiKey.substring(0, 8) + "***" : "***";
            log.error("Plate Recognizer OCR failed for upload: apiUrl={}, key={}",
                    apiUrl, maskedKey, e);
            throw new BaseAPIException(ErrorCode.OCR_FAILED, "OCR failed: " + e.getMessage());
        }
    }

    private OcrResult parseJsonResponse(String jsonResponse, String source) {
        long start = System.currentTimeMillis();

        if (jsonResponse == null || jsonResponse.isBlank()) {
            log.warn("Plate Recognizer returned empty response for {}", source);
            return buildEmptyResult();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode results = root.path("results");

            if (results.isMissingNode() || !results.isArray() || results.isEmpty()) {
                log.info("No plates detected in response for {}", source);
                return buildEmptyResult();
            }

            List<String> allCandidates = new ArrayList<>();
            String bestPlate = null;
            double bestConfidence = 0;

            for (JsonNode plateNode : results) {
                String plate = plateNode.path("plate").asText(null);
                double conf = plateNode.path("score").asDouble(0);

                if (plate != null && !plate.isBlank()) {
                    // Chuẩn hóa biển số VN
                    String normalized = normalize(plate);
                    List<String> candidates = extractPlateCandidates(normalized);

                    for (String candidate : candidates) {
                        if (!allCandidates.contains(candidate)) {
                            allCandidates.add(candidate);
                        }
                    }

                    if (conf > bestConfidence && !candidates.isEmpty()) {
                        bestConfidence = conf;
                        bestPlate = pickBest(candidates);
                    }
                }
            }

            if (allCandidates.isEmpty() || bestPlate == null) {
                log.info("No valid plate candidates found for {}", source);
                return buildEmptyResult();
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("Plate Recognizer OCR result ({}ms) [{}]: plate={}, confidence={}, candidates={}",
                    elapsed, source, bestPlate, bestConfidence, allCandidates);

            // Check duplicate active session
            PlateDuplicateInfo duplicate = null;
            Optional<PlateDuplicateInfo> dupOpt = findActiveSessionByPlate(bestPlate);
            if (dupOpt.isPresent()) {
                duplicate = dupOpt.get();
                log.info("Found duplicate active session for plate {}: sessionId={}",
                        bestPlate, duplicate.sessionId());
            }

            return new OcrResult(
                    bestPlate,
                    bestPlate,
                    bestPlate,
                    allCandidates,
                    bestConfidence,
                    duplicate
            );

        } catch (Exception e) {
            log.error("Failed to parse Plate Recognizer response: {}", jsonResponse, e);
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

    private OcrResult buildEmptyResult() {
        return new OcrResult(null, null, null, List.of(), 0.0, null);
    }

    /**
     * Tìm active session theo biển số
     */
    public Optional<PlateDuplicateInfo> findActiveSessionByPlate(String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            return Optional.empty();
        }
        return parkingSessionRepository.findActiveGuestByPlateNumber(plateNumber)
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

    // ========== OcrResult record ==========

    public record OcrResult(
            String rawText,
            String normalizedText,
            String plateNumber,
            List<String> candidates,
            double confidence,
            PlateDuplicateInfo duplicateActiveSession
    ) {}
}
