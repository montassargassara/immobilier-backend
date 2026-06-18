package com.immobilier.backend.service;

import com.immobilier.backend.dto.AgencyAffiliateCommissionDTO;
import com.immobilier.backend.dto.AgencyAffiliateDTO;
import com.immobilier.backend.dto.AgencyAffiliateStatsDTO;
import com.immobilier.backend.entity.AffiliateTransaction;
import com.immobilier.backend.entity.SaleOffer;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.repository.AffiliateProfileRepository;
import com.immobilier.backend.repository.AffiliateTransactionRepository;
import com.immobilier.backend.repository.SaleOfferRepository;
import com.immobilier.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgencyAffiliateService {

    private final SecurityUtils securityUtils;
    private final SaleOfferRepository saleOfferRepository;
    private final AffiliateTransactionRepository transactionRepository;
    private final AffiliateProfileRepository affiliateProfileRepository;

    // ── Stats aggregées ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgencyAffiliateStatsDTO getAgencyStats() {
        Long adminId = securityUtils.getCurrentUser().getId();

        List<AffiliateTransaction> txns = transactionRepository.findByAgencyAdminIdOrderByDateDesc(adminId);
        List<User> affiliates = saleOfferRepository.findDistinctAffiliatesForAgency(adminId);
        List<SaleOffer> allOffers = saleOfferRepository.findByAgencyAdminIdOrderByCreatedAtDesc(adminId);

        double paid    = txns.stream().filter(t -> Boolean.TRUE.equals(t.getIsPaid()))
                             .mapToDouble(AffiliateTransaction::getCommissionAmount).sum();
        double pending = txns.stream().filter(t -> !Boolean.TRUE.equals(t.getIsPaid()))
                             .mapToDouble(AffiliateTransaction::getCommissionAmount).sum();
        long distinctClients = allOffers.stream().map(SaleOffer::getBuyerEmail).distinct().count();

        AgencyAffiliateStatsDTO dto = new AgencyAffiliateStatsDTO();
        dto.setTotalAffiliates(affiliates.size());
        dto.setTotalSalesViaAffiliation(txns.size());
        dto.setTotalCommissionsPaid(paid);
        dto.setTotalCommissionsPending(pending);
        dto.setTotalCommissionsGlobal(paid + pending);
        dto.setTotalClientsApportes((int) distinctClients);
        return dto;
    }

    // ── Liste des affiliés ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyAffiliateDTO> getAffiliatesForAgency() {
        Long adminId = securityUtils.getCurrentUser().getId();
        List<User> affiliates = saleOfferRepository.findDistinctAffiliatesForAgency(adminId);

        return affiliates.stream().map(affiliate -> {
            AgencyAffiliateDTO dto = new AgencyAffiliateDTO();
            dto.setAffiliateId(affiliate.getId());
            dto.setPrenom(affiliate.getPrenom());
            dto.setNom(affiliate.getNom());
            dto.setEmail(affiliate.getEmail());
            dto.setTelephone(affiliate.getTelephone());

            long clients = saleOfferRepository.countOffersForAffiliateAndAgency(affiliate.getId(), adminId);
            dto.setClientsApportes((int) clients);

            List<AffiliateTransaction> txns = transactionRepository
                    .findByAffiliateIdAndAgencyAdminId(affiliate.getId(), adminId);
            dto.setBiensVendus(txns.size());
            dto.setTotalCommissions(txns.stream().mapToDouble(AffiliateTransaction::getCommissionAmount).sum());

            affiliateProfileRepository.findByUserId(affiliate.getId())
                    .ifPresent(p -> dto.setStatus(p.getStatus()));

            return dto;
        }).collect(Collectors.toList());
    }

    // ── Commissions récentes ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyAffiliateCommissionDTO> getRecentCommissions(int limit) {
        Long adminId = securityUtils.getCurrentUser().getId();
        return transactionRepository.findByAgencyAdminIdOrderByDateDesc(adminId)
                .stream()
                .limit(limit)
                .map(this::toCommissionDTO)
                .collect(Collectors.toList());
    }

    private AgencyAffiliateCommissionDTO toCommissionDTO(AffiliateTransaction t) {
        AgencyAffiliateCommissionDTO dto = new AgencyAffiliateCommissionDTO();
        dto.setTransactionId(t.getId());
        dto.setAffiliatePrenom(t.getAffiliate().getPrenom());
        dto.setAffiliateNom(t.getAffiliate().getNom());
        dto.setPropertyTitre(t.getProperty().getTitre());
        dto.setPropertyAdresse(t.getProperty().getAdresse());
        dto.setPropertyCity(t.getProperty().getCity());
        dto.setPropertyPrice(t.getPropertyPrice());
        dto.setCommissionPercentage(t.getCommissionPercentage());
        dto.setCommissionAmount(t.getCommissionAmount());
        dto.setTransactionType(t.getTransactionType());
        dto.setTransactionDate(t.getTransactionDate());
        dto.setIsPaid(t.getIsPaid());
        dto.setClientEmail(t.getClientEmail());
        return dto;
    }
}
