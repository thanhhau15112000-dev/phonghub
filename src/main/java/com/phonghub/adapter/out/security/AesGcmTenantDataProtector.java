package com.phonghub.adapter.out.security;

import com.phonghub.application.port.out.TenantDataProtectionPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmTenantDataProtector implements TenantDataProtectionPort {

    private static final byte[] AAD = "phonghub:tenant:identity-card:v1".getBytes(StandardCharsets.UTF_8);
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String keyId;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom secureRandom;

    public AesGcmTenantDataProtector(
        String keyId,
        String encryptionKeyBase64,
        String hmacKeyBase64
    ) {
        this(keyId, encryptionKeyBase64, hmacKeyBase64, new SecureRandom());
    }

    AesGcmTenantDataProtector(
        String keyId,
        String encryptionKeyBase64,
        String hmacKeyBase64,
        SecureRandom secureRandom
    ) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Tenant encryption key id is required");
        }
        byte[] encryptionKeyBytes = decodeKey(encryptionKeyBase64, "encryption");
        byte[] hmacKeyBytes = decodeKey(hmacKeyBase64, "HMAC");
        if (encryptionKeyBytes.length != 32) {
            throw new IllegalArgumentException("Tenant encryption key must be exactly 32 bytes (AES-256)");
        }
        if (hmacKeyBytes.length < 32) {
            throw new IllegalArgumentException("Tenant HMAC key must be at least 32 bytes");
        }
        this.keyId = keyId.trim();
        this.encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");
        this.hmacKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
        this.secureRandom = secureRandom;
    }

    @Override
    public ProtectedValue protect(String plaintext) {
        String normalized = normalize(plaintext);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return new ProtectedValue(
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(nonce),
                fingerprint(normalized),
                keyId
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt tenant identity data", ex);
        }
    }

    @Override
    public String reveal(ProtectedValue protectedValue) {
        if (!keyId.equals(protectedValue.keyId())) {
            throw new IllegalStateException("No tenant encryption key configured for key id " + protectedValue.keyId());
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey,
                new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(protectedValue.nonce()))
            );
            cipher.updateAAD(AAD);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(protectedValue.ciphertext()));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt tenant identity data", ex);
        }
    }

    @Override
    public String fingerprint(String plaintext) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(normalize(plaintext).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to fingerprint tenant identity data", ex);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identity card number cannot be blank");
        }
        return value.trim().replaceAll("\\s+", "");
    }

    private static byte[] decodeKey(String encoded, String purpose) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Tenant " + purpose + " key is required");
        }
        try {
            return Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Tenant " + purpose + " key must be Base64 encoded", ex);
        }
    }
}
