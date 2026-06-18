package com.immobilier.backend.config;

import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.PropertyValidationStatus;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-shot backfill for properties created before the validation workflow shipped.
 *
 *   validationStatus → APPROVED   (existing data was implicitly live)
 *   commissionLocked → true       (preserve current commission, prevent lower-role edits)
 *   priceLocked      → true
 *   ownerRole        → SUPER_ADMIN for SUPER_ADMIN_OWNED, ADMIN otherwise
 *   createdBy        → agencyAdmin (best available author for legacy rows)
 *
 * Idempotent: only touches rows where validationStatus IS NULL.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PropertyValidationBackfillRunner {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    @Bean
    public ApplicationRunner backfillPropertyValidation() {
        return args -> backfill();
    }

    @Transactional
    public void backfill() {
        List<Property> legacy = propertyRepository.findAllNeedingValidationBackfill();
        if (legacy.isEmpty()) {
            return;
        }
        log.info("Backfilling validationStatus on {} legacy properties", legacy.size());

        User firstSuperAdmin = userRepository.findByRole(RoleType.SUPER_ADMIN).stream()
                .findFirst().orElse(null);

        for (Property p : legacy) {
            p.setValidationStatus(PropertyValidationStatus.APPROVED);
            p.setCommissionLocked(true);
            p.setPriceLocked(true);

            boolean isSuperAdminOwned = "SUPER_ADMIN_OWNED".equals(p.getOwnerType());
            p.setOwnerRole(isSuperAdminOwned ? RoleType.SUPER_ADMIN : RoleType.ADMIN);

            if (p.getCreatedBy() == null) {
                if (isSuperAdminOwned && firstSuperAdmin != null) {
                    p.setCreatedBy(firstSuperAdmin);
                } else if (p.getAgencyAdmin() != null) {
                    p.setCreatedBy(p.getAgencyAdmin());
                }
            }
        }
        propertyRepository.saveAll(legacy);
        log.info("Backfill completed for {} properties", legacy.size());
    }
}
