package com.phonghub.application.port.out;

import com.phonghub.domain.model.UserRole;
import java.io.Serializable;
import java.util.UUID;

// Serializable: nằm trong SecurityContext được lưu ở session store (Redis)
public record CurrentUser(
    UUID id,
    String email,
    String fullName,
    UserRole role,
    boolean mustChangePassword
) implements Serializable {
    public CurrentUser(UUID id, String email, String fullName, UserRole role) {
        this(id, email, fullName, role, false);
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isOwner() {
        return role == UserRole.OWNER;
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
