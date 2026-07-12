package fpt.swp391.parkingmanagement.dto;

import java.util.List;

public record OcrResponse(
        String plateNumber,
        List<String> candidates,
        String rawText,
        String normalizedText,
        double confidence,
        PlateDuplicateInfo duplicateActiveSession
) {
}