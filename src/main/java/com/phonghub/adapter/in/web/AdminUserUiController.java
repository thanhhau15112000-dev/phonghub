package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserUiController {

    private static final List<UserRole> CREATABLE_ROLES = List.of(
        UserRole.STAFF,
        UserRole.TECHNICIAN,
        UserRole.TENANT
    );

    private final AuthUseCase authUseCase;
    private final UserUseCase userUseCase;
    private final CurrentUserPort currentUserPort;

    public AdminUserUiController(
        AuthUseCase authUseCase,
        UserUseCase userUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.authUseCase = authUseCase;
        this.userUseCase = userUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String listUsers(Model model, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể quản lý tài khoản.");
            return "redirect:/";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("users", userUseCase.listUsers());
        model.addAttribute("roles", CREATABLE_ROLES);
        return "admin/users";
    }

    @PostMapping
    public String createUser(
        @RequestParam String username,
        @RequestParam String email,
        @RequestParam String fullName,
        @RequestParam(required = false) String phone,
        @RequestParam UserRole role,
        RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(currentUserPort.getCurrentUser())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể tạo tài khoản.");
            return "redirect:/";
        }
        if (!CREATABLE_ROLES.contains(role)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vai trò được chọn không được phép tạo từ giao diện này.");
            return "redirect:/admin/users";
        }

        try {
            AuthUseCase.AdminCreateUserResult created = authUseCase.adminCreateUser(
                new AuthUseCase.CreateUserCommand(username, email, fullName, phone, role)
            );
            redirectAttributes.addFlashAttribute("createdUser", created);
            return "redirect:/admin/users";
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userFacingMessage(ex));
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/{userId}/reset-password")
    public String resetTenantPassword(
        @PathVariable UUID userId,
        RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(currentUserPort.getCurrentUser())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể đặt lại mật khẩu.");
            return "redirect:/";
        }

        try {
            User target = userUseCase.getUser(userId);
            if (target.role() != UserRole.TENANT) {
                redirectAttributes.addFlashAttribute("errorMessage", "Chỉ có thể đặt lại mật khẩu cho tài khoản người thuê từ giao diện này.");
                return "redirect:/admin/users";
            }

            redirectAttributes.addFlashAttribute("resetResult", authUseCase.adminResetPassword(userId));
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", userFacingMessage(ex));
        }
        return "redirect:/admin/users";
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.role() == UserRole.ADMIN;
    }

    private String userFacingMessage(RuntimeException exception) {
        if (exception instanceof DomainException) {
            return "Không thể hoàn tất thao tác tài khoản. Vui lòng kiểm tra dữ liệu và thử lại.";
        }
        return exception.getMessage() != null ? exception.getMessage() : "Dữ liệu tài khoản không hợp lệ.";
    }
}
