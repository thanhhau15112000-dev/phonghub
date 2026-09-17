package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.application.port.in.AuthUseCase.AdminCreateUserResult;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;

public record AdminCreatedUserResponse(
    UUID id,
    String username,
    String email,
    String fullName,
    String phone,
    UserRole role,
    String status,
    boolean mustChangePassword,
    String temporaryPassword,
    Instant createdAt
) {
    public static AdminCreatedUserResponse from(AdminCreateUserResult result) {
        return new AdminCreatedUserResponse(
            result.id(),
            result.username(),
            result.email(),
            result.fullName(),
            result.phone(),
            result.role(),
            result.status().name(),
            result.mustChangePassword(),
            result.temporaryPassword(),
            result.createdAt()
        );
    }
}
