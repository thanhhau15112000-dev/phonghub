package com.phonghub.adapter.in.web;

import java.math.BigDecimal;

/** Đọc số từ ô nhập của form; nhập sai thì báo lỗi dễ hiểu thay vì trả trang 400 (#366). */
final class FormNumbers {

    private FormNumbers() {
    }

    /** Ô trống thì {@code null}; không phải số thì ném {@link IllegalArgumentException} với {@code message}. */
    static BigDecimal parseOptional(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(message);
        }
    }
}
