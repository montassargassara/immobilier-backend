package com.immobilier.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class SharePropertyRequest {
    /** IDs of ADMIN users to share this property with. Pass an empty list to remove all shares. */
    private List<Long> agencyAdminIds;
}
