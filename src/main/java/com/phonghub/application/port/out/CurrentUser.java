package com.phonghub.application.port.out;

import com.phonghub.domain.model.UserRole;
import java.util.UUID;

public record CurrentUser(
    UUID id,
    String email,
    String fullName,
    UserRole role,
    boolean mustChangePassword
) {
    public CurrentUser(UUID id, String email, String fullName, UserRole role) {
        this(id, email, fullName, role, false);
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isStaff() {
        return role == UserRole.STAFF;
    }

    public boolean isTechnician() {
        return role == UserRole.TECHNICIAN;
    }

    public boolean isTenant() {
        return role == UserRole.TENANT;
    }
}
