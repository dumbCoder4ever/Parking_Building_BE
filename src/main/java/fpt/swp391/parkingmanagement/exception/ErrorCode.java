package fpt.swp391.parkingmanagement.exception;

public enum ErrorCode {
    USER_NOT_FOUND("User not found"),
    VEHICLE_NOT_FOUND("Vehicle not found"),
    VEHICLE_ALREADY_EXISTS("Vehicle with this plate number already exists"),
    VEHICLE_TYPE_NOT_FOUND("Vehicle type not found"),
    RESERVATION_NOT_FOUND("Reservation not found"),
    UNAUTHORIZED("You are not authorized to perform this action"),
    FORBIDDEN("You do not have permission to access this resource"),
    INVALID_REQUEST("Invalid request"),
    INTERNAL_ERROR("Internal server error"),
    TICKET_NOT_FOUND("Ticket not found"),
    TICKET_ALREADY_USED("Ticket has already been used"),
    TICKET_EXPIRED("Ticket has expired"),
    RESERVATION_NOT_APPROVED("Reservation has not been approved yet"),
    RESERVATION_EXPIRED("Reservation has expired"),
    CHECKIN_TOO_EARLY("Cannot check in before reservation start time"),
    SLOT_NOT_RESERVED("Slot is not in reserved status"),
    SLOT_NOT_FOUND("Slot not found"),
    SESSION_NOT_FOUND("Active parking session not found for this ticket"),
    PLATE_NUMBER_MISMATCH("Plate number does not match reservation"),
    CHECKIN_TIME_MISSING("Check-in time is missing for this session"),
    BAD_REQUEST("Bad request"),
    SLOT_OCCUPIED("Slot is already occupied"),
    PAYMENT_NOT_COMPLETED("Payment not completed"),
    PAYMENT_NOT_FOUND("Payment not found"),
    PAYMENT_NOT_CONFIRMED("Payment has not been confirmed yet"),
    PAYMENT_ALREADY_CONFIRMED("Payment has already been confirmed"),
    BUILDING_NOT_FOUND("Building not found"),
    SLOT_NOT_AVAILABLE("Slot is not available for parking"),
    GUEST_SESSION_NOT_FOUND("Active guest session not found"),
    OCR_FAILED("OCR failed to recognize plate number from image"),
    PLATE_MISMATCH("Plate number scanned does not match the registered plate for this session"),
    PLATE_ALREADY_PARKED("This plate number is already registered in an active parking session"),
    GUEST_ALREADY_PARKING("This vehicle is already checked in as a guest"),
    OCR_PLATE_NOT_DETECTED("Could not detect a license plate in the image"),
    RESERVATION_EXISTS_FOR_PLATE("This plate number already has an active reservation");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}