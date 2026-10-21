package com.phonghub.adapter.in.web;

import com.phonghub.config.RateLimitProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #77: giới hạn tần suất đăng nhập / quên mật khẩu / webhook. */
class RateLimitFilterTest {

    /** Đồng hồ chỉnh tay để test cửa sổ thời gian mà không phải chờ. */
    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(1_000_000L);

        void advanceSeconds(long seconds) {
            millis.addAndGet(seconds * 1000);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    private final MutableClock clock = new MutableClock();
    private final RateLimitProperties props = new RateLimitProperties();
    private final RateLimitFilter filter = new RateLimitFilter(props, new FixedWindowRateLimiter(clock));

    private MockHttpServletResponse post(String uri, String ip, String... params) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.setParameter(params[i], params[i + 1]);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("đăng nhập: vượt ngưỡng theo IP thì 429 kèm Retry-After, hết cửa sổ thì cho lại")
    void loginByIp() throws Exception {
        props.setLogin(new RateLimitProperties.Rule(3, 60));
        for (int i = 0; i < 3; i++) {
            assertEquals(200, post("/api/auth/login", "1.1.1.1").getStatus());
        }
        MockHttpServletResponse blocked = post("/api/auth/login", "1.1.1.1");
        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentType().contains("problem+json"));

        assertEquals(200, post("/api/auth/login", "2.2.2.2").getStatus(), "IP khác không bị ảnh hưởng");

        clock.advanceSeconds(30);
        assertEquals("30", post("/api/auth/login", "1.1.1.1").getHeader("Retry-After"));
        clock.advanceSeconds(31);
        assertEquals(200, post("/api/auth/login", "1.1.1.1").getStatus());
    }

    @Test
    @DisplayName("đăng nhập UI: giới hạn theo tài khoản dù đổi IP")
    void loginByAccount() throws Exception {
        props.setLoginAccount(new RateLimitProperties.Rule(2, 300));
        assertEquals(200, post("/login", "1.1.1.1", "username", "Owner1").getStatus());
        assertEquals(200, post("/login", "1.1.1.2", "username", "owner1 ").getStatus());
        assertEquals(429, post("/login", "1.1.1.3", "username", "OWNER1").getStatus());
        assertEquals(200, post("/login", "1.1.1.3", "username", "other").getStatus());
    }

    @Test
    @DisplayName("#164: form đăng nhập/quên mật khẩu bị chặn thì trả trang HTML có nút quay lại, vẫn 429 + Retry-After")
    void uiBlockedPageIsHtml() throws Exception {
        props.setLoginAccount(new RateLimitProperties.Rule(1, 300));
        props.setForgotPasswordTarget(new RateLimitProperties.Rule(1, 900));
        post("/login", "1.1.1.1", "username", "owner1");
        MockHttpServletResponse login = post("/login", "1.1.1.1", "username", "owner1");
        assertEquals(429, login.getStatus());
        assertEquals("300", login.getHeader("Retry-After"));
        assertTrue(login.getContentType().startsWith("text/html"), login.getContentType());
        String html = login.getContentAsString();
        assertTrue(html.contains("Vui lòng thử lại sau 300 giây"), html);
        assertTrue(html.contains("href=\"/login\""), html);

        post("/forgot-password", "1.1.1.1", "identifier", "admin");
        String forgot = post("/forgot-password", "1.1.1.1", "identifier", "admin").getContentAsString();
        assertTrue(forgot.contains("href=\"/forgot-password\""), forgot);
    }

    @Test
    @DisplayName("các rule không dùng chung bộ đếm")
    void rulesAreIndependent() throws Exception {
        props.setLogin(new RateLimitProperties.Rule(1, 60));
        props.setForgotPassword(new RateLimitProperties.Rule(1, 60));
        assertEquals(200, post("/api/auth/login", "1.1.1.1").getStatus());
        assertEquals(429, post("/api/auth/login", "1.1.1.1").getStatus());
        assertEquals(200, post("/forgot-password", "1.1.1.1", "identifier", "a").getStatus());
    }

    @Test
    @DisplayName("quên mật khẩu: giới hạn theo từng tài khoản đích để không spam thông báo cho admin")
    void forgotPasswordTarget() throws Exception {
        props.setForgotPasswordTarget(new RateLimitProperties.Rule(1, 900));
        assertEquals(200, post("/forgot-password", "1.1.1.1", "identifier", "admin").getStatus());
        assertEquals(429, post("/forgot-password", "9.9.9.9", "identifier", "ADMIN").getStatus());
    }

    @Test
    @DisplayName("webhook SePay: ngưỡng rộng nên các lần retry hợp lệ không bị chặn")
    void webhookAllowsRetries() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertEquals(200, post(RateLimitFilter.WEBHOOK_PATH, "3.3.3.3").getStatus());
        }
    }

    @Test
    @DisplayName("GET và đường dẫn khác không bị giới hạn")
    void otherRequestsUntouched() throws Exception {
        props.setLogin(new RateLimitProperties.Rule(1, 60));
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/login");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(get, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
            assertEquals(200, post("/properties", "1.1.1.1").getStatus());
        }
    }

    @Test
    @DisplayName("tắt qua cấu hình thì không giới hạn")
    void disabled() throws Exception {
        props.setEnabled(false);
        props.setLogin(new RateLimitProperties.Rule(1, 60));
        for (int i = 0; i < 5; i++) {
            assertEquals(200, post("/api/auth/login", "1.1.1.1").getStatus());
        }
    }

    @Test
    @DisplayName("X-Forwarded-For bị bỏ qua nếu không tin cậy proxy; nếu tin cậy thì lấy phần tử proxy thêm vào")
    void clientIpResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

        assertEquals("10.0.0.1", filter.clientIp(request));

        props.setTrustForwardedFor(true);
        assertEquals("203.0.113.9", filter.clientIp(request), "client giả mạo phần tử đầu không đổi được khóa");

        props.setTrustedProxyHops(2);
        assertEquals("6.6.6.6", filter.clientIp(request));

        props.setTrustedProxyHops(5);
        assertEquals("10.0.0.1", filter.clientIp(request), "ít phần tử hơn số hop thì quay về IP kết nối");
    }

    @Test
    @DisplayName("bộ đếm: cửa sổ mới reset, Retry-After tính theo thời gian còn lại")
    void limiterWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);
        assertTrue(limiter.tryAcquire("r", "k", 1, 10).allowed());
        var denied = limiter.tryAcquire("r", "k", 1, 10);
        assertFalse(denied.allowed());
        assertEquals(10, denied.retryAfterSeconds());
        clock.advanceSeconds(10);
        assertTrue(limiter.tryAcquire("r", "k", 1, 10).allowed());
    }

    @Test
    @DisplayName("bộ đếm: dọn khóa hết hạn theo cửa sổ của chính khóa đó, không xóa sớm bộ đếm của rule cửa sổ dài")
    void cleanupKeepsLongerWindows() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);
        assertTrue(limiter.tryAcquire("long", "victim", 1, 900).allowed());
        assertFalse(limiter.tryAcquire("long", "victim", 1, 900).allowed());
        for (int i = 0; i <= 10_000; i++) {
            limiter.tryAcquire("short", "k" + i, 1, 60);
        }
        clock.advanceSeconds(61);
        limiter.tryAcquire("short", "trigger-cleanup", 1, 60);

        assertFalse(limiter.tryAcquire("long", "victim", 1, 900).allowed(),
            "khóa cửa sổ 900 giây mới qua 61 giây vẫn phải bị chặn");
    }
}
