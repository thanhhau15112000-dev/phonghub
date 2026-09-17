package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.application.port.in.AuthUseCase.PasswordResetResult;
import java.util.UUID;

public record AdminPasswordResetResponse(
    UUID userId,
    String username,
    String temporaryPassword,
    boolean mustChangePassword,
    String message
) {
    public static AdminPasswordResetResponse from(PasswordResetResult result) {
        return new AdminPasswordResetResponse(
            result.userId(),
            result.username(),
            result.temporaryPassword(),
            result.mustChangePassword(),
            result.message()
        );
    }
}
