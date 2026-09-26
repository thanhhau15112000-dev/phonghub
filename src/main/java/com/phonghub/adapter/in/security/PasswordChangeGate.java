package com.phonghub.adapter.in.security;

import com.phonghub.application.port.out.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PasswordChangeGate extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean mustChangePassword = false;
        if (auth instanceof DomainAuthenticationToken domainAuth) {
            mustChangePassword = domainAuth.isMustChangePassword();
        } else if (auth != null && auth.getPrincipal() instanceof CurrentUser currentUser) {
            mustChangePassword = currentUser.mustChangePassword();
        }

        if (mustChangePassword) {
            String uri = request.getRequestURI();
            boolean isAllowed = uri.equals("/api/auth/change-password")
                || uri.equals("/api/auth/logout")
                || uri.equals("/account/password")
                || uri.equals("/session/logout")
                || uri.equals("/login")
                || uri.equals("/health")
                || uri.startsWith("/static/")
                || uri.startsWith("/css/")
                || uri.equals("/favicon.ico");

            if (!isAllowed) {
                if (!uri.startsWith("/api/")) {
                    response.sendRedirect("/account/password");
                    return;
                }

                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                String problemJson = """
                    {
                      "type": "https://phonghub.local/errors/password-change-required",
                      "title": "Password Change Required",
                      "status": 403,
                      "detail": "Password change is required before accessing business resources",
                      "timestamp": "%s"
                    }
                    """.formatted(Instant.now());
                response.getWriter().write(problemJson);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
