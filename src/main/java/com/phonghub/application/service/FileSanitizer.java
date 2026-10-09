package com.phonghub.application.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Kiểm tra loại tệp bằng magic bytes và loại bỏ metadata (EXIF/XMP) của ảnh trước khi lưu. */
public final class FileSanitizer {

    private static final Map<String, FileType> ALLOWED_TYPES = Map.of(
        "image/jpeg", new FileType("jpg", FileKind.JPEG),
        "image/png", new FileType("png", FileKind.PNG),
        "image/webp", new FileType("webp", FileKind.WEBP),
        "application/pdf", new FileType("pdf", FileKind.PDF)
    );

    private FileSanitizer() {}

    public record SanitizedFile(String contentType, String extension, byte[] content) {}

    public static SanitizedFile sanitize(String declaredContentType, byte[] content, long maxFileSizeBytes) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (content.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("File exceeds the configured size limit");
        }
        String declaredType = normalizeContentType(declaredContentType);
        FileType fileType = ALLOWED_TYPES.get(declaredType);
        if (fileType == null || !matchesMagicBytes(fileType.kind(), content)) {
            throw new IllegalArgumentException("File content does not match an allowed jpeg/png/webp/pdf type");
        }
        return new SanitizedFile(declaredType, fileType.extension(), clean(fileType.kind(), content));
    }

    public static boolean isImage(String contentType) {
        String type = normalizeContentType(contentType);
        return type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/webp");
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
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

    private static byte[] clean(FileKind kind, byte[] content) {
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
