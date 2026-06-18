package com.immobilier.backend.service;

import com.immobilier.backend.entity.Property;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyScheduler {

    private final PropertyRepository propertyRepository;

    /**
     * Runs every hour. Finds LOUE properties whose rental period has expired and
     * resets them to DISPONIBLE so they can be listed again.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void unlockExpiredRentals() {
        LocalDateTime now = LocalDateTime.now();
        List<Property> locked = propertyRepository.findByStatut("LOUE");
        int unlocked = 0;
        for (Property p : locked) {
            if (p.getRentalEndDate() != null && now.isAfter(p.getRentalEndDate())) {
                p.setStatut("DISPONIBLE");
                p.setRentalStartDate(null);
                p.setRentalEndDate(null);
                propertyRepository.save(p);
                unlocked++;
                log.info("Propriété ID {} déverrouillée (location expirée le {})", p.getId(), p.getRentalEndDate());
            }
        }
        if (unlocked > 0) {
            log.info("Scheduler: {} propriété(s) remise(s) à DISPONIBLE après expiration de la location", unlocked);
        }
    }
}
