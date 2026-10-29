package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.exception.DuplicateUsernameException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminUserUiController.class);
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
        model.addAttribute("tenantAccountsMissingProfiles", authUseCase.listTenantAccountsMissingProfiles());
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

    @PostMapping("/{userId}/tenant-profile")
    public String attachTenantProfile(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "") String identityCardNumber,
        @RequestParam(defaultValue = "") String phone,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(currentUserPort.getCurrentUser())) {
            redirectAttributes.addFlashAttribute("tableErrorMessage", "Chỉ quản trị viên mới có thể bổ sung hồ sơ người thuê.");
            return "redirect:/";
        }
        Map<String, String> fieldErrors = new java.util.LinkedHashMap<>();
        if (!phone.trim().matches("^0\\d{9}$")) {
            fieldErrors.put("phone", "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.");
        }
        if (!identityCardNumber.trim().matches("^(\\d{9}|\\d{12})$")) {
            fieldErrors.put("identityCardNumber", "CCCD gồm 9 hoặc 12 chữ số.");
        }
        String generalError = null;
        if (fieldErrors.isEmpty()) {
            try {
                authUseCase.adminAttachTenantProfile(userId, identityCardNumber, phone);
                redirectAttributes.addFlashAttribute("tableSuccessMessage", "Đã gắn hồ sơ CCCD với tài khoản người thuê.");
                return "redirect:/admin/users";
            } catch (DuplicateIdentityCardException ex) {
                fieldErrors.put("identityCardNumber", "CCCD này đã gắn với một tài khoản người thuê.");
            } catch (DomainException | IllegalArgumentException ex) {
                generalError = userFacingMessage(ex);
            }
        }
        // Giữ lại giá trị đã nhập và báo lỗi ngay dưới dòng tài khoản (#247)
        model.addAttribute("profileFormUserId", userId);
        model.addAttribute("profileForm", Map.of("phone", phone, "identityCardNumber", identityCardNumber));
        model.addAttribute("profileFieldErrors", fieldErrors);
        if (generalError != null) {
            model.addAttribute("tableErrorMessage", generalError);
        }
        return listUsers(null, 1, 5, null, model, redirectAttributes);
    }

    @PostMapping
    public String createUser(
        @RequestParam(defaultValue = "") String username,
        @RequestParam(defaultValue = "") String email,
        @RequestParam(defaultValue = "") String fullName,
        @RequestParam(required = false) String phone,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) String identityCardNumber,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(currentUserPort.getCurrentUser())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể tạo tài khoản.");
            return "redirect:/";
        }
        AuthUseCase.CreateUserCommand command =
            new AuthUseCase.CreateUserCommand(username, email, fullName, phone, role, identityCardNumber);

        // Kiểm tra hết các trường trong một lần để báo lỗi theo từng trường và giữ nguyên dữ liệu đã nhập (#247)
        Map<String, String> fieldErrors = new java.util.LinkedHashMap<>(authUseCase.validateCreateUser(command));
        if (role != null && !CREATABLE_ROLES.contains(role)) {
            fieldErrors.put("role", "Vai trò được chọn không được phép tạo từ giao diện này.");
        }
        String generalError = null;
        if (fieldErrors.isEmpty()) {
            try {
                AuthUseCase.AdminCreateUserResult created = authUseCase.adminCreateUser(command);
                redirectAttributes.addFlashAttribute("createdUser", created);
                return "redirect:/admin/users";
            } catch (DuplicateEmailException ex) {
                fieldErrors.put("email", "Email đã được dùng.");
            } catch (DuplicateUsernameException ex) {
                fieldErrors.put("username", "Tên đăng nhập đã được dùng.");
            } catch (DuplicateIdentityCardException ex) {
                fieldErrors.put("identityCardNumber", "CCCD này đã gắn với một tài khoản người thuê.");
            } catch (DomainException | IllegalArgumentException ex) {
                generalError = userFacingMessage(ex);
            } catch (Exception ex) {
                generalError = "Không thể tạo tài khoản: " + (ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống.");
            }
        }
        // Không redirect: trả thẳng trang với dữ liệu đã nhập để CCCD không phải đi qua flash/session hay URL
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("username", username);
        form.put("email", email);
        form.put("fullName", fullName);
        form.put("phone", phone == null ? "" : phone);
        form.put("role", role == null ? "" : role.name());
        form.put("identityCardNumber", identityCardNumber == null ? "" : identityCardNumber);
        model.addAttribute("createForm", form);
        model.addAttribute("createFieldErrors", fieldErrors);
        if (generalError != null) {
            model.addAttribute("createErrorMessage", generalError);
        } else if (!fieldErrors.isEmpty()) {
            model.addAttribute("createErrorMessage", "Vui lòng sửa các trường được đánh dấu rồi tạo lại.");
        }
        return listUsers(null, 1, 5, null, model, redirectAttributes);
    }

    @PostMapping("/{userId}/reset-password")
    public String resetTenantPassword(
        @PathVariable UUID userId,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser admin = currentUserPort.getCurrentUser();
        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể đặt lại mật khẩu.");
            redirectAttributes.addFlashAttribute("tableErrorMessage", "Chỉ quản trị viên mới có thể đặt lại mật khẩu.");
            return "redirect:/";
        }

        log.info("Admin [{}] (ID: {}) requested password reset for target user [{}]",
            admin != null ? admin.email() : "UNKNOWN",
            admin != null ? admin.id() : "UNKNOWN",
            userId);

        try {
            User target = userUseCase.getUser(userId);
            if (target.role() == UserRole.ADMIN) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể đặt lại mật khẩu cho tài khoản Quản trị viên.");
                redirectAttributes.addFlashAttribute("tableErrorMessage", "Không thể đặt lại mật khẩu cho tài khoản Quản trị viên.");
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

    /** Trang sửa thông tin, vai trò, trạng thái của một tài khoản (#89). */
    @GetMapping("/{userId}/edit")
    public String editUserForm(@PathVariable UUID userId, Model model, RedirectAttributes redirectAttributes) {
        CurrentUser admin = currentUserPort.getCurrentUser();
        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể quản lý tài khoản.");
            return "redirect:/";
        }
        try {
            User target = userUseCase.getUser(userId);
            if (target.role() == UserRole.ADMIN) {
                redirectAttributes.addFlashAttribute("tableErrorMessage", "Không sửa tài khoản quản trị viên tại đây.");
                return "redirect:/admin/users";
            }
            model.addAttribute("currentUser", admin);
            model.addAttribute("target", target);
            model.addAttribute("roles", java.util.Arrays.stream(UserRole.values())
                .filter(role -> role != UserRole.ADMIN).toList());
            model.addAttribute("statuses", User.UserStatus.values());
            return "admin/user-edit";
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("tableErrorMessage", userFacingMessage(ex));
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/{userId}/edit")
    public String editUser(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "") String fullName,
        @RequestParam(required = false) String phone,
        @RequestParam UserRole role,
        @RequestParam User.UserStatus status,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser admin = currentUserPort.getCurrentUser();
        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể quản lý tài khoản.");
            return "redirect:/";
        }
        Map<String, String> fieldErrors = new java.util.LinkedHashMap<>();
        if (fullName.isBlank()) {
            fieldErrors.put("fullName", "Vui lòng nhập họ và tên.");
        }
        if (phone != null && !phone.isBlank() && !phone.trim().matches("^0\\d{9}$")) {
            fieldErrors.put("phone", "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.");
        }
        String generalError = null;
        if (fieldErrors.isEmpty()) {
            try {
                User saved = userUseCase.adminUpdateUser(new UserUseCase.AdminUpdateUserCommand(
                    userId, fullName, phone, role, status));
                String msg = "Đã cập nhật tài khoản " + saved.fullName() + ".";
                redirectAttributes.addFlashAttribute("successMessage", msg);
                redirectAttributes.addFlashAttribute("tableSuccessMessage", msg);
                return "redirect:/admin/users";
            } catch (DomainException | IllegalArgumentException ex) {
                generalError = userFacingMessage(ex);
            }
        }
        // Trả lại form với giá trị vừa nhập thay vì quay về dữ liệu cũ trong DB (#247)
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("fullName", fullName);
        form.put("phone", phone == null ? "" : phone);
        form.put("role", role.name());
        form.put("status", status.name());
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errorMessage", generalError != null ? generalError
            : "Vui lòng sửa các trường được đánh dấu rồi lưu lại.");
        return editUserForm(userId, model, redirectAttributes);
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
