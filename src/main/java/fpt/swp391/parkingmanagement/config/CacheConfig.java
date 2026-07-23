package fpt.swp391.parkingmanagement.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "vehicleTypes",
                "pricingPolicies",
                "buildings",
                "floors",
                "zones",
                "slotConfigs",
                "dashboardStats",
                "revenueDashboard",
                "buildingsAvailable",
                "buildingFloors",
                "managerBuildings",
                "managerFloors",
                "managerZones",
                "managerSlots");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(300, TimeUnit.SECONDS));
        // Admin dashboard can be slightly stale; short TTL avoids COUNT storm on refresh
        cacheManager.registerCustomCache("dashboardStats",
                Caffeine.newBuilder()
                        .maximumSize(50)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());
        // Manager revenue dashboard — same freshness window as admin stats
        cacheManager.registerCustomCache("revenueDashboard",
                Caffeine.newBuilder()
                        .maximumSize(50)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());
        // Availability picker: 15s TTL — fast repeat opens, still fairly fresh counts
        cacheManager.registerCustomCache("buildingsAvailable",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(15, TimeUnit.SECONDS)
                        .build());
        cacheManager.registerCustomCache("buildingFloors",
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());
        // Manager parking-space cascading reads (remote MySQL) — same APIs, faster repeats
        cacheManager.registerCustomCache("managerBuildings",
                Caffeine.newBuilder()
                        .maximumSize(20)
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .build());
        cacheManager.registerCustomCache("managerFloors",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());
        cacheManager.registerCustomCache("managerZones",
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .build());
        // Slot statuses change often — keep short
        cacheManager.registerCustomCache("managerSlots",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(10, TimeUnit.SECONDS)
                        .build());
        return cacheManager;
    }
}
