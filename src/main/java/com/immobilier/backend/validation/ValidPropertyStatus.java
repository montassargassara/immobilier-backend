package com.immobilier.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PropertyStatusValidator.class)
@Documented
public @interface ValidPropertyStatus {
    String message() default "Le statut n'est pas compatible avec la catégorie de la propriété";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}