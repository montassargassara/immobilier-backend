// RoleCountDTO.java
package com.immobilier.backend.dto;

import lombok.Data;
import com.immobilier.backend.enums.RoleType;

@Data
public class RoleCountDTO {
    private RoleType role;
    private long count;
}