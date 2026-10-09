package com.phonghub.application.service;

import com.phonghub.application.port.in.FileStorageUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

public class PrivateFileStorageService implements FileStorageUseCase {

    private static final Duration MAX_SIGNED_URL_TTL = Duration.ofMinutes(10);
    private static final Map<String, FileType> ALLOWED_TYPES = Map.of(
        "image/jpeg", new FileType("jpg", FileKind.JPEG),
        "image/png", new FileType("png", FileKind.PNG),
        "image/webp", new FileType("webp", FileKind.WEBP),
        "application/pdf", new FileType("pdf", FileKind.PDF)
    );

    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final long maxFileSizeBytes;

    public PrivateFileStorageService(
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        long maxFileSizeBytes
    ) {
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Maximum file size must be positive");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public StoredFile upload(UploadFileCommand command) {
        if (command == null || command.propertyId() == null) {
            throw new IllegalArgumentException("Property is required");
        }
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, command.propertyId());

        byte[] content = command.content();
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (content.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("File exceeds the configured size limit");
        }

        String declaredType = normalizeContentType(command.contentType());
        FileType fileType = ALLOWED_TYPES.get(declaredType);
        if (fileType == null || !matchesMagicBytes(fileType.kind(), content)) {
            throw new IllegalArgumentException("File content does not match an allowed jpeg/png/webp/pdf type");
        }

        byte[] sanitized = sanitize(fileType.kind(), content);
        String objectKey = "properties/" + command.propertyId() + "/" + normalizeCategory(command.category()) + "/"
            + UUID.randomUUID() + "." + fileType.extension();
        storagePort.put(objectKey, declaredType, sanitized);
        return new StoredFile(objectKey, declaredType, sanitized.length);
    }

    @Override
    public URI createSignedUrl(UUID propertyId, String objectKey, Duration requestedTtl) {
        assertObjectAccess(propertyId, objectKey);
        Duration ttl = requestedTtl == null || requestedTtl.isNegative() || requestedTtl.isZero()
            ? MAX_SIGNED_URL_TTL
            : requestedTtl.compareTo(MAX_SIGNED_URL_TTL) > 0 ? MAX_SIGNED_URL_TTL : requestedTtl;
        return storagePort.createSignedUrl(objectKey, ttl);
    }

    @Override
    public void delete(UUID propertyId, String objectKey) {
        assertObjectAccess(propertyId, objectKey);
        storagePort.delete(objectKey);
    }

    private void assertObjectAccess(UUID propertyId, String objectKey) {
        if (propertyId == null || objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Property and object key are required");
        }
        authorizationService.assertCanManageProperty(currentUserPort.getCurrentUser(), propertyId);
        String expectedPrefix = "properties/" + propertyId + "/";
        if (!objectKey.startsWith(expectedPrefix) || objectKey.contains("..")) {
            throw new IllegalArgumentException("Object key does not belong to the property");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCategory(String category) {
        if (category == null || !category.matches("[a-z0-9][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException("Invalid storage category");
        }
        return category;
    }

    private static boolean matchesMagicBytes(FileKind kind, byte[] content) {
        return switch (kind) {
            case JPEG -> startsWith(content, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case PNG -> startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case PDF -> startsWith(content, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d});
            case WEBP -> content.length >= 12
                && Arrays.equals(Arrays.copyOfRange(content, 0, 4), "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(Arrays.copyOfRange(content, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
        };
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
            && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }

    private static byte[] sanitize(FileKind kind, byte[] content) {
        return switch (kind) {
            case JPEG -> reencodeImage(content, "jpg");
            case PNG -> reencodeImage(content, "png");
            case WEBP -> stripWebpMetadata(content);
            case PDF -> Arrays.copyOf(content, content.length);
        };
    }

    private static byte[] reencodeImage(byte[] content, String format) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalArgumentException("Image payload is corrupt");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalArgumentException("Image format cannot be sanitized");
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Image payload is corrupt", ex);
        }
    }

    private static byte[] stripWebpMetadata(byte[] content) {
        try {
            ByteArrayOutputStream chunks = new ByteArrayOutputStream();
            int offset = 12;
            while (offset + 8 <= content.length) {
                String type = new String(content, offset, 4, StandardCharsets.US_ASCII);
                int size = ByteBuffer.wrap(content, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int paddedSize = size + (size & 1);
                if (size < 0 || offset + 8L + paddedSize > content.length) {
                    throw new IllegalArgumentException("WebP payload is corrupt");
                }
                if (!type.equals("EXIF") && !type.equals("XMP ")) {
                    byte[] chunk = Arrays.copyOfRange(content, offset, offset + 8 + paddedSize);
                    if (type.equals("VP8X") && size >= 1) {
                        chunk[8] = (byte) (chunk[8] & ~0x0c);
                    }
                    chunks.write(chunk);
                }
                offset += 8 + paddedSize;
            }
            byte[] body = chunks.toByteArray();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            result.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            result.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.length + 4).array());
            result.write("WEBP".getBytes(StandardCharsets.US_ASCII));
            result.write(body);
            return result.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to sanitize WebP image", ex);
        }
    }

    private record FileType(String extension, FileKind kind) {}

    private enum FileKind { JPEG, PNG, WEBP, PDF }
}
