package com.phonghub.adapter.out.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.phonghub.application.port.out.MeterOcrPort.OcrResult;
import com.phonghub.domain.model.MeterType;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #67: phân tích kết quả Tesseract, giới hạn tiến trình và dọn file tạm; đọc ảnh thật khi máy có lệnh tesseract. */
class TesseractMeterOcrAdapterTest {

    private static final String HEADER = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n";
    private static final byte[] IMAGE = {1, 2, 3};

    private static String tsv(String... wordsAndConfidences) {
        StringBuilder out = new StringBuilder(HEADER)
            .append("1\t1\t0\t0\t0\t0\t0\t0\t420\t120\t-1\t\n")
            .append("4\t1\t1\t1\t1\t0\t21\t24\t106\t26\t-1\t\n");
        for (int i = 0; i + 1 < wordsAndConfidences.length; i += 2) {
            out.append("5\t1\t1\t1\t1\t").append(i / 2 + 1).append("\t21\t24\t106\t26\t")
                .append(wordsAndConfidences[i + 1]).append('\t').append(wordsAndConfidences[i]).append('\n');
        }
        return out.toString();
    }

    private static TesseractMeterOcrAdapter adapter(TesseractMeterOcrAdapter.CommandRunner runner) {
        return new TesseractMeterOcrAdapter("tesseract", Duration.ofSeconds(2), 1, runner);
    }

    @Test
    @DisplayName("Một cụm số: giá trị và độ tin cậy quy về [0, 1]")
    void parsesSingleWord() {
        OcrResult result = TesseractMeterOcrAdapter.parseTsv(tsv("01250.5", "96.092445"));
        assertEquals(new BigDecimal("01250.5"), result.value());
        assertEquals(new BigDecimal("0.961"), result.confidence());
        assertEquals("tesseract", result.engine());
    }

    @Test
    @DisplayName("Nhiều cụm được ghép lại; độ tin cậy lấy cụm thấp nhất")
    void joinsWordsAndUsesLowestConfidence() {
        OcrResult result = TesseractMeterOcrAdapter.parseTsv(tsv("1250", "95", ".5", "40"));
        assertEquals(new BigDecimal("1250.5"), result.value());
        assertEquals(new BigDecimal("0.400"), result.confidence());
    }

    @Test
    @DisplayName("Rỗng, nhiều dấu chấm hay độ tin cậy không hợp lệ thì coi như không đọc được")
    void unreadableOutputs() {
        for (String output : List.of(HEADER, "", tsv("12.3.4", "90"), tsv(".", "90"), tsv("12", "abc"))) {
            OcrResult result = TesseractMeterOcrAdapter.parseTsv(output);
            assertNull(result.value(), output);
            assertEquals(0, result.confidence().signum());
        }
        // cụm có conf -1 (không phải chữ) bị bỏ qua
        assertEquals(new BigDecimal("88"), TesseractMeterOcrAdapter.parseTsv(tsv("88", "91", "7", "-1")).value());
    }

    @Test
    @DisplayName("Gọi tesseract với ảnh tạm đúng đuôi, chỉ nhận chữ số; xong thì xóa file tạm")
    void runsCommandAndCleansUp() {
        List<List<String>> calls = new ArrayList<>();
        List<Path> images = new ArrayList<>();
        TesseractMeterOcrAdapter ocr = adapter((command, timeout) -> {
            calls.add(command);
            Path image = Path.of(command.get(1));
            assertTrue(Files.exists(image), "ảnh phải được ghi ra trước khi gọi lệnh");
            images.add(image);
            return Optional.of(tsv("4321", "93"));
        });

        OcrResult png = ocr.read(MeterType.WATER, "image/png", IMAGE);
        ocr.read(MeterType.ELECTRIC, "image/webp", IMAGE);
        ocr.read(MeterType.ELECTRIC, "image/jpeg", IMAGE);

        assertEquals(new BigDecimal("4321"), png.value());
        assertEquals(List.of("tesseract", images.get(0).toString(), "stdout", "--psm", "7",
            "-c", "tessedit_char_whitelist=0123456789.", "tsv"), calls.get(0));
        assertTrue(images.get(0).toString().endsWith(".png"));
        assertTrue(images.get(1).toString().endsWith(".webp"));
        assertTrue(images.get(2).toString().endsWith(".jpg"));
        images.forEach(image -> {
            assertFalse(Files.exists(image), "file tạm phải bị xóa");
            assertFalse(Files.exists(image.getParent()), "thư mục tạm phải bị xóa");
        });
    }

    @Test
    @DisplayName("Lệnh lỗi, quá giờ, ném lỗi hay ảnh rỗng đều trả về không đọc được")
    void failuresBecomeManualEntry() {
        assertNull(adapter((c, t) -> Optional.empty()).read(MeterType.WATER, "image/png", IMAGE).value());
        assertNull(adapter((c, t) -> {
            throw new IllegalStateException("boom");
        }).read(MeterType.WATER, "image/png", IMAGE).value());
        TesseractMeterOcrAdapter neverCalled = adapter((c, t) -> {
            throw new AssertionError("không được gọi lệnh khi ảnh rỗng");
        });
        assertNull(neverCalled.read(MeterType.WATER, "image/png", new byte[0]).value());
        assertNull(neverCalled.read(MeterType.WATER, "image/png", null).value());
    }

    @Test
    @DisplayName("Hết lượt chạy đồng thời thì ảnh chờ quá thời gian bị bỏ qua thay vì chạy thêm tiến trình")
    void limitsConcurrency() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TesseractMeterOcrAdapter ocr = new TesseractMeterOcrAdapter("tesseract", Duration.ofMillis(200), 1,
            (command, timeout) -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of(tsv("100", "99"));
            });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<OcrResult> first = pool.submit(() -> ocr.read(MeterType.WATER, "image/png", IMAGE));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertNull(ocr.read(MeterType.WATER, "image/png", IMAGE).value(), "ảnh thứ hai không chờ được lượt");
            release.countDown();
            assertEquals(new BigDecimal("100"), first.get(5, TimeUnit.SECONDS).value());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Cấu hình sai bị từ chối ngay khi tạo")
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TesseractMeterOcrAdapter(" ", Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new TesseractMeterOcrAdapter("t", Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new TesseractMeterOcrAdapter("t", Duration.ofSeconds(1), 0));
        assertFalse(TesseractMeterOcrAdapter.isAvailable("phonghub-khong-co-lenh-nay", Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("Máy có tesseract: đọc đúng ảnh chỉ số in rõ")
    void readsRealImageWhenTesseractInstalled() throws Exception {
        assumeTrue(TesseractMeterOcrAdapter.isAvailable("tesseract", Duration.ofSeconds(5)),
            "Bỏ qua: máy không có lệnh tesseract");
        BufferedImage image = new BufferedImage(420, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 420, 120);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 64));
        g.drawString("04217", 30, 85);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        OcrResult result = new TesseractMeterOcrAdapter("tesseract", Duration.ofSeconds(15), 1)
            .read(MeterType.WATER, "image/png", png.toByteArray());
        assertEquals(0, new BigDecimal("4217").compareTo(result.value()), String.valueOf(result));
        assertTrue(result.confidence().compareTo(new BigDecimal("0.5")) > 0, String.valueOf(result));
    }
}
