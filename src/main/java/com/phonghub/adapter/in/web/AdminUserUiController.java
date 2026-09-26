package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.DuplicateUsernameException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        UserRole.OWNER,
        UserRole.STAFF,
        UserRole.TECHNICIAN,
        UserRole.TENANT
    );
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

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
    public String listUsers(
        @RequestParam(name = "role", required = false) String role,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "5") int size,
        @RequestParam(name = "q", required = false) String q,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể quản lý tài khoản.");
            return "redirect:/";
        }

        List<User> allUsers = userUseCase.listUsers();

        String keyword = q != null ? q.trim() : "";
        List<User> matchedUsers = allUsers;
        if (!keyword.isEmpty()) {
            matchedUsers = allUsers.stream()
                .filter(u -> matchesUser(u, keyword))
                .toList();
        }

        Map<String, Long> roleCounts = matchedUsers.stream()
            .collect(Collectors.groupingBy(u -> u.role().name(), Collectors.counting()));

        UserRole filteredRole = null;
        if (role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")) {
            try {
                filteredRole = UserRole.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                filteredRole = null;
            }
        }

        final UserRole roleToFilter = filteredRole;
        List<User> filteredUsers = roleToFilter == null
            ? matchedUsers
            : matchedUsers.stream().filter(u -> u.role() == roleToFilter).toList();

        int pageSize = size > 0 ? size : 5;
        int totalItems = filteredUsers.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (totalPages < 1) {
            totalPages = 1;
        }

        // Nếu người dùng nhập số trang không tồn tại (< 1 hoặc > totalPages), tự động redirect về trang hợp lệ
        if (page < 1 || page > totalPages) {
            int validPage = Math.max(1, Math.min(page, totalPages));
            StringBuilder redirectUrl = new StringBuilder("redirect:/admin/users");
            boolean hasParam = false;
            if (role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")) {
                redirectUrl.append("?role=").append(role);
                hasParam = true;
            }
            if (!keyword.isEmpty()) {
                redirectUrl.append(hasParam ? "&" : "?").append("q=").append(keyword);
                hasParam = true;
            }
            if (validPage > 1) {
                redirectUrl.append(hasParam ? "&" : "?").append("page=").append(validPage);
                hasParam = true;
            }
            if (pageSize != 5) {
                redirectUrl.append(hasParam ? "&" : "?").append("size=").append(pageSize);
            }
            return redirectUrl.toString();
        }

        int currentPage = page;
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<User> pagedUsers = fromIndex < totalItems
            ? filteredUsers.subList(fromIndex, toIndex)
            : List.of();

        String selectedRole = roleToFilter != null ? roleToFilter.name() : "ALL";
        String selectedRoleTitle = switch (selectedRole) {
            case "ADMIN" -> "Quản trị viên";
            case "OWNER" -> "Chủ trọ";
            case "STAFF" -> "Nhân viên";
            case "TECHNICIAN" -> "Kỹ thuật viên";
            case "TENANT" -> "Người thuê";
            default -> "Chung";
        };

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("users", pagedUsers);
        model.addAttribute("totalCount", matchedUsers.size());
        model.addAttribute("filteredCount", totalItems);
        model.addAttribute("selectedRole", selectedRole);
        model.addAttribute("selectedRoleTitle", selectedRoleTitle);
        model.addAttribute("roleCounts", roleCounts);
        model.addAttribute("roles", CREATABLE_ROLES);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("q", keyword);
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
            String msg = userFacingMessage(ex);
            redirectAttributes.addFlashAttribute("errorMessage", msg);
            redirectAttributes.addFlashAttribute("createErrorMessage", msg);
            return "redirect:/admin/users";
        } catch (Exception ex) {
            String msg = "Không thể tạo tài khoản: " + (ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống.");
            redirectAttributes.addFlashAttribute("errorMessage", msg);
            redirectAttributes.addFlashAttribute("createErrorMessage", msg);
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
            redirectAttributes.addFlashAttribute("tableErrorMessage", "Chỉ quản trị viên mới có thể đặt lại mật khẩu.");
            return "redirect:/";
        }

        try {
            User target = userUseCase.getUser(userId);
            if (target.role() != UserRole.TENANT) {
                redirectAttributes.addFlashAttribute("errorMessage", "Chỉ có thể đặt lại mật khẩu cho tài khoản người thuê từ giao diện này.");
                redirectAttributes.addFlashAttribute("tableErrorMessage", "Chỉ có thể đặt lại mật khẩu cho tài khoản người thuê từ giao diện này.");
                return "redirect:/admin/users";
            }

            redirectAttributes.addFlashAttribute("resetResult", authUseCase.adminResetPassword(userId));
        } catch (DomainException | IllegalArgumentException ex) {
            String msg = userFacingMessage(ex);
            redirectAttributes.addFlashAttribute("errorMessage", msg);
            redirectAttributes.addFlashAttribute("tableErrorMessage", msg);
        } catch (Exception ex) {
            String msg = "Đặt lại mật khẩu thất bại: " + (ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống.");
            redirectAttributes.addFlashAttribute("errorMessage", msg);
            redirectAttributes.addFlashAttribute("tableErrorMessage", msg);
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/delete")
    public String deleteUser(@PathVariable("userId") UUID userId, RedirectAttributes redirectAttributes) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể quản lý tài khoản.");
            redirectAttributes.addFlashAttribute("tableErrorMessage", "Chỉ quản trị viên mới có thể quản lý tài khoản.");
            return "redirect:/";
        }

        try {
            authUseCase.adminDeleteUser(userId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xoá tài khoản người dùng thành công.");
            redirectAttributes.addFlashAttribute("tableSuccessMessage", "Đã xoá tài khoản người dùng thành công.");
        } catch (DomainException | IllegalArgumentException ex) {
            String msg = userFacingMessage(ex);
            redirectAttributes.addFlashAttribute("errorMessage", msg);
            redirectAttributes.addFlashAttribute("tableErrorMessage", msg);
        } catch (Exception ex) {
            String msg = "Xoá tài khoản thất bại: " + (ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống.");
            redirectAttributes.addFlashAttribute("errorMessage", msg);
            redirectAttributes.addFlashAttribute("tableErrorMessage", msg);
        }
        return "redirect:/admin/users";
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.role() == UserRole.ADMIN;
    }

    private String userFacingMessage(RuntimeException exception) {
        if (exception instanceof DuplicateEmailException || exception instanceof DuplicateUsernameException) {
            return exception.getMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        if (exception instanceof DomainException) {
            return "Không thể hoàn tất thao tác tài khoản. Vui lòng kiểm tra dữ liệu và thử lại.";
        }
        return "Dữ liệu tài khoản không hợp lệ.";
    }

    private boolean matchesUser(User u, String keyword) {
        if (keyword.isBlank()) {
            return true;
        }
        String normalizedKw = removeAccents(keyword);
        String roleVi = UiText.INSTANCE.role(u.role());

        String combined = String.join(" ",
            u.fullName() != null ? u.fullName() : "",
            u.email() != null ? u.email() : "",
            u.username() != null ? u.username() : "",
            u.phone() != null ? u.phone() : "",
            u.role().name(),
            roleVi
        );

        return combined.toLowerCase().contains(keyword.toLowerCase())
            || removeAccents(combined).contains(normalizedKw);
    }

    private static String removeAccents(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
            .replaceAll("")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase()
            .trim();
    }
}
