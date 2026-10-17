package com.phonghub.adapter.out.security;

import com.phonghub.application.port.out.TenantDataProtectionPort;
import java.nio.ByteBuffer;
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
    private static final byte[] DOCUMENT_AAD = "phonghub:tenant:document-image:v1".getBytes(StandardCharsets.UTF_8);
    private static final byte BLOB_VERSION = 1;
    private static final String FIELD_PREFIX = "enc1.";
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
    public byte[] encryptBytes(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Data to encrypt cannot be empty");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(DOCUMENT_AAD);
            byte[] encrypted = cipher.doFinal(plaintext);
            ByteBuffer blob = ByteBuffer.allocate(2 + keyIdBytes.length + NONCE_BYTES + encrypted.length);
            blob.put(BLOB_VERSION).put((byte) keyIdBytes.length).put(keyIdBytes).put(nonce).put(encrypted);
            return blob.array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt document data", ex);
        }
    }

    @Override
    public byte[] decryptBytes(byte[] protectedBytes) {
        try {
            ByteBuffer blob = ByteBuffer.wrap(protectedBytes);
            if (blob.get() != BLOB_VERSION) {
                throw new IllegalStateException("Unsupported encrypted document format");
            }
            byte[] storedKeyId = new byte[blob.get() & 0xff];
            blob.get(storedKeyId);
            if (!keyId.equals(new String(storedKeyId, StandardCharsets.UTF_8))) {
                throw new IllegalStateException("No encryption key configured for key id "
                    + new String(storedKeyId, StandardCharsets.UTF_8));
            }
            byte[] nonce = new byte[NONCE_BYTES];
            blob.get(nonce);
            byte[] encrypted = new byte[blob.remaining()];
            blob.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(DOCUMENT_AAD);
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException | java.nio.BufferUnderflowException ex) {
            throw new IllegalStateException("Unable to decrypt document data", ex);
        }
    }

    @Override
    public String encryptField(String purpose, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(fieldAad(purpose));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return FIELD_PREFIX + encoder.encodeToString(keyId.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt personal data field " + purpose, ex);
        }
    }

    @Override
    public String decryptField(String purpose, String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(FIELD_PREFIX)) {
            throw new IllegalStateException("Personal data field " + purpose + " is not encrypted");
        }
        String[] parts = stored.substring(FIELD_PREFIX.length()).split("\\.");
        if (parts.length != 3) {
            throw new IllegalStateException("Malformed encrypted personal data field " + purpose);
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String storedKeyId = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
            if (!keyId.equals(storedKeyId)) {
                throw new IllegalStateException("No encryption key configured for key id " + storedKeyId);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                new GCMParameterSpec(GCM_TAG_BITS, decoder.decode(parts[1])));
            cipher.updateAAD(fieldAad(purpose));
            return new String(cipher.doFinal(decoder.decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt personal data field " + purpose, ex);
        }
    }

    @Override
    public String blindIndex(String purpose, String normalizedValue) {
        if (normalizedValue == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            mac.update(("phonghub:blind:" + purpose + ":").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute blind index for " + purpose, ex);
        }
    }

    private static byte[] fieldAad(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("Encryption purpose is required");
        }
        return ("phonghub:pii:" + purpose + ":v1").getBytes(StandardCharsets.UTF_8);
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
        String clean = encoded.trim();
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(clean);
            if (decoded.length == 32 || (purpose.equalsIgnoreCase("HMAC") && decoded.length >= 32)) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(clean);
            if (decoded.length == 32 || (purpose.equalsIgnoreCase("HMAC") && decoded.length >= 32)) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(clean.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException nsae) {
            throw new IllegalStateException("SHA-256 algorithm not available", nsae);
        }
    }
}
