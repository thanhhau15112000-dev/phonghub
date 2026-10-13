package com.phonghub.adapter.in.web;

import com.phonghub.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;

/** Giới hạn tần suất đăng nhập, quên mật khẩu và webhook SePay; vượt ngưỡng trả 429 kèm Retry-After. */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String WEBHOOK_PATH = "/api/v1/payments/sepay/webhook";

    private final RateLimitProperties props;
    private final FixedWindowRateLimiter limiter;

    public RateLimitFilter(RateLimitProperties props, FixedWindowRateLimiter limiter) {
        this.props = props;
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled() || !"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.equals("/login") || path.equals("/api/auth/login")
            || path.equals("/forgot-password") || path.equals(WEBHOOK_PATH));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = clientIp(request);
        FixedWindowRateLimiter.Decision decision;
        switch (path) {
            case "/login", "/api/auth/login" -> {
                decision = check("login", ip, props.getLogin());
                if (decision.allowed() && path.equals("/login")) {
                    decision = check("login-account", normalized(request.getParameter("username")), props.getLoginAccount());
                }
            }
            case "/forgot-password" -> {
                decision = check("forgot-password", ip, props.getForgotPassword());
                if (decision.allowed()) {
                    decision = check("forgot-password-target", normalized(request.getParameter("identifier")),
                        props.getForgotPasswordTarget());
                }
            }
            default -> decision = check("webhook", ip, props.getWebhook());
        }
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        reject(request, response, decision.retryAfterSeconds());
    }

    private FixedWindowRateLimiter.Decision check(String rule, String key, RateLimitProperties.Rule limit) {
        return limiter.tryAcquire(rule, key, limit.getMaxRequests(), limit.getWindowSeconds());
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** IP khách; chỉ đọc X-Forwarded-For khi cấu hình tin cậy proxy, và lấy phần tử do proxy tin cậy thêm vào. */
    String clientIp(HttpServletRequest request) {
        if (props.isTrustForwardedFor()) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                String[] parts = header.split(",");
                int index = parts.length - Math.max(1, props.getTrustedProxyHops());
                if (index >= 0) {
                    String candidate = parts[index].trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
        throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = "Bạn thao tác quá nhanh. Vui lòng thử lại sau " + retryAfterSeconds + " giây.";
        if (request.getRequestURI().startsWith("/api/")) {
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"https://phonghub.local/errors/too-many-requests\","
                + "\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"" + message + "\"}");
        } else {
            response.setContentType("text/plain");
            response.getWriter().write(message);
        }
    }
}
