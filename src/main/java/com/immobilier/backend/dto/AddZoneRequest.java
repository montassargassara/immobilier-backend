package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class AddZoneRequest {
    private String country;
    private String city;
    /** Client confirms simulated payment for paid zones. */
    private Boolean paymentConfirmed;
}
