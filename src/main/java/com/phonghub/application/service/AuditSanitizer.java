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

    /** Giá trị thay cho số tiền khi người xem không được biết số liệu tài chính (admin xem nhật ký, #163). */
    public static final String MONEY_HIDDEN = "***";

    /** Khóa chứa số tiền: amount, totalAmount, rentAmount, depositAmount, basePrice, pricePerUnit, feeValue, cost... */
    private static final String MONEY_KEY = "[A-Za-z]*(?i:amount|price|priceperunit|cost|feevalue|rent|deposit)";
    /** Chi tiết dạng JSON (PostgreSQL): {@code "rentAmount":"3000000"} hoặc {@code "amount":150000}. */
    private static final Pattern MONEY_JSON = Pattern.compile(
        "(\"" + MONEY_KEY + "\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d+(?:\\.\\d+)?)");
    /** Chi tiết dạng {@code Map.toString()} (bộ nhớ trong): {@code rentAmount=3000000}. */
    private static final Pattern MONEY_MAP = Pattern.compile("((?:^|[{,\\s])" + MONEY_KEY + "=)([^,}\\]]*)");

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

    /** Che mọi giá trị tiền trong chi tiết nhật ký đã lưu (JSON hoặc Map.toString), giữ nguyên các trường khác. */
    public static String maskMoney(String details) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        String masked = MONEY_JSON.matcher(details).replaceAll("$1\"" + MONEY_HIDDEN + "\"");
        return MONEY_MAP.matcher(masked).replaceAll("$1" + MONEY_HIDDEN);
    }

    static String maskKeepingLastFour(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
