package fpt.swp391.parkingmanagement.repository;

import java.math.BigDecimal;

public interface RevenueTrendProjection {
    String getDate();
    BigDecimal getRevenue();
    Long getCount();
}
