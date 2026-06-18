package com.immobilier.backend.dto;

import com.immobilier.backend.entity.SaleValidationRequest;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SaleValidationRequestDTO {

    private Long id;

    // Property info — field names mirror the frontend SaleValidationRequestDTO contract
    private Long   propertyId;
    private String propertyTitle;
    private String propertyType;
    private String propertyCity;
    private String propertyCountry;
    private String propertyOwnerType;
    private String propertyMainImageUrl;
    private Double propertyPrixVente;
    private Double propertyPrixLocation;

    // Requester
    private Long   requesterId;
    private String requesterName;
    private String requesterEmail;
    private String requesterRole;

    // Buyer
    private Long   buyerId;
    private String buyerName;
    private String buyerEmail;
    private String clientNom;
    private String clientPrenom;
    private String clientEmail;
    private String clientTelephone;

    // Transaction
    private String targetStatus;   // VENDU or LOUE
    private String source;         // DIRECT_SALE or CRM_LEAD
    private Long   interestRequestId;

    // Rental
    private String  rentalStartDate;
    private Integer rentalDurationMonths;
    private Double  rentalAmount;
    private String  rentalNotes;

    // Admin-entered terms at approval (null while PENDING)
    private Double        finalPrice;
    private Double        commissionPercentage;

    // Workflow
    private String        status;  // PENDING, APPROVED, REJECTED
    private String        rejectionReason;
    private String        reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public static SaleValidationRequestDTO from(SaleValidationRequest r, String apiBase) {
        SaleValidationRequestDTO dto = new SaleValidationRequestDTO();
        dto.setId(r.getId());

        // Property
        if (r.getProperty() != null) {
            dto.setPropertyId(r.getProperty().getId());
            dto.setPropertyTitle(r.getProperty().getTitre());
            dto.setPropertyType(r.getProperty().getType());
            dto.setPropertyCity(r.getProperty().getCity());
            dto.setPropertyCountry(r.getProperty().getCountry());
            dto.setPropertyOwnerType(r.getProperty().getOwnerType());
            dto.setPropertyPrixVente(r.getProperty().getPrixVente());
            dto.setPropertyPrixLocation(r.getProperty().getPrixLocation());
            if (r.getProperty().getMainImageId() != null) {
                // Relative URL — same centralized convention as every other DTO
                // (PropertyService, SaleOfferService, PublicPortalService …).
                // The frontend prepends apiBaseUrl via its imageUrl() helper.
                dto.setPropertyMainImageUrl("/api/images/public/" + r.getProperty().getMainImageId());
            }
        }

        // Requester
        if (r.getRequester() != null) {
            dto.setRequesterId(r.getRequester().getId());
            dto.setRequesterName(r.getRequester().getFullName());
            dto.setRequesterEmail(r.getRequester().getEmail());
            dto.setRequesterRole(r.getRequester().getRole() != null ? r.getRequester().getRole().name() : null);
        }

        // Buyer
        if (r.getBuyer() != null) {
            dto.setBuyerId(r.getBuyer().getId());
            dto.setBuyerName(r.getBuyer().getFullName());
            dto.setBuyerEmail(r.getBuyer().getEmail());
        }
        dto.setClientNom(r.getClientNom());
        dto.setClientPrenom(r.getClientPrenom());
        dto.setClientEmail(r.getClientEmail());
        dto.setClientTelephone(r.getClientTelephone());

        // Transaction
        dto.setTargetStatus(r.getTargetStatus());
        dto.setSource(r.getSource());
        if (r.getInterestRequest() != null) dto.setInterestRequestId(r.getInterestRequest().getId());

        // Rental
        dto.setRentalStartDate(r.getRentalStartDate());
        dto.setRentalDurationMonths(r.getRentalDurationMonths());
        dto.setRentalAmount(r.getRentalAmount());
        dto.setRentalNotes(r.getRentalNotes());

        // Admin-entered terms (set at approval)
        dto.setFinalPrice(r.getFinalPrice());
        dto.setCommissionPercentage(r.getCommissionPercentage());

        // Workflow
        dto.setStatus(r.getStatus() != null ? r.getStatus().name() : null);
        dto.setRejectionReason(r.getRejectionReason());
        if (r.getReviewedBy() != null) dto.setReviewedByName(r.getReviewedBy().getFullName());
        dto.setReviewedAt(r.getReviewedAt());
        dto.setCreatedAt(r.getCreatedAt());

        return dto;
    }
}
