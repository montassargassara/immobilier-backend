package com.immobilier.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PropertyStatusValidator implements ConstraintValidator<ValidPropertyStatus, PropertyStatusValidatable> {

    @Override
    public boolean isValid(PropertyStatusValidatable obj, ConstraintValidatorContext context) {
        if (obj == null) return true;

        String category = obj.getCategory();
        String statut = obj.getStatut();

        if (category == null || statut == null) return true;

        boolean valid = switch (category) {
            case "VENTE" -> !"LOUE".equals(statut);
            case "LOCATION" -> !"VENDU".equals(statut);
            default -> true;
        };

        if (!valid) {
            context.disableDefaultConstraintViolation();
            String message = "VENTE".equals(category)
                ? "Une propriété en vente ne peut pas avoir le statut 'LOUE'. Statuts autorisés: DISPONIBLE, EN_ATTENTE, VENDU"
                : "Une propriété en location ne peut pas avoir le statut 'VENDU'. Statuts autorisés: DISPONIBLE, EN_ATTENTE, LOUE";
            context.buildConstraintViolationWithTemplate(message)
                   .addPropertyNode("statut")
                   .addConstraintViolation();
        }

        return valid;
    }
}
