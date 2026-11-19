package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.config.RateLimitProperties;
import com.phonghub.domain.model.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AiRateLimitInterceptorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "owner@phonghub.local", "Chủ trọ", UserRole.OWNER);

    private AiRateLimitInterceptor interceptor(RateLimitProperties props, CurrentUser user, SimpleMeterRegistry meters) {
        return new AiRateLimitInterceptor(props, new FixedWindowRateLimiter(clock), () -> {
            if (user == null) {
                throw new IllegalStateException("not authenticated");
            }
            return user;
        }, meters);
    }

    private static RateLimitProperties limitOf(int max) {
        RateLimitProperties props = new RateLimitProperties();
        props.setAi(new RateLimitProperties.Rule(max, 60));
        return props;
    }

    private static MockHttpServletRequest post() {
        return new MockHttpServletRequest("POST", "/api/v1/ai/operations/ask");
    }

    @Test
    void rejectsAboveLimitAndCountsMetric() throws Exception {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AiRateLimitInterceptor limiter = interceptor(limitOf(1), owner, meters);

        assertTrue(limiter.preHandle(post(), new MockHttpServletResponse(), new Object()));
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertFalse(limiter.preHandle(post(), rejected, new Object()));

        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        assertTrue(rejected.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("thử lại sau 60 giây"));
        assertEquals(1.0, meters.counter("phonghub.ratelimit.rejected", "path", "/api/v1/ai").count());
    }

    @Test
    void skipsWhenDisabledForNonPostAndForUnknownUser() throws Exception {
        RateLimitProperties disabled = limitOf(0);
        disabled.setEnabled(false);
        assertTrue(interceptor(disabled, owner, null).preHandle(post(), new MockHttpServletResponse(), new Object()));

        AiRateLimitInterceptor strict = interceptor(limitOf(0), owner, null);
        assertTrue(strict.preHandle(new MockHttpServletRequest("GET", "/api/v1/ai/x"),
            new MockHttpServletResponse(), new Object()));

        assertTrue(interceptor(limitOf(0), null, null).preHandle(post(), new MockHttpServletResponse(), new Object()));
    }
}
