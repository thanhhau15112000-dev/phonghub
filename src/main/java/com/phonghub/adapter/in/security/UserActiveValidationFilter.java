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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đăng nhập lưu trong session chỉ còn hiệu lực khi tài khoản vẫn ACTIVE và vai trò không đổi so với lúc đăng nhập.
 * Ngược lại phiên bị hủy: giao diện chuyển về trang đăng nhập, API trả 401 (#305).
 *
 * <p>Ở chuỗi API, request mang Bearer token được bỏ qua vì {@link SupabaseJwtAuthenticationConverter} đã đọc lại tài
 * khoản và kiểm trạng thái ở mỗi request.
 */
public class UserActiveValidationFilter extends OncePerRequestFilter {

    static final String REVOKED_DETAIL = "Phiên đăng nhập không còn hiệu lực: tài khoản đã bị khóa hoặc thay đổi vai trò.";

    private final UserRepositoryPort userRepository;
    private final boolean api;

    public UserActiveValidationFilter(UserRepositoryPort userRepository) {
        this(userRepository, false);
    }

    /** @param api {@code true} cho chuỗi {@code /api/**}: trả 401 JSON thay vì chuyển hướng tới trang đăng nhập. */
    public UserActiveValidationFilter(UserRepositoryPort userRepository, boolean api) {
        this.userRepository = userRepository;
        this.api = api;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
            && !(api && hasBearerToken(request))) {
            CurrentUser current = extractCurrentUser(auth);
            if (current != null && current.id() != null) {
                Optional<User> userOpt = userRepository.findById(current.id());
                if (userOpt.isEmpty() || userOpt.get().status() != User.UserStatus.ACTIVE
                    || (current.role() != null && userOpt.get().role() != current.role())) {
                    revoke(request, response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void revoke(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        if (api) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                + REVOKED_DETAIL + "\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private static CurrentUser extractCurrentUser(Authentication auth) {
        if (auth instanceof DomainAuthenticationToken dat && dat.getCurrentUser() != null) {
            return dat.getCurrentUser();
        }
        if (auth.getPrincipal() instanceof CurrentUser cu) {
            return cu;
        }
        return null;
    }
}
