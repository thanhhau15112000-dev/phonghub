package com.phonghub.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Làm sạch chi tiết trước khi ghi nhật ký (#78): không bao giờ lưu mật khẩu, token, khóa bí mật và số giấy tờ đầy đủ.
 * Chạy ở adapter lưu trữ nên lỗi sót ở nơi gọi cũng không làm lộ dữ liệu.
 */
public final class AuditSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern SECRET_KEY = Pattern.compile(
        "(pass(word|wd)?|token|secret|api[-_]?key|authorization|cookie|credential|private[-_]?key|otp)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCUMENT_NUMBER_KEY = Pattern.compile(
        "(cccd|cmnd|identity|id[-_]?(card|number)|document[-_]?(number|no)|passport|license[-_]?number|gplx)",
        Pattern.CASE_INSENSITIVE);

    private AuditSanitizer() {}

    public static Map<String, Object> sanitize(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : details.entrySet()) {
            clean.put(e.getKey(), sanitizeValue(e.getKey(), e.getValue()));
        }
        return clean;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        String lowerKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (SECRET_KEY.matcher(lowerKey).find() && !lowerKey.contains("changed") && !lowerKey.contains("must")) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> nested) {
            return sanitize((Map<String, Object>) nested);
        }
        if (value instanceof Iterable<?> items) {
            List<Object> out = new ArrayList<>();
            for (Object item : items) {
                out.add(sanitizeValue(key, item));
            }
            return out;
        }
        if (DOCUMENT_NUMBER_KEY.matcher(lowerKey).find() && value instanceof CharSequence text) {
            return maskKeepingLastFour(text.toString());
        }
        return value;
    }

    static String maskKeepingLastFour(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
