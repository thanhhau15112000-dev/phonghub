package com.phonghub.application.service;

import com.phonghub.domain.model.User;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Tìm và hiển thị tài khoản trong các danh sách chọn người (#249). Danh sách mở cho mọi chủ trọ nên chỉ đưa ra họ tên
 * và email hoặc số điện thoại đã che; tên đăng nhập, email và số điện thoại đầy đủ chỉ dùng để so khớp ở máy chủ.
 */
final class PersonLookup {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private PersonLookup() {
    }

    static int clampLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    /** Khớp theo họ tên, tên đăng nhập, email (không phân biệt dấu, chữ hoa) hoặc 3–4 số cuối số điện thoại. */
    static boolean matches(User user, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return true;
        }
        String needle = fold(q);
        if (fold(user.fullName()).contains(needle) || fold(user.username()).contains(needle)
            || fold(user.email()).contains(needle)) {
            return true;
        }
        String phone = user.phone() == null ? "" : user.phone().replaceAll("\\D", "");
        return q.matches("\\d{3,4}") && phone.endsWith(q);
    }

    /** Email đã che nếu có, không thì số điện thoại đã che. */
    static String maskedContact(User user) {
        String email = maskEmail(user.email());
        return email.isEmpty() ? maskPhone(user.phone()) : email;
    }

    /** {@code nguyenvana@gmail.com} thành {@code ng***@gmail.com}. */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = value.substring(0, at);
        String keep = local.length() > 2 ? local.substring(0, 2) : local.substring(0, 1);
        return keep + "***" + value.substring(at);
    }

    /** {@code 0912345612} thành {@code 09******12}. */
    static String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 6) {
            return digits.isEmpty() ? "" : "***";
        }
        return digits.substring(0, 2) + "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 2);
    }

    /** Hạ chữ thường và bỏ dấu tiếng Việt để "nguyen" tìm được "Nguyễn". */
    static String fold(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
