package fpt.swp391.parkingmanagement.repository;

import java.math.BigDecimal;

public interface PaymentMethodProjection {
    String getPaymentMethod();
    BigDecimal getTotalRevenue();
    Long getCount();
}
