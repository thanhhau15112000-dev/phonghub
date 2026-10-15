package com.phonghub.application;

import com.phonghub.application.service.AuditSanitizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** #78: nhật ký không bao giờ chứa mật khẩu, token hay số giấy tờ đầy đủ. */
class AuditSanitizerTest {

    @Test
    @DisplayName("che mật khẩu, token, khóa bí mật bất kể hoa thường, kể cả trong map lồng nhau và danh sách")
    void redactsSecrets() {
        Map<String, Object> clean = AuditSanitizer.sanitize(Map.of(
            "password", "hunter2",
            "newPassword", "abc",
            "accessToken", "eyJ...",
            "refresh_token", "xyz",
            "apiKey", "k",
            "nested", Map.of("Authorization", "Bearer abc", "ok", "keep"),
            "items", List.of(Map.of("secret", "s", "name", "n"))
        ));

        assertEquals(AuditSanitizer.REDACTED, clean.get("password"));
        assertEquals(AuditSanitizer.REDACTED, clean.get("newPassword"));
        assertEquals(AuditSanitizer.REDACTED, clean.get("accessToken"));
        assertEquals(AuditSanitizer.REDACTED, clean.get("refresh_token"));
        assertEquals(AuditSanitizer.REDACTED, clean.get("apiKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) clean.get("nested");
        assertEquals(AuditSanitizer.REDACTED, nested.get("Authorization"));
        assertEquals("keep", nested.get("ok"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) clean.get("items");
        assertEquals(AuditSanitizer.REDACTED, items.getFirst().get("secret"));
        assertEquals("n", items.getFirst().get("name"));
    }

    @Test
    @DisplayName("số giấy tờ chỉ giữ 4 ký tự cuối")
    void masksDocumentNumbers() {
        Map<String, Object> clean = AuditSanitizer.sanitize(Map.of(
            "identityCardNumber", "079201004321",
            "cccd", "1234",
            "documentNumber", "B1234567"
        ));

        assertEquals("********4321", clean.get("identityCardNumber"));
        assertEquals("****", clean.get("cccd"));
        assertEquals("****4567", clean.get("documentNumber"));
    }

    @Test
    @DisplayName("cờ nghiệp vụ như mustChangePassword và dữ liệu thường được giữ nguyên")
    void keepsOrdinaryFields() {
        Map<String, Object> clean = AuditSanitizer.sanitize(Map.of(
            "mustChangePassword", true,
            "passwordChanged", true,
            "oldStatus", "DRAFT",
            "count", 3
        ));

        assertEquals(true, clean.get("mustChangePassword"));
        assertEquals(true, clean.get("passwordChanged"));
        assertEquals("DRAFT", clean.get("oldStatus"));
        assertEquals(3, clean.get("count"));
    }

    @Test
    @DisplayName("null được giữ nguyên")
    void nullSafe() {
        assertNull(AuditSanitizer.sanitize(null));
    }
}
