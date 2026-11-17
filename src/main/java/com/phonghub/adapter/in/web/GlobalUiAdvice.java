package com.phonghub.adapter.in.web;

import com.phonghub.adapter.in.security.DomainAuthenticationToken;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.in.NotificationInboxUseCase;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUiAdvice {

    private final boolean demoEnabled;
    private final Optional<DemoActorPort> demoActorPort;
    private final Optional<NotificationUseCase> notificationUseCase;
    private final Optional<NotificationInboxUseCase> notificationInbox;
    private final Optional<com.phonghub.application.port.in.ChatMessageUseCase> chatMessageUseCase;
    private final long chatMaxImageBytes;
    private final long chatMaxVideoBytes;

    public GlobalUiAdvice(
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled,
        @Value("${phonghub.storage.max-file-size-bytes:10485760}") long chatMaxImageBytes,
        @Value("${phonghub.chat.max-video-bytes:10485760}") long chatMaxVideoBytes,
        Optional<DemoActorPort> demoActorPort,
        Optional<NotificationUseCase> notificationUseCase,
        Optional<NotificationInboxUseCase> notificationInbox,
        Optional<com.phonghub.application.port.in.ChatMessageUseCase> chatMessageUseCase
    ) {
        this.demoEnabled = demoEnabled;
        this.demoActorPort = demoActorPort;
        this.notificationUseCase = notificationUseCase;
        this.notificationInbox = notificationInbox;
        this.chatMessageUseCase = chatMessageUseCase;
        this.chatMaxImageBytes = chatMaxImageBytes;
        this.chatMaxVideoBytes = Math.min(chatMaxVideoBytes, chatMaxImageBytes);
    }

    /** Giới hạn dung lượng ảnh/video trong chat để widget kiểm tra trước khi gửi (#251, như #207). */
    @ModelAttribute("chatMediaLimits")
    public Map<String, Long> chatMediaLimits() {
        return Map.of("imageBytes", chatMaxImageBytes, "videoBytes", chatMaxVideoBytes);
    }

    /**
     * Chuông thông báo cá nhân trên header cho mọi vai trò trừ quản trị viên (quản trị viên giữ chuông việc hệ thống):
     * 5 thông báo mới nhất và số chưa đọc (#86). Bỏ qua request API vì không render header.
     */
    @ModelAttribute("myNotifications")
    public NotificationInboxUseCase.InboxPage myNotifications(HttpServletRequest request) {
        CurrentUser user = currentUser(request);
        if (user == null || user.role() == UserRole.ADMIN || notificationInbox.isEmpty()
            || request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            return null;
        }
        return notificationInbox.get().inbox(false, 1, 5);
    }

    @ModelAttribute("demoEnabled")
    public boolean isDemoEnabled() {
        return demoEnabled && demoActorPort.isPresent();
    }

    @ModelAttribute("demoUsers")
    public Map<UUID, CurrentUser> demoUsers() {
        return (demoEnabled && demoActorPort.isPresent()) ? demoActorPort.get().getAllDemoUsers() : Collections.emptyMap();
    }

    @ModelAttribute("uiText")
    public UiText uiText() {
        return UiText.INSTANCE;
    }

    @ModelAttribute("currentUser")
    public CurrentUser currentUser(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUser");
        if (attr instanceof CurrentUser user) {
            return user;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof DomainAuthenticationToken domainAuth) {
            return domainAuth.getCurrentUser();
        }
        return null;
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(HttpServletRequest request) {
        CurrentUser user = currentUser(request);
        if (user != null && user.role() == UserRole.ADMIN && notificationUseCase.isPresent()) {
            return notificationUseCase.get().countUnreadAdminNotifications();
        }
        return 0L;
    }

    @ModelAttribute("adminNotifications")
    public List<Notification> adminNotifications(HttpServletRequest request) {
        CurrentUser user = currentUser(request);
        if (user != null && user.role() == UserRole.ADMIN && notificationUseCase.isPresent()) {
            return notificationUseCase.get().listAdminNotifications();
        }
        return Collections.emptyList();
    }

    @ModelAttribute("unreadChatCount")
    public int unreadChatCount(HttpServletRequest request) {
        CurrentUser user = currentUser(request);
        if (user != null && chatMessageUseCase.isPresent()) {
            try {
                return chatMessageUseCase.get().getTotalUnreadCount(user);
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
