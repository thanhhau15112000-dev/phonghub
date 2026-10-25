package com.phonghub.application.service;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
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
            throw new IllegalArgumentException("Tệp không được rỗng.");
        }
        if (content.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("Tệp vượt quá dung lượng cho phép.");
        }
        String declaredType = normalizeContentType(declaredContentType);
        FileType fileType = ALLOWED_TYPES.get(declaredType);
        if (fileType == null || !matchesMagicBytes(fileType.kind(), content)) {
            throw new IllegalArgumentException("Nội dung tệp không phải ảnh JPEG/PNG/WebP hoặc PDF hợp lệ.");
        }
        return new SanitizedFile(declaredType, fileType.extension(), clean(fileType.kind(), content));
    }

    /** Video ngắn: mp4/quicktime (hộp ftyp) hoặc webm (EBML). Chỉ kiểm tra chữ ký; không xử lý lại nội dung. */
    public static SanitizedFile sanitizeVideo(String declaredContentType, byte[] content, long maxFileSizeBytes) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Tệp không được rỗng.");
        }
        if (content.length > maxFileSizeBytes) {
            throw new IllegalArgumentException("Video vượt quá dung lượng cho phép.");
        }
        String type = normalizeContentType(declaredContentType);
        boolean ok = switch (type) {
            case "video/mp4", "video/quicktime" -> content.length >= 12
                && Arrays.equals(Arrays.copyOfRange(content, 4, 8), "ftyp".getBytes(StandardCharsets.US_ASCII));
            case "video/webm" -> startsWith(content, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});
            default -> false;
        };
        if (!ok) {
            throw new IllegalArgumentException("Nội dung video không phải MP4/WebM/QuickTime hợp lệ.");
        }
        String extension = type.equals("video/webm") ? "webm" : type.equals("video/quicktime") ? "mov" : "mp4";
        return new SanitizedFile(type, extension, content);
    }

    public static boolean isVideo(String contentType) {
        String type = normalizeContentType(contentType);
        return type.equals("video/mp4") || type.equals("video/webm") || type.equals("video/quicktime");
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
            case JPEG -> reencodeImage(content, "jpg", jpegOrientation(content));
            case PNG -> reencodeImage(content, "png", 1);
            case WEBP -> stripWebpMetadata(content);
            case PDF -> Arrays.copyOf(content, content.length);
        };
    }

    private static byte[] reencodeImage(byte[] content, String format, int orientation) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalArgumentException("Không đọc được ảnh: tệp bị hỏng hoặc dùng định dạng màu không hỗ trợ.");
            }
            // EXIF bị bỏ khi mã hóa lại, nên chiều chụp (ảnh dọc của điện thoại) phải được xoay vào điểm ảnh
            image = applyOrientation(image, orientation);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalArgumentException("Không xử lý được định dạng ảnh này.");
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không đọc được ảnh: tệp bị hỏng hoặc dùng định dạng màu không hỗ trợ.", ex);
        }
    }

    /**
     * Thẻ Orientation (0x0112) trong segment EXIF (APP1) của JPEG, 1 đến 8 theo chuẩn EXIF.
     * Không có hoặc không đọc được thì coi như ảnh đã đúng chiều (1).
     */
    private static int jpegOrientation(byte[] content) {
        try {
            int offset = 2;
            while (offset + 4 <= content.length && (content[offset] & 0xff) == 0xff) {
                int marker = content[offset + 1] & 0xff;
                if (marker == 0xda || marker == 0xd9) {
                    break; // dữ liệu ảnh bắt đầu, sau đây không còn metadata
                }
                int length = ((content[offset + 2] & 0xff) << 8) | (content[offset + 3] & 0xff);
                int segmentEnd = offset + 2 + length;
                if (length < 2 || segmentEnd > content.length) {
                    break;
                }
                if (marker == 0xe1 && length >= 8
                    && new String(content, offset + 4, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
                    int orientation = tiffOrientation(content, offset + 10, segmentEnd);
                    if (orientation > 0) {
                        return orientation;
                    }
                }
                offset = segmentEnd;
            }
        } catch (RuntimeException ignored) {
            // EXIF hỏng không làm hỏng việc tải ảnh
        }
        return 1;
    }

    private static int tiffOrientation(byte[] content, int tiff, int end) {
        if (tiff + 8 > end) {
            return 0;
        }
        ByteOrder order = content[tiff] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer buffer = ByteBuffer.wrap(content, 0, end).order(order);
        int ifd = tiff + buffer.getInt(tiff + 4);
        if (ifd < tiff || ifd + 2 > end) {
            return 0;
        }
        int entries = buffer.getShort(ifd) & 0xffff;
        for (int i = 0; i < entries && ifd + 2 + (i + 1) * 12 <= end; i++) {
            int entry = ifd + 2 + i * 12;
            if ((buffer.getShort(entry) & 0xffff) == 0x0112) {
                int value = buffer.getShort(entry + 8) & 0xffff;
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    /** Xoay/lật điểm ảnh theo giá trị Orientation của EXIF để ảnh hiển thị đúng chiều khi không còn EXIF. */
    private static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // lật ngang
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // xoay 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // lật dọc
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // chuyển vị
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // xoay 90° phải
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // chuyển vị ngược
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: xoay 90° trái
        };
        BufferedImage oriented = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = oriented.createGraphics();
        try {
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return oriented;
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
                    throw new IllegalArgumentException("Không đọc được ảnh WebP: tệp bị hỏng.");
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
            throw new IllegalStateException("Không xử lý được ảnh WebP.", ex);
        }
    }

    private record FileType(String extension, FileKind kind) {}

    private enum FileKind { JPEG, PNG, WEBP, PDF }
}
