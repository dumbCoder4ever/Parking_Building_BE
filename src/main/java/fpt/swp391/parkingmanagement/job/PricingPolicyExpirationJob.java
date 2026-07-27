package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.repository.PricingPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PricingPolicyExpirationJob {

    private final PricingPolicyRepository pricingPolicyRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void updateExpiredPolicies() {
        try {
            int updated = pricingPolicyRepository.updateExpiredPolicies();
            if (updated > 0) {
                log.info("Deactivated {} expired pricing policies", updated);
            }
        } catch (Exception e) {
            log.error("Error deactivating expired pricing policies", e);
        }
    }
}
