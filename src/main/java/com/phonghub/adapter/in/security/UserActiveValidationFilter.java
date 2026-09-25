package com.phonghub.adapter.in.security;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserActiveValidationFilter extends OncePerRequestFilter {

    private final UserRepositoryPort userRepository;

    public UserActiveValidationFilter(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            UUID userId = extractUserId(auth);
            if (userId != null) {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty() || userOpt.get().status() != User.UserStatus.ACTIVE) {
                    SecurityContextHolder.clearContext();
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    response.sendRedirect(request.getContextPath() + "/login");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private UUID extractUserId(Authentication auth) {
        if (auth instanceof DomainAuthenticationToken dat && dat.getCurrentUser() != null) {
            return dat.getCurrentUser().id();
        }
        if (auth.getPrincipal() instanceof CurrentUser cu) {
            return cu.id();
        }
        return null;
    }
}
