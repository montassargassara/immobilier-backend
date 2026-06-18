package com.immobilier.backend.dto;

import lombok.Data;

/**
 * Optional body of PUT /api/properties/{id}/validate.
 *
 * Lets the approving ADMIN set the commission % at validation time
 * (there is no automatic default anymore). Ignored for LOCATION
 * properties (rentals never carry a commission).
 */
@Data
public class ValidatePropertyRequest {
    private Double commissionPercentage;
}
