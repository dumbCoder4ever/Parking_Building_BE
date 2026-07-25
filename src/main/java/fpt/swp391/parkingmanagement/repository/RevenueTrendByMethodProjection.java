package fpt.swp391.parkingmanagement.repository;

import java.math.BigDecimal;

public interface RevenueTrendByMethodProjection {
    String getDate();
    String getPaymentMethod();
    BigDecimal getRevenue();
    Long getCount();
}
