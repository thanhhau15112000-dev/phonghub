package com.phonghub.application.port.out;

public interface TenantDataProtectionPort {

    ProtectedValue protect(String plaintext);

    String reveal(ProtectedValue protectedValue);

    String fingerprint(String plaintext);

    /** Mã hóa dữ liệu nhị phân (ảnh giấy tờ); kết quả tự mô tả key id và nonce nên chỉ cần lưu một khối. */
    byte[] encryptBytes(byte[] plaintext);

    byte[] decryptBytes(byte[] protectedBytes);

    /**
     * Mã hóa một trường thông tin cá nhân thành một chuỗi tự mô tả (có key id và nonce). {@code purpose} gắn
     * ciphertext với đúng cột (ví dụ "users.email") nên không thể dán sang cột khác. Null được giữ là null.
     */
    String encryptField(String purpose, String plaintext);

    String decryptField(String purpose, String stored);

    /** Chỉ mục mù: HMAC xác định của giá trị đã chuẩn hóa, dùng để tra cứu và chống trùng mà không lưu bản rõ. */
    String blindIndex(String purpose, String normalizedValue);

    record ProtectedValue(String ciphertext, String nonce, String fingerprint, String keyId) {}
}
