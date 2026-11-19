package com.phonghub.adapter.in.web;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.config.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Giới hạn số lần gọi trợ lý AI theo tài khoản đã đăng nhập (#311). Mỗi lần gọi đều tốn chi phí mô hình nên đếm
 * theo người dùng, không theo IP: nhiều người dùng chung mạng không chặn nhau, một người đổi IP cũng không được thêm
 * lượt. Vượt ngưỡng trả 429 {@code application/problem+json} kèm {@code Retry-After}.
 *
 * <p>Chạy sau xác thực nên đã biết người gọi; request chưa xác định được người dùng để controller và lớp bảo mật xử lý.
 */
public class AiRateLimitInterceptor implements HandlerInterceptor {

    static final String RULE = "ai";

    private final RateLimitProperties props;
    private final FixedWindowRateLimiter limiter;
    private final CurrentUserPort currentUserPort;
    private final MeterRegistry meterRegistry;

    public AiRateLimitInterceptor(RateLimitProperties props, FixedWindowRateLimiter limiter,
                                  CurrentUserPort currentUserPort, MeterRegistry meterRegistry) {
        this.props = props;
        this.limiter = limiter;
        this.currentUserPort = currentUserPort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {
        if (!props.isEnabled() || !"POST".equals(request.getMethod())) {
            return true;
        }
        CurrentUser user = currentUser();
        if (user == null || user.id() == null) {
            return true;
        }
        RateLimitProperties.Rule rule = props.getAi();
        FixedWindowRateLimiter.Decision decision =
            limiter.tryAcquire(RULE, user.id().toString(), rule.getMaxRequests(), rule.getWindowSeconds());
        if (decision.allowed()) {
            return true;
        }
        if (meterRegistry != null) {
            meterRegistry.counter("phonghub.ratelimit.rejected", "path", "/api/v1/ai").increment();
        }
        reject(response, decision.retryAfterSeconds());
        return false;
    }

    private CurrentUser currentUser() {
        try {
            return currentUserPort.getCurrentUser();
        } catch (RuntimeException notAuthenticated) {
            return null;
        }
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        String message = "Bạn đã hỏi trợ lý quá nhiều lần trong thời gian ngắn. Vui lòng thử lại sau "
            + retryAfterSeconds + " giây.";
        response.getWriter().write("{\"type\":\"https://phonghub.local/errors/too-many-requests\","
            + "\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"" + message + "\"}");
    }
}
