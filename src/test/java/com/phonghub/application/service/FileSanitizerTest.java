package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Kiểm tra magic bytes và việc loại bỏ metadata (EXIF/XMP/tEXt) trước khi lưu tệp tải lên. */
class FileSanitizerTest {

    private static final long LIMIT = 1024 * 1024;
    private static final String SECRET = "SECRET-GPS-10.7769,106.7009";

    @Test
    @DisplayName("Rejects empty, oversized, unknown and mismatched files")
    void rejectsInvalidFiles() throws IOException {
        byte[] png = image("png");
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/png", null, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/png", new byte[0], LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/png", png, png.length - 1));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/gif", png, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize(null, png, LIMIT));
        // khai PNG nhưng nội dung là JPEG / HTML
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/png", image("jpg"), LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("application/pdf",
            "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII), LIMIT));
        // đúng chữ ký JPEG nhưng phần thân hỏng
        byte[] corrupt = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x01, 0x02, 0x03};
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/jpeg", corrupt, LIMIT));
    }

    @Test
    @DisplayName("JPEG is re-encoded without its EXIF segment")
    void jpegLosesExif() throws IOException {
        byte[] plain = image("jpg");
        byte[] exif = ("Exif\0\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(plain, 0, 2);
        withExif.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, (byte) (exif.length + 2)});
        withExif.write(exif);
        withExif.write(plain, 2, plain.length - 2);
        assertTrue(contains(withExif.toByteArray(), SECRET));

        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize("IMAGE/JPEG; charset=binary", withExif.toByteArray(),
            LIMIT);
        assertEquals("image/jpeg", file.contentType());
        assertEquals("jpg", file.extension());
        assertFalse(contains(file.content(), SECRET));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(file.content())));
    }

    @Test
    @DisplayName("PNG is re-encoded without its text chunks")
    void pngLosesTextChunk() throws IOException {
        byte[] plain = image("png");
        byte[] text = ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
        int iend = plain.length - 12;
        ByteArrayOutputStream withText = new ByteArrayOutputStream();
        withText.write(plain, 0, iend);
        withText.write(ByteBuffer.allocate(4).putInt(text.length).array());
        byte[] typeAndData = concat("tEXt".getBytes(StandardCharsets.US_ASCII), text);
        withText.write(typeAndData);
        CRC32 crc = new CRC32();
        crc.update(typeAndData);
        withText.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        withText.write(plain, iend, 12);
        assertTrue(contains(withText.toByteArray(), SECRET));

        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize("image/png", withText.toByteArray(), LIMIT);
        assertEquals("png", file.extension());
        assertFalse(contains(file.content(), SECRET));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(file.content())));
    }

    @Test
    @DisplayName("WebP drops EXIF and XMP chunks, clears their VP8X flags and rewrites the RIFF size")
    void webpLosesMetadataChunks() {
        byte[] vp8x = {0x1c, 0, 0, 0, 0, 0, 0, 0, 0, 0}; // alpha 0x10 + EXIF 0x08 + XMP 0x04
        byte[] webp = webp(
            chunk("VP8X", vp8x),
            chunk("VP8 ", new byte[]{1, 2, 3}),
            chunk("EXIF", ("EXIF-" + SECRET).getBytes(StandardCharsets.US_ASCII)),
            chunk("XMP ", ("XMP-" + SECRET).getBytes(StandardCharsets.US_ASCII)));

        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize("image/webp", webp, LIMIT);
        byte[] out = file.content();
        assertEquals("webp", file.extension());
        assertFalse(contains(out, SECRET));
        assertFalse(contains(out, "EXIF") || contains(out, "XMP "));
        assertEquals(12 + 18 + 12, out.length, "VP8X (10) + VP8 (3 + 1 byte đệm)");
        assertEquals(out.length - 8, ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(0x10, out[20], "chỉ còn cờ alpha");
        assertArrayEquals(webp(chunk("VP8X", new byte[]{0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
            chunk("VP8 ", new byte[]{1, 2, 3})), out);
    }

    @Test
    @DisplayName("WebP with a chunk size past the end or negative is rejected")
    void corruptWebp() {
        byte[] tooLong = webp(chunk("VP8 ", new byte[]{1, 2, 3, 4}));
        ByteBuffer.wrap(tooLong, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(100);
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/webp", tooLong, LIMIT));

        byte[] negative = webp(chunk("VP8 ", new byte[]{1, 2, 3, 4}));
        ByteBuffer.wrap(negative, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1);
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/webp", negative, LIMIT));

        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitize("image/webp",
            "RIFF0000WEB".getBytes(StandardCharsets.US_ASCII), LIMIT));
    }

    @Test
    @DisplayName("PDF passes through as a copy")
    void pdfIsCopied() {
        byte[] pdf = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize("application/pdf", pdf, LIMIT);
        assertEquals("pdf", file.extension());
        assertArrayEquals(pdf, file.content());
        assertNotSame(pdf, file.content());
    }

    @Test
    @DisplayName("Videos are accepted only with a matching mp4/quicktime/webm signature")
    void videos() {
        byte[] mp4 = concat(new byte[]{0, 0, 0, 0x18}, "ftypisom".getBytes(StandardCharsets.US_ASCII));
        byte[] webm = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0x01};

        assertEquals("mp4", FileSanitizer.sanitizeVideo("video/mp4", mp4, LIMIT).extension());
        assertEquals("mov", FileSanitizer.sanitizeVideo("Video/QuickTime", mp4, LIMIT).extension());
        FileSanitizer.SanitizedFile file = FileSanitizer.sanitizeVideo("video/webm;codecs=vp9", webm, LIMIT);
        assertEquals("video/webm", file.contentType());
        assertEquals("webm", file.extension());
        assertArrayEquals(webm, file.content());

        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/mp4", webm, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/mp4",
            Arrays.copyOf(mp4, 11), LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/webm", mp4, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/x-msvideo", mp4, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/mp4", new byte[0], LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/mp4", null, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> FileSanitizer.sanitizeVideo("video/mp4", mp4, 5));
    }

    @Test
    @DisplayName("isImage and isVideo normalise the declared content type")
    void contentTypeChecks() {
        assertTrue(FileSanitizer.isImage(" Image/WEBP "));
        assertTrue(FileSanitizer.isImage("image/jpeg; q=1"));
        assertFalse(FileSanitizer.isImage("application/pdf"));
        assertFalse(FileSanitizer.isImage(null));
        assertTrue(FileSanitizer.isVideo("VIDEO/MP4"));
        assertTrue(FileSanitizer.isVideo("video/quicktime"));
        assertFalse(FileSanitizer.isVideo("image/png"));
        assertFalse(FileSanitizer.isVideo(null));
    }

    @Test
    @DisplayName("JPEG chụp dọc (EXIF Orientation) được xoay thật sang đúng chiều trước khi bỏ EXIF")
    void jpegKeepsOrientationAfterExifIsRemoved() throws IOException {
        // Ảnh gốc 40x20: nửa trái đỏ, nửa phải xanh
        Picture rotatedRight = Picture.of(FileSanitizer.sanitize("image/jpeg", orientedJpeg(6, false), LIMIT));
        assertEquals(20, rotatedRight.width());
        assertEquals(40, rotatedRight.height());
        assertTrue(rotatedRight.isRed(10, 3) && rotatedRight.isBlue(10, 36), "xoay 90° phải: nửa trái lên trên");

        Picture bigEndian = Picture.of(FileSanitizer.sanitize("image/jpeg", orientedJpeg(6, true), LIMIT));
        assertTrue(bigEndian.width() == 20 && bigEndian.isRed(10, 3), "EXIF kiểu Motorola (MM) cũng được đọc");

        Picture rotatedLeft = Picture.of(FileSanitizer.sanitize("image/jpeg", orientedJpeg(8, false), LIMIT));
        assertTrue(rotatedLeft.width() == 20 && rotatedLeft.isBlue(10, 3) && rotatedLeft.isRed(10, 36));

        Picture upsideDown = Picture.of(FileSanitizer.sanitize("image/jpeg", orientedJpeg(3, false), LIMIT));
        assertTrue(upsideDown.width() == 40 && upsideDown.isBlue(3, 10) && upsideDown.isRed(36, 10));

        Picture mirrored = Picture.of(FileSanitizer.sanitize("image/jpeg", orientedJpeg(2, false), LIMIT));
        assertTrue(mirrored.width() == 40 && mirrored.isBlue(3, 10) && mirrored.isRed(36, 10));

        Picture upright = Picture.of(FileSanitizer.sanitize("image/jpeg", orientedJpeg(1, false), LIMIT));
        assertTrue(upright.width() == 40 && upright.isRed(3, 10) && upright.isBlue(36, 10));
    }

    private record Picture(BufferedImage image) {
        static Picture of(FileSanitizer.SanitizedFile file) throws IOException {
            return new Picture(ImageIO.read(new ByteArrayInputStream(file.content())));
        }

        int width() { return image.getWidth(); }

        int height() { return image.getHeight(); }

        boolean isRed(int x, int y) {
            int rgb = image.getRGB(x, y);
            return ((rgb >> 16) & 0xff) > 180 && (rgb & 0xff) < 80;
        }

        boolean isBlue(int x, int y) {
            int rgb = image.getRGB(x, y);
            return (rgb & 0xff) > 180 && ((rgb >> 16) & 0xff) < 80;
        }
    }

    /** JPEG 40x20 (trái đỏ, phải xanh) kèm segment EXIF chỉ có thẻ Orientation. */
    private static byte[] orientedJpeg(int orientation, boolean bigEndian) throws IOException {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 20; y++) {
                image.setRGB(x, y, x < 20 ? 0xff0000 : 0x0000ff);
            }
        }
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", plain);
        ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
        tiff.put((byte) (bigEndian ? 'M' : 'I')).put((byte) (bigEndian ? 'M' : 'I')).putShort((short) 42).putInt(8);
        tiff.putShort((short) 1).putShort((short) 0x0112).putShort((short) 3).putInt(1)
            .putShort((short) orientation).putShort((short) 0).putInt(0);
        byte[] payload = concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff.array());
        byte[] jpeg = plain.toByteArray();
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, (byte) (payload.length + 2)});
        withExif.write(payload);
        withExif.write(jpeg, 2, jpeg.length - 2);
        return withExif.toByteArray();
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xff0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + data.length + (data.length & 1)).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(type.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        return buffer.array();
    }

    private static byte[] webp(byte[]... chunks) {
        byte[] body = new byte[0];
        for (byte[] chunk : chunks) {
            body = concat(body, chunk);
        }
        ByteBuffer buffer = ByteBuffer.allocate(12 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(body.length + 4)
            .put("WEBP".getBytes(StandardCharsets.US_ASCII)).put(body);
        return buffer.array();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static boolean contains(byte[] content, String text) {
        return new String(content, StandardCharsets.ISO_8859_1).contains(text);
    }
}
