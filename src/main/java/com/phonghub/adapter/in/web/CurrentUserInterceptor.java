package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.application.port.out.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

public class CurrentUserInterceptor implements HandlerInterceptor {

    private final LocalDemoAuthenticationAdapter authAdapter;
    private final boolean demoEnabled;

    public CurrentUserInterceptor(LocalDemoAuthenticationAdapter authAdapter, boolean demoEnabled) {
        this.authAdapter = authAdapter;
        this.demoEnabled = demoEnabled;
    }

    public CurrentUserInterceptor(LocalDemoAuthenticationAdapter authAdapter) {
        this(authAdapter, true);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID requestedUserId = null;

        if (demoEnabled) {
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
                        // Persist to session for UI browsing
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
                Optional<CurrentUser> userOpt = authAdapter.findDemoUser(requestedUserId);
                if (userOpt.isPresent()) {
                    authAdapter.setCurrentUser(userOpt.get());
                    request.setAttribute("currentUser", userOpt.get());
                    return true;
                }
            }
        }

        // Default to current fallback
        CurrentUser current = authAdapter.getCurrentUser();
        authAdapter.setCurrentUser(current);
        request.setAttribute("currentUser", current);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        authAdapter.clear();
    }
}
