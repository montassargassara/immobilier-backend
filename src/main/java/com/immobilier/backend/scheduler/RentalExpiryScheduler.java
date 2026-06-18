package com.immobilier.backend.scheduler;

import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.PropertyRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily job that re-opens properties whose rental period has ended.
 *
 * Runs at 02:00 every day. For each LOUE property where rentalEndDate < now:
 * 1. statut → DISPONIBLE
 * 2. rental dates cleared
 * 3. isActive kept true (property remains listed)
 * 4. Agency admin (or super admins if no agency) notified
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentalExpiryScheduler {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 2 * * *")   // 02:00 every day
    @Transactional
    public void checkExpiredRentals() {
        LocalDateTime now = LocalDateTime.now();
        List<Property> expired = propertyRepository.findExpiredRentals(now);

        if (expired.isEmpty()) {
            log.debug("RentalExpiryScheduler: no expired rentals found at {}", now);
            return;
        }

        log.info("RentalExpiryScheduler: processing {} expired rental(s)", expired.size());

        for (Property property : expired) {
            try {
                reopenProperty(property);
            } catch (Exception e) {
                log.error("RentalExpiryScheduler: failed to reopen property {}: {}",
                        property.getId(), e.getMessage(), e);
            }
        }
    }

    private void reopenProperty(Property property) {
        String previousEndDate = property.getRentalEndDate() != null
                ? property.getRentalEndDate().toLocalDate().toString()
                : "?";

        property.setStatut("DISPONIBLE");
        property.setRentalStartDate(null);
        property.setRentalEndDate(null);
        property.setRentalDurationMonths(null);
        propertyRepository.save(property);

        log.info("Property {} '{}' re-opened: rental ended on {}",
                property.getId(), property.getTitre(), previousEndDate);

        notifyAdmin(property, previousEndDate);
    }

    private void notifyAdmin(Property property, String endDate) {
        String title  = "Location terminée — bien remis en disponible";
        String msg    = String.format(
                "La location du bien « %s » s'est terminée le %s. "
                + "Le bien est automatiquement remis en DISPONIBLE.",
                property.getTitre(), endDate);

        // Notify agency admin if the property has one
        User agencyAdmin = property.getAgencyAdmin();
        if (agencyAdmin != null) {
            notificationService.create(agencyAdmin, NotificationType.PROPERTY_AVAILABLE_AGAIN,
                    title, msg, property.getId());
            return;
        }

        // Fallback: notify all super admins
        List<User> superAdmins = userRepository.findByRole(RoleType.SUPER_ADMIN);
        for (User sa : superAdmins) {
            notificationService.create(sa, NotificationType.PROPERTY_AVAILABLE_AGAIN,
                    title, msg, property.getId());
        }
    }
}
