package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin sửa thông tin, vai trò, trạng thái tài khoản (#89). Tạo/xóa/đặt lại mật khẩu nằm ở AuthApiController. */
@RestController
public class AdminUserApiController {

    private final UserUseCase userUseCase;

    public AdminUserApiController(UserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @PatchMapping("/api/admin/users/{userId}")
    public ResponseEntity<AdminUserResponse> updateUser(
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        User user = userUseCase.adminUpdateUser(new UserUseCase.AdminUpdateUserCommand(
            userId, request.fullName(), request.phone(), request.role(), request.status()));
        return ResponseEntity.ok(AdminUserResponse.from(user));
    }

    public record UpdateUserRequest(
        @NotBlank(message = "Full name is required") String fullName,
        String phone,
        @NotNull(message = "Role is required") UserRole role,
        @NotNull(message = "Status is required") User.UserStatus status
    ) {}

    public record AdminUserResponse(UUID id, String username, String email, String fullName, String phone,
                                    UserRole role, User.UserStatus status) {
        static AdminUserResponse from(User user) {
            return new AdminUserResponse(user.id(), user.username(), user.email(), user.fullName(), user.phone(),
                user.role(), user.status());
        }
    }
}
