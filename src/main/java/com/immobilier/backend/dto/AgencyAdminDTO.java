package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgencyAdminDTO {
    private Long id;
    private String fullName;
    private String email;
    private boolean alreadyShared;
    // Latest share request status: PENDING, ACCEPTED, REJECTED, CANCELLED, or null (never requested)
    private String shareRequestStatus;

    // Backward-compat constructor used by PropertyService (no shareRequestStatus)
    public AgencyAdminDTO(Long id, String fullName, String email, boolean alreadyShared) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.alreadyShared = alreadyShared;
        this.shareRequestStatus = null;
    }
}
