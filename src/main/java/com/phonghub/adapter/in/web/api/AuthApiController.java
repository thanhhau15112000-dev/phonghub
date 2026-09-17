package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.AdminCreatedUserResponse;
import com.phonghub.adapter.in.web.api.dto.AdminPasswordResetResponse;
import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.domain.model.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AuthApiController {

    private final AuthUseCase authUseCase;

    public AuthApiController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    public record LoginRequest(
        @NotBlank(message = "Username cannot be blank") String username,
        @NotBlank(message = "Password cannot be blank") String password
    ) {}

    public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token cannot be blank") String refreshToken
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "New password cannot be blank")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword
    ) {}

    public record CreateUserRequest(
        @NotBlank(message = "Username cannot be blank") String username,
        @NotBlank(message = "Email cannot be blank") String email,
        @NotBlank(message = "Full name cannot be blank") String fullName,
        String phone,
        @NotNull(message = "Role is required") UserRole role
    ) {}

    @PostMapping("/api/auth/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResponse response = authUseCase.login(request.username(), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthTokenResponse response = authUseCase.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, String>> logout(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authUseCase.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authUseCase.changePassword(request.newPassword());
        return ResponseEntity.ok(Map.of(
            "message", "Password changed successfully",
            "mustChangePassword", false
        ));
    }

    @PostMapping("/api/admin/users/{userId}/password-reset")
    public ResponseEntity<AdminPasswordResetResponse> adminResetPassword(@PathVariable UUID userId) {
        AuthUseCase.PasswordResetResult result = authUseCase.adminResetPassword(userId);
        return ResponseEntity.ok(AdminPasswordResetResponse.from(result));
    }

    @PostMapping("/api/admin/users")
    public ResponseEntity<AdminCreatedUserResponse> adminCreateUser(@Valid @RequestBody CreateUserRequest request) {
        AuthUseCase.AdminCreateUserResult created = authUseCase.adminCreateUser(new AuthUseCase.CreateUserCommand(
            request.username(),
            request.email(),
            request.fullName(),
            request.phone(),
            request.role()
        ));
        AdminCreatedUserResponse response = AdminCreatedUserResponse.from(created);
        return ResponseEntity.created(URI.create("/api/admin/users/" + response.id())).body(response);
    }
}
