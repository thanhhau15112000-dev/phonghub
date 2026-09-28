package com.phonghub.adapter.in.web;

import com.phonghub.adapter.in.security.DomainAuthenticationToken;
import com.phonghub.application.port.in.DemoActorPort;
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

    public GlobalUiAdvice(
        @Value("${phonghub.demo.enabled:true}") boolean demoEnabled,
        Optional<DemoActorPort> demoActorPort,
        Optional<NotificationUseCase> notificationUseCase
    ) {
        this.demoEnabled = demoEnabled;
        this.demoActorPort = demoActorPort;
        this.notificationUseCase = notificationUseCase;
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
}
