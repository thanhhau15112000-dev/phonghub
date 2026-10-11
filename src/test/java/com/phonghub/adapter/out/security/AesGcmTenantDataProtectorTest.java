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

    private static byte[] filledBytes(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
