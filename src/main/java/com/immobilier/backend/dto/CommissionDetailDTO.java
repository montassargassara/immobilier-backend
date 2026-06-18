package com.immobilier.backend.dto;

import com.immobilier.backend.entity.Commission;
import com.immobilier.backend.entity.Property;
import com.immobilier.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Detailed commission line for the dedicated Agency / Commercial commission pages.
 *
 * Built from the unified {@link Commission} entity — there is exactly ONE
 * commission domain. {@code source} ("AGENCY" | "STAFF") tells which page
 * it belongs to. No new table, no duplicated math.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionDetailDTO {

    private Long          id;
    private String        source;           // AGENCY | STAFF

    // Beneficiary (the agency ADMIN, or the COMMERCIAL / RESPONSABLE_COMMERCIAL)
    private Long          beneficiaryId;
    private String        beneficiaryName;
    private String        beneficiaryEmail;
    private String        beneficiaryRole;

    // Property
    private Long          propertyId;
    private String        propertyTitle;
    private String        propertyMainImageUrl;   // relative — frontend prepends apiBase
    private String        propertyOwnerType;

    // Buyer / tenant
    private String        buyerName;

    // Money
    private String        transactionType;        // SALE | RENT
    private double        propertyPrice;          // montant de la vente / location
    private String        commissionType;         // PERCENTAGE | FIXED
    private double        commissionRate;          // taux négocié
    private double        commissionAmount;

    // Payment state
    private boolean       paid;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static CommissionDetailDTO from(Commission c) {
        User b = c.getBeneficiary();
        Property p = c.getProperty();
        User buyer = p != null ? p.getBuyer() : null;

        return CommissionDetailDTO.builder()
                .id(c.getId())
                .source(c.getBeneficiaryType())
                .beneficiaryId(b != null ? b.getId() : null)
                .beneficiaryName(b != null ? b.getFullName() : "—")
                .beneficiaryEmail(b != null ? b.getEmail() : "")
                .beneficiaryRole(b != null && b.getRole() != null ? b.getRole().name() : null)
                .propertyId(p != null ? p.getId() : null)
                .propertyTitle(p != null ? p.getTitre() : "—")
                .propertyMainImageUrl(p != null && p.getMainImageId() != null
                        ? "/api/images/public/" + p.getMainImageId() : null)
                .propertyOwnerType(p != null ? p.getOwnerType() : null)
                .buyerName(buyer != null ? buyer.getFullName() : "—")
                .transactionType(c.getTransactionType())
                .propertyPrice(c.getPropertyPrice() != null ? c.getPropertyPrice() : 0.0)
                .commissionType(c.getCommissionType())
                .commissionRate(c.getCommissionRate() != null ? c.getCommissionRate() : 0.0)
                .commissionAmount(c.getCommissionAmount() != null ? c.getCommissionAmount() : 0.0)
                .paid("PAID".equals(c.getStatus()))
                .paidAt(c.getPaidAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
