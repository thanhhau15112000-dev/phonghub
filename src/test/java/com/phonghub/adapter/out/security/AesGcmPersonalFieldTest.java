package com.phonghub.adapter.out.security;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmPersonalFieldTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String HMAC = Base64.getEncoder().encodeToString(filled((byte) 7));

    private final AesGcmTenantDataProtector protector = new AesGcmTenantDataProtector("v1", KEY, HMAC);

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }

    @Test
    void roundTripKeepsUnicodeAndHidesPlaintext() {
        String name = "Nguyễn Văn Ánh";

        String stored = protector.encryptField("users.full_name", name);

        assertTrue(stored.startsWith("enc1."));
        assertFalse(stored.contains("Nguy"));
        assertEquals(name, protector.decryptField("users.full_name", stored));
    }

    @Test
    void nullStaysNullAndEmptyStringRoundTrips() {
        assertNull(protector.encryptField("tenants.email", null));
        assertNull(protector.decryptField("tenants.email", null));
        assertEquals("", protector.decryptField("tenants.email", protector.encryptField("tenants.email", "")));
    }

    @Test
    void sameValueGivesDifferentCiphertextEveryTime() {
        assertNotEquals(protector.encryptField("users.email", "a@x.vn"), protector.encryptField("users.email", "a@x.vn"));
    }

    @Test
    void ciphertextCannotBeMovedToAnotherColumn() {
        String stored = protector.encryptField("users.email", "a@x.vn");

        assertThrows(IllegalStateException.class, () -> protector.decryptField("users.phone", stored));
    }

    @Test
    void tamperedUnknownKeyOrPlaintextValuesAreRejected() {
        String stored = protector.encryptField("users.email", "a@x.vn");
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("AA") ? "BB" : "AA");

        assertThrows(IllegalStateException.class, () -> protector.decryptField("users.email", tampered));
        assertThrows(IllegalStateException.class, () -> protector.decryptField("users.email", "a@x.vn"));
        assertThrows(IllegalStateException.class,
            () -> new AesGcmTenantDataProtector("v2", KEY, HMAC).decryptField("users.email", stored));
        assertThrows(IllegalStateException.class, () -> protector.decryptField("users.email", "enc1.only-one-part"));
    }

    @Test
    void blindIndexIsDeterministicAndSeparatedByPurpose() {
        assertEquals(protector.blindIndex("users.email", "a@x.vn"), protector.blindIndex("users.email", "a@x.vn"));
        assertNotEquals(protector.blindIndex("users.email", "a@x.vn"), protector.blindIndex("tenants.email", "a@x.vn"));
        assertNotEquals(protector.blindIndex("users.email", "a@x.vn"), protector.blindIndex("users.email", "b@x.vn"));
        assertEquals(64, protector.blindIndex("users.email", "a@x.vn").length());
        assertNull(protector.blindIndex("users.email", null));
    }

    @Test
    void blindIndexDoesNotRevealThePlainValue() {
        assertFalse(protector.blindIndex("users.phone", "0901234567").contains("0901234567"));
        assertNotEquals(protector.fingerprint("0901234567"), protector.blindIndex("users.phone", "0901234567"));
    }

    @Test
    void emptyPurposeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> protector.encryptField(" ", "x"));
    }
}
