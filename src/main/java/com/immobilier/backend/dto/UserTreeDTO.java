// UserTreeDTO.java
package com.immobilier.backend.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserTreeDTO {
    private UserDTO user;
    private List<UserTreeDTO> children = new ArrayList<>();
    private int descendantCount;
    private List<RoleCountDTO> roleCounts;
}