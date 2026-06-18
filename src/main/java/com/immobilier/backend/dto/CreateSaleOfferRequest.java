package com.immobilier.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateSaleOfferRequest {

    @NotNull(message = "L'identifiant du bien est requis")
    private Long propertyId;

    @NotBlank(message = "Le nom de l'acheteur est requis")
    private String buyerName;

    @NotBlank(message = "L'email de l'acheteur est requis")
    @Email(message = "Email de l'acheteur invalide")
    private String buyerEmail;

    private String buyerPhone;

    @Positive(message = "Le prix proposé doit être positif")
    private Double offeredPrice;

    private String message;
}
