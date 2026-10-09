package com.phonghub.application.port.out;

public interface TenantDataProtectionPort {

    ProtectedValue protect(String plaintext);

    String reveal(ProtectedValue protectedValue);

    String fingerprint(String plaintext);

    /** Mã hóa dữ liệu nhị phân (ảnh giấy tờ); kết quả tự mô tả key id và nonce nên chỉ cần lưu một khối. */
    byte[] encryptBytes(byte[] plaintext);

    byte[] decryptBytes(byte[] protectedBytes);

    record ProtectedValue(String ciphertext, String nonce, String fingerprint, String keyId) {}
}
