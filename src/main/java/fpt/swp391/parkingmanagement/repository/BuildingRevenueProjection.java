package fpt.swp391.parkingmanagement.repository;

import java.math.BigDecimal;

public interface BuildingRevenueProjection {

    String getBuildingId();

    String getBuildingName();

    BigDecimal getTotalRevenue();

    Long getPaymentCount();
}
