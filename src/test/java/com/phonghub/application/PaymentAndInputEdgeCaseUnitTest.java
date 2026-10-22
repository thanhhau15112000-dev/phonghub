package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.*;

import com.phonghub.application.service.AuditSanitizer;
import com.phonghub.application.service.FileSanitizer;
import com.phonghub.domain.model.PaymentCode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Issue #159 + #94: Kiểm thử các trường hợp biên cho Nhận diện Mã Thanh toán (Payment Code),
 * Làm sạch tệp tin (FileSanitizer), và Khử độc nhật ký kiểm toán (AuditSanitizer).
 */
class PaymentAndInputEdgeCaseUnitTest {

    // =========================================================================
    // 1. PAYMENT CODE EXTRACTION EDGE CASES
    // =========================================================================

    @ParameterizedTest(name = "Nội dung \"{0}\" -> Trích xuất được mã \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "PH12345678 chuyen tien phong | PH12345678",
        "chuyen tien phong ph12345678 | PH12345678",
        "NOI DUNG: [PHABCDEF12]! | PHABCDEF12",
        "PHONGHUBPH12345678 | PH12345678",
        "nguyen van a chuyen khoan ph99998888 cho thang 10 | PH99998888",
        "   phabcdef12   | PHABCDEF12",
        "PH12345678vaPH87654321 | PH12345678" // Chứa nhiều mã
    })
    @DisplayName("PaymentCode: Trích xuất chính xác mã thanh toán bất kể hoa thường, khoảng trắng hoặc ký tự bao quanh")
    void testExtractPaymentCodeEdgeCases(String input, String expectedCode) {
        Set<String> candidates = PaymentCode.findCandidates(input);
        assertTrue(candidates.contains(expectedCode), "Phải tìm thấy mã: " + expectedCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "chuyen tien phong khong co ma",
        "PH123", // Quá ngắn (thiếu ký tự)
        "PH1234567890", // Cắt đúng 8 ký tự sau PH
        "MA PHONG 101"
    })
    @DisplayName("PaymentCode: Nội dung không hợp lệ hoặc rỗng không gây lỗi Exception")
    void testInvalidPaymentCodeInputs(String text) {
        assertDoesNotThrow(() -> {
            Set<String> candidates = PaymentCode.findCandidates(text);
            if (text.trim().isEmpty() || !text.toUpperCase().contains("PH")) {
                assertTrue(candidates.isEmpty());
            }
        });
    }

    @Test
    @DisplayName("PaymentCode: Text null trả về tập rỗng an toàn")
    void testNullPaymentCodeSafe() {
        Set<String> candidates = PaymentCode.findCandidates(null);
        assertNotNull(candidates);
        assertTrue(candidates.isEmpty());
    }

    // =========================================================================
    // 2. FILE SANITIZER EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("FileSanitizer: File 0-byte ném ngoại lệ IllegalArgumentException")
    void testZeroByteFileThrows() {
        byte[] emptyContent = new byte[0];
        assertThrows(IllegalArgumentException.class, () ->
            FileSanitizer.sanitize("image/png", emptyContent, 1024 * 1024)
        );
        assertThrows(IllegalArgumentException.class, () ->
            FileSanitizer.sanitizeVideo("video/mp4", emptyContent, 10 * 1024 * 1024)
        );
    }

    @Test
    @DisplayName("FileSanitizer: File vượt quá kích thước cho phép ném ngoại lệ")
    void testExceedingMaxFileSizeThrows() {
        byte[] largeData = new byte[100];
        assertThrows(IllegalArgumentException.class, () ->
            FileSanitizer.sanitize("image/png", largeData, 50)
        );
    }

    @Test
    @DisplayName("FileSanitizer: File giả mạo extension (ví dụ text file đổi tên thành .png) bị từ chối bằng Magic Bytes")
    void testFakeExtensionMismatchMagicBytes() {
        byte[] fakePng = "Đây là văn bản giả mạo file ảnh".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () ->
            FileSanitizer.sanitize("image/png", fakePng, 1024 * 1024)
        );
        assertThrows(IllegalArgumentException.class, () ->
            FileSanitizer.sanitize("image/jpeg", fakePng, 1024 * 1024)
        );
        assertThrows(IllegalArgumentException.class, () ->
            FileSanitizer.sanitize("application/pdf", fakePng, 1024 * 1024)
        );
    }

    // =========================================================================
    // 3. AUDIT SANITIZER EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("AuditSanitizer: Che giấu mọi biến thể tên trường nhạy cảm và token")
    void testAuditSanitizerSecretKeyVariations() {
        Map<String, Object> input = Map.of(
            "PASSWORD", "supersecret123",
            "client_secret", "xyzSecret",
            "api_key", "sk-123456",
            "token", "bearer-abc",
            "normal_note", "Ghi chú hợp đồng tiếng Việt có dấu: Cọc giữ chỗ phòng 101 ⭐"
        );

        Map<String, Object> sanitized = AuditSanitizer.sanitize(input);

        assertEquals(AuditSanitizer.REDACTED, sanitized.get("PASSWORD"));
        assertEquals(AuditSanitizer.REDACTED, sanitized.get("client_secret"));
        assertEquals(AuditSanitizer.REDACTED, sanitized.get("api_key"));
        assertEquals(AuditSanitizer.REDACTED, sanitized.get("token"));
        assertEquals("Ghi chú hợp đồng tiếng Việt có dấu: Cọc giữ chỗ phòng 101 ⭐", sanitized.get("normal_note"));
    }

    @Test
    @DisplayName("AuditSanitizer: Che giấu số CCCD/hộ chiếu chỉ giữ 4 số cuối, chuỗi ngắn bị che toàn bộ")
    void testAuditSanitizerShortDocumentNumbers() {
        Map<String, Object> input = Map.of(
            "identityCardNumber", "123", // < 4 ký tự -> che thành ****
            "cccd", "123456789012"
        );

        Map<String, Object> sanitized = AuditSanitizer.sanitize(input);
        assertEquals("****", sanitized.get("identityCardNumber"));
        assertEquals("********9012", sanitized.get("cccd"));
    }
}
