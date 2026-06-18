package com.immobilier.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

public class CustomUserDetails extends User {
    
    private final Long userId;
    private final String email;
    private final String fullName;
    
    public CustomUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities,
                             Long userId, String email, String fullName) {
        super(username, password, authorities);
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getFullName() {
        return fullName;
    }
}
