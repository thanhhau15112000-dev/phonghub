package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

public class CurrentUserInterceptor implements HandlerInterceptor {

    private final CurrentUserPort currentUserPort;
    private final DemoActorPort demoActorPort;
    private final boolean demoEnabled;

    public CurrentUserInterceptor(CurrentUserPort currentUserPort, DemoActorPort demoActorPort, boolean demoEnabled) {
        this.currentUserPort = currentUserPort;
        this.demoActorPort = demoActorPort;
        this.demoEnabled = demoEnabled;
    }

    public CurrentUserInterceptor(CurrentUserPort currentUserPort, DemoActorPort demoActorPort) {
        this(currentUserPort, demoActorPort, true);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID requestedUserId = null;

        if (demoEnabled && demoActorPort != null) {
            // 1. Check HTTP header X-User-Id (highest priority, used by API & tests)
            String headerVal = request.getHeader("X-User-Id");
            if (headerVal != null && !headerVal.isBlank()) {
                try {
                    requestedUserId = UUID.fromString(headerVal.trim());
                } catch (IllegalArgumentException ignored) {}
            }

            // 2. Check query parameter asUser
            if (requestedUserId == null) {
                String paramVal = request.getParameter("asUser");
                if (paramVal != null && !paramVal.isBlank()) {
                    try {
                        requestedUserId = UUID.fromString(paramVal.trim());
                        HttpSession session = request.getSession(true);
                        session.setAttribute("currentUserId", requestedUserId);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // 3. Check session attribute
            if (requestedUserId == null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    Object sessionVal = session.getAttribute("currentUserId");
                    if (sessionVal instanceof UUID uid) {
                        requestedUserId = uid;
                    }
                }
            }

            // Resolve user
            if (requestedUserId != null) {
                Optional<CurrentUser> userOpt = demoActorPort.findDemoUser(requestedUserId);
                if (userOpt.isPresent()) {
                    demoActorPort.setCurrentUser(userOpt.get());
                    request.setAttribute("currentUser", userOpt.get());
                    return true;
                }
            }
        }

        // Default to current fallback from read-only port
        if (currentUserPort != null) {
            try {
                CurrentUser current = currentUserPort.getCurrentUser();
                if (demoActorPort != null) {
                    demoActorPort.setCurrentUser(current);
                }
                request.setAttribute("currentUser", current);
            } catch (Exception ignored) {
                // In production without authentication, interceptor does not enforce fallback
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (demoActorPort != null) {
            demoActorPort.clear();
        }
    }
}
