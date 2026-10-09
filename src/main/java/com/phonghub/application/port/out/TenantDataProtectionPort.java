package com.phonghub.application.port.out;

public interface TenantDataProtectionPort {

    ProtectedValue protect(String plaintext);

    String reveal(ProtectedValue protectedValue);

    String fingerprint(String plaintext);

    record ProtectedValue(String ciphertext, String nonce, String fingerprint, String keyId) {}
}
