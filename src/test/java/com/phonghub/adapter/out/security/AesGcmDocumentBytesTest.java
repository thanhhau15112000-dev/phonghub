package com.phonghub.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmDocumentBytesTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(filled((byte) 9));
    private static final String HMAC = Base64.getEncoder().encodeToString(filled((byte) 7));

    private final AesGcmTenantDataProtector protector = new AesGcmTenantDataProtector("v1", KEY, HMAC);

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }

    @Test
    void roundTripKeepsContentAndHidesPlaintext() {
        byte[] image = "PNG-IMAGE-CONTENT-OF-A-ID-CARD".getBytes(StandardCharsets.UTF_8);

        byte[] blob = protector.encryptBytes(image);

        assertArrayEquals(image, protector.decryptBytes(blob));
        assertFalse(new String(blob, StandardCharsets.ISO_8859_1).contains("ID-CARD"));
    }

    @Test
    void sameContentProducesDifferentCiphertextEachTime() {
        byte[] image = "same bytes".getBytes(StandardCharsets.UTF_8);

        assertNotEquals(Arrays.hashCode(protector.encryptBytes(image)), Arrays.hashCode(protector.encryptBytes(image)));
    }

    @Test
    void tamperedBlobIsRejected() {
        byte[] blob = protector.encryptBytes("secret".getBytes(StandardCharsets.UTF_8));
        blob[blob.length - 1] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> protector.decryptBytes(blob));
    }

    @Test
    void blobFromAnotherKeyIdOrKeyIsRejected() {
        byte[] blob = protector.encryptBytes("secret".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class,
            () -> new AesGcmTenantDataProtector("v2", KEY, HMAC).decryptBytes(blob));
        assertThrows(IllegalStateException.class,
            () -> new AesGcmTenantDataProtector("v1", OTHER_KEY, HMAC).decryptBytes(blob));
    }

    @Test
    void emptyOrTruncatedInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> protector.encryptBytes(new byte[0]));
        assertThrows(IllegalStateException.class, () -> protector.decryptBytes(new byte[]{1, 2}));
    }
}
