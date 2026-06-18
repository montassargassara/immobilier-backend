package com.immobilier.backend.dto;

import lombok.Data;

/**
 * Body of PUT /api/sale-validations/{id}/approve.
 *
 * The reviewing ADMIN / SUPER_ADMIN MUST enter both values manually —
 * there is no automatic default commission anymore. The service rejects
 * the approval if either is missing/invalid.
 */
@Data
public class ApproveValidationRequest {

    /** Final negotiated price applied to the property (sale or rental). Mandatory, > 0. */
    private Double finalPrice;

    /** Commission percentage for the commercial who brokered the deal. Mandatory, >= 0. */
    private Double commissionPercentage;
}
