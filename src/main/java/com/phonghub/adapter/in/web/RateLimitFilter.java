package com.phonghub.adapter.in.web;

import com.phonghub.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Giới hạn tần suất đăng nhập, quên mật khẩu và webhook SePay; vượt ngưỡng trả 429 kèm Retry-After. */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String WEBHOOK_PATH = "/api/v1/payments/sepay/webhook";
    /** Body đăng nhập chỉ có tên đăng nhập và mật khẩu; lớn hơn mức này không phải yêu cầu hợp lệ. */
    static final int MAX_LOGIN_BODY_BYTES = 16 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

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
        HttpServletRequest forwarded = request;
        switch (path) {
            case "/login", "/api/auth/login" -> {
                decision = check("login", ip, props.getLogin());
                if (decision.allowed()) {
                    String account;
                    if (path.equals("/login")) {
                        account = request.getParameter("username");
                    } else {
                        // API nhận JSON: đọc body một lần, giữ lại cho controller đọc tiếp
                        byte[] body = request.getInputStream().readNBytes(MAX_LOGIN_BODY_BYTES + 1);
                        if (body.length > MAX_LOGIN_BODY_BYTES) {
                            rejectTooLarge(response);
                            return;
                        }
                        forwarded = new CachedBodyRequest(request, body);
                        account = usernameFromJson(body);
                    }
                    // Web và API dùng chung bộ đếm theo tài khoản: đổi cổng đăng nhập không được thêm lượt thử
                    decision = check("login-account", normalized(account), props.getLoginAccount());
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
            chain.doFilter(forwarded, response);
            return;
        }
        reject(request, response, decision.retryAfterSeconds());
    }

    /** Tên đăng nhập trong body JSON; body sai định dạng hoặc thiếu trường thì dùng khóa rỗng (vẫn bị đếm). */
    private static String usernameFromJson(byte[] body) {
        if (body.length == 0) {
            return "";
        }
        try {
            JsonNode username = JSON.readTree(body).get("username");
            return username != null && username.isString() ? username.asString() : "";
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static void rejectTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"https://phonghub.local/errors/payload-too-large\","
            + "\"title\":\"Payload Too Large\",\"status\":413,\"detail\":\"Dữ liệu đăng nhập quá lớn.\"}");
    }

    /** Request với body đã đọc sẵn, cho phép controller đọc lại. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Đọc bất đồng bộ không được hỗ trợ");
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return in.read(buffer, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
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
            // Form đăng nhập/quên mật khẩu: trang HTML có nút quay lại thay vì một dòng chữ trơn (#164).
            String back = request.getContextPath()
                + (request.getRequestURI().endsWith("/forgot-password") ? "/forgot-password" : "/login");
            response.setContentType("text/html");
            response.getWriter().write("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Thao tác quá nhanh - PhongHub</title>"
                + "<link rel=\"stylesheet\" href=\"" + request.getContextPath() + "/css/style.css\"></head><body>"
                + "<main class=\"container\" style=\"max-width: 480px; margin-top: 48px;\"><div class=\"card\">"
                + "<h1>Thao tác quá nhanh</h1><p id=\"rateLimitMessage\">" + message + "</p>"
                + "<a class=\"btn btn-primary\" href=\"" + back + "\">Quay lại</a>"
                + "</div></main></body></html>");
        }
    }
}
