package fpt.swp391.parkingmanagement.controller;

import fpt.swp391.parkingmanagement.dto.OcrRequest;
import fpt.swp391.parkingmanagement.dto.OcrResponse;
import fpt.swp391.parkingmanagement.service.OcrService;
import fpt.swp391.parkingmanagement.service.OcrService.OcrResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
@Tag(name = "ocr-controller")
public class OcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/plate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OcrResponse> detectFromUrl(@RequestBody OcrRequest request) {
        log.info("OCR detect from URL: {}", request.imageUrl());
        OcrResult result = ocrService.recognizeFromUrl(request.imageUrl());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping(value = "/plate/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrResponse> detectFromUpload(@RequestParam("file") MultipartFile file) {
        log.info("OCR detect from upload: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());
        OcrResult result = ocrService.recognizeFromUpload(file);
        return ResponseEntity.ok(toResponse(result));
    }

    private OcrResponse toResponse(OcrResult r) {
        return new OcrResponse(
                r.plateNumber(),
                r.candidates(),
                r.rawText(),
                r.normalizedText(),
                r.confidence());
    }
}