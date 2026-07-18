package fpt.swp391.parkingmanagement.enums;

public final class EnumParser {

    private EnumParser() {
    }

    public static PaymentStatus parsePaymentStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static SessionPaymentStatus parseSessionPaymentStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SessionPaymentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static PaidStatus resolvePaidStatus(String paymentStatus) {
        PaymentStatus status = parsePaymentStatus(paymentStatus);
        if (status == null) {
            return PaidStatus.UNPAID;
        }
        return switch (status) {
            case PAID -> PaidStatus.PAID;
            case FAILED, PENDING -> PaidStatus.UNPAID;
        };
    }
}
