package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Notification;
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
@RequestMapping("/admin/notifications")
public class AdminNotificationUiController {

    private final NotificationUseCase notificationUseCase;
    private final CurrentUserPort currentUserPort;

    public AdminNotificationUiController(
        NotificationUseCase notificationUseCase,
        CurrentUserPort currentUserPort
    ) {
        this.notificationUseCase = notificationUseCase;
        this.currentUserPort = currentUserPort;
    }

    @GetMapping
    public String listNotifications(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "10") int size,
        @RequestParam(name = "q", required = false) String q,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể xem thông báo hệ thống.");
            return "redirect:/";
        }

        List<Notification> allNotifications = notificationUseCase.listAdminNotifications();

        long totalCount = allNotifications.size();
        long unresolvedCount = allNotifications.stream().filter(n -> !n.isResolved()).count();
        long resolvedCount = allNotifications.stream().filter(Notification::isResolved).count();

        String keyword = q != null ? q.trim().toLowerCase() : "";
        List<Notification> matched = allNotifications;
        if (!keyword.isEmpty()) {
            matched = matched.stream()
                .filter(n -> matchesKeyword(n, keyword))
                .toList();
        }

        String filter = status != null ? status.trim().toUpperCase() : "ALL";
        List<Notification> filtered = switch (filter) {
            case "UNRESOLVED" -> matched.stream().filter(n -> !n.isResolved()).toList();
            case "RESOLVED" -> matched.stream().filter(Notification::isResolved).toList();
            default -> {
                filter = "ALL";
                yield matched;
            }
        };

        int pageSize = size > 0 ? size : 10;
        int totalItems = filtered.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (totalPages < 1) {
            totalPages = 1;
        }

        if (page < 1 || page > totalPages) {
            int validPage = Math.max(1, Math.min(page, totalPages));
            StringBuilder redirectUrl = new StringBuilder("redirect:/admin/notifications");
            boolean hasParam = false;
            if (!filter.equals("ALL")) {
                redirectUrl.append("?status=").append(filter);
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
            if (pageSize != 10) {
                redirectUrl.append(hasParam ? "&" : "?").append("size=").append(pageSize);
            }
            return redirectUrl.toString();
        }

        int currentPage = page;
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<Notification> pagedList = fromIndex < totalItems ? filtered.subList(fromIndex, toIndex) : List.of();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("notifications", pagedList);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("unresolvedCount", unresolvedCount);
        model.addAttribute("resolvedCount", resolvedCount);
        model.addAttribute("selectedStatus", filter);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("q", keyword);
        return "admin/notifications";
    }

    @PostMapping("/{id}/reset-password")
    public String processPasswordReset(
        @PathVariable UUID id,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể đặt lại mật khẩu.");
            return "redirect:/";
        }

        try {
            AuthUseCase.PasswordResetResult result = notificationUseCase.processPasswordReset(id);
            redirectAttributes.addFlashAttribute("resetResult", result);
            redirectAttributes.addFlashAttribute("successMessage",
                "Đã cấp lại mật khẩu tạm thời thành công cho tài khoản " + result.username() + "!");
        } catch (DomainException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                ex.getMessage() != null ? ex.getMessage() : "Không thể đặt lại mật khẩu.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi khi đặt lại mật khẩu.");
        }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/{id}/resolve")
    public String markResolved(
        @PathVariable UUID id,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể xử lý thông báo.");
            return "redirect:/";
        }

        try {
            notificationUseCase.markAsResolved(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đánh dấu thông báo là đã xử lý.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật trạng thái thông báo.");
        }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/{id}/delete")
    public String deleteNotification(
        @PathVariable UUID id,
        RedirectAttributes redirectAttributes
    ) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        if (!isAdmin(currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chỉ quản trị viên mới có thể xóa thông báo.");
            return "redirect:/";
        }

        try {
            notificationUseCase.deleteNotification(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa thông báo thành công.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa thông báo.");
        }
        return "redirect:/admin/notifications";
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.role() == UserRole.ADMIN;
    }

    private boolean matchesKeyword(Notification n, String keyword) {
        if (n.targetUsername() != null && n.targetUsername().toLowerCase().contains(keyword)) {
            return true;
        }
        if (n.targetFullName() != null && n.targetFullName().toLowerCase().contains(keyword)) {
            return true;
        }
        if (n.title() != null && n.title().toLowerCase().contains(keyword)) {
            return true;
        }
        if (n.message() != null && n.message().toLowerCase().contains(keyword)) {
            return true;
        }
        return false;
    }
}
