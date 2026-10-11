package com.phonghub.adapter.out.security;

import java.util.Locale;

/**
 * Tên "mục đích" của từng cột chứa thông tin cá nhân (gắn vào AAD của AES-GCM và vào chỉ mục mù HMAC)
 * và cách chuẩn hóa giá trị trước khi tính chỉ mục mù. Dùng chung cho repository và migration để hai bên luôn khớp.
 */
public final class PersonalDataFields {

    public static final String USERS_EMAIL = "users.email";
    public static final String USERS_USERNAME = "users.username";
    public static final String USERS_PHONE = "users.phone";
    public static final String USERS_FULL_NAME = "users.full_name";

    public static final String TENANTS_FULL_NAME = "tenants.full_name";
    public static final String TENANTS_PHONE = "tenants.phone";
    public static final String TENANTS_EMAIL = "tenants.email";
    public static final String TENANTS_ADDRESS = "tenants.permanent_address";

    public static final String SEPAY_ACCOUNT_NUMBER = "sepay_transactions.account_number";
    public static final String SEPAY_SUB_ACCOUNT = "sepay_transactions.sub_account";
    public static final String SEPAY_CONTENT = "sepay_transactions.content";
    public static final String SEPAY_DESCRIPTION = "sepay_transactions.description";

    public static final String AUDIT_DETAILS = "audit_logs.details";

    private PersonalDataFields() {}

    /** Email và tên đăng nhập không phân biệt hoa thường. */
    public static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Số điện thoại: bỏ khoảng trắng, dấu chấm, gạch ngang, ngoặc. */
    public static String normalizePhone(String value) {
        return value == null || value.isBlank() ? null : value.trim().replaceAll("[\\s.\\-()]", "");
    }
}
