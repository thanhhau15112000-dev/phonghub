package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.NotificationInboxUseCase;
import com.phonghub.application.port.in.NotificationInboxUseCase.InboxPage;
import com.phonghub.domain.exception.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang trung tâm thông báo cho mọi vai trò (#86). */
@Controller
@RequestMapping("/notifications")
public class NotificationUiController {

    private static final int PAGE_SIZE = 20;

    private final NotificationInboxUseCase inbox;

    public NotificationUiController(NotificationInboxUseCase inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public String list(
        @RequestParam(name = "status", defaultValue = "ALL") String status,
        @RequestParam(name = "page", defaultValue = "1") int page,
        Model model
    ) {
        boolean unreadOnly = "UNREAD".equalsIgnoreCase(status);
        InboxPage result = inbox.inbox(unreadOnly, page, PAGE_SIZE);
        model.addAttribute("inbox", result);
        model.addAttribute("selectedStatus", unreadOnly ? "UNREAD" : "ALL");
        return "notifications/list";
    }

    /** Đánh dấu đã đọc rồi chuyển tới trang liên quan (nếu có), không thì quay lại danh sách. */
    @PostMapping("/{id}/open")
    public String open(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            // Liên kết chỉ có thể là đường dẫn nội bộ (Notification kiểm tra khi tạo) nên không bị chuyển ra ngoài.
            return inbox.open(id).map(link -> "redirect:" + link).orElse("redirect:/notifications");
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/notifications";
        }
    }

    @PostMapping("/{id}/read")
    public String markRead(
        @PathVariable UUID id,
        @RequestParam(name = "status", defaultValue = "ALL") String status,
        @RequestParam(name = "page", defaultValue = "1") int page,
        RedirectAttributes redirectAttributes
    ) {
        try {
            inbox.markRead(id);
        } catch (DomainException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        redirectAttributes.addAttribute("status", "UNREAD".equalsIgnoreCase(status) ? "UNREAD" : "ALL");
        redirectAttributes.addAttribute("page", Math.max(1, page));
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(RedirectAttributes redirectAttributes) {
        int updated = inbox.markAllRead();
        redirectAttributes.addFlashAttribute("successMessage",
            updated == 0 ? "Không có thông báo chưa đọc." : "Đã đánh dấu " + updated + " thông báo là đã đọc.");
        return "redirect:/notifications";
    }
}
