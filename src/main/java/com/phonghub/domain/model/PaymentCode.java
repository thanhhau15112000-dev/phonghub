package com.phonghub.domain.model;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mã nội dung chuyển khoản của kỳ thanh toán: "PH" + 8 ký tự [A-Z0-9].
 * Chỉ dùng chữ/số vì nhiều ngân hàng bỏ dấu cách, dấu gạch và ký tự đặc biệt trong nội dung.
 */
public final class PaymentCode {

    public static final String PREFIX = "PH";
    public static final int SUFFIX_LENGTH = 8;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    // Lookahead để bắt cả các ứng viên chồng lấn, ví dụ "PHONGHUBPHABCD2345" khi ngân hàng bỏ dấu cách.
    private static final Pattern CANDIDATE = Pattern.compile("(?=(" + PREFIX + "[A-Z0-9]{" + SUFFIX_LENGTH + "}))");

    private PaymentCode() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Các chuỗi có dạng mã thanh toán trong văn bản, theo thứ tự xuất hiện, không phân biệt hoa thường. */
    public static Set<String> findCandidates(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        Matcher matcher = CANDIDATE.matcher(text.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}
