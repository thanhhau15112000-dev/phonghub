package com.phonghub.adapter.out.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.application.port.out.TenantDataProtectionPort;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmTenantDataProtectorTest {

    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String HMAC_KEY = Base64.getEncoder().encodeToString(filledBytes((byte) 7));

    @Test
    void encryptsWithRandomNonceAndKeepsDeterministicFingerprint() {
        AesGcmTenantDataProtector protector = new AesGcmTenantDataProtector(
            "v1", ENCRYPTION_KEY, HMAC_KEY
        );

        TenantDataProtectionPort.ProtectedValue first = protector.protect("079201001111");
        TenantDataProtectionPort.ProtectedValue second = protector.protect("079201001111");

        assertNotEquals(first.ciphertext(), second.ciphertext());
        assertNotEquals(first.nonce(), second.nonce());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals("079201001111", protector.reveal(first));
        assertEquals("v1", first.keyId());
    }

    @Test
    void refusesCiphertextFromUnknownKeyId() {
        AesGcmTenantDataProtector protector = new AesGcmTenantDataProtector(
            "v1", ENCRYPTION_KEY, HMAC_KEY
        );
        TenantDataProtectionPort.ProtectedValue value = protector.protect("079201001111");

        assertThrows(IllegalStateException.class, () -> protector.reveal(
            new TenantDataProtectionPort.ProtectedValue(
                value.ciphertext(), value.nonce(), value.fingerprint(), "v2"
            )
        ));
    }

    @Test
    void handlesNonBase64FallbackAndBase64UrlKeys() {
        // Test with non-base64 placeholder string
        AesGcmTenantDataProtector protector1 = new AesGcmTenantDataProtector(
            "v1", "base64_encoded_32_byte_key", "base64_encoded_32_byte_key"
        );
        TenantDataProtectionPort.ProtectedValue val1 = protector1.protect("079201001111");
        assertEquals("079201001111", protector1.reveal(val1));

        // Test with quoted base64 key
        AesGcmTenantDataProtector protector2 = new AesGcmTenantDataProtector(
            "v1", "\"" + ENCRYPTION_KEY + "\"", "'" + HMAC_KEY + "'"
        );
        TenantDataProtectionPort.ProtectedValue val2 = protector2.protect("079201002222");
        assertEquals("079201002222", protector2.reveal(val2));
    }

    private static byte[] filledBytes(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
