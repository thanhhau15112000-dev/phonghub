package com.phonghub.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter ghi nhận traceId / requestId, HTTP route và mã người dùng đã băm (SHA-256)
 * vào MDC (Mapped Diagnostic Context) phục vụ log tập trung có cấu trúc JSON qua OpenSearch.
 *
 * Tuyệt đối không ghi CCCD, mật khẩu, token vào MDC hay log.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_ROUTE = "route";
    public static final String MDC_USER_ID_HASH = "userIdHash";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_TRACE_ID, requestId);
        MDC.put(MDC_ROUTE, request.getMethod() + " " + request.getRequestURI());

        // Đính kèm requestId vào header response để client/tester đối soát khi gặp lỗi
        response.setHeader(REQUEST_ID_HEADER, requestId);

        updateUserHash();

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID_HEADER);
        if (header != null && !header.isBlank()) {
            String sanitized = header.replaceAll("[^a-zA-Z0-9_-]", "");
            if (!sanitized.isBlank() && sanitized.length() <= 64) {
                return sanitized;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static void updateUserHash() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            MDC.put(MDC_USER_ID_HASH, sha256Hex(auth.getName()));
        } else {
            MDC.put(MDC_USER_ID_HASH, "anonymous");
        }
    }

    public static String sha256Hex(String input) {
        if (input == null || input.isBlank() || "anonymousUser".equalsIgnoreCase(input)) {
            return "anonymous";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}

