package com.phonghub.adapter.out.ocr;

import com.phonghub.application.port.out.MeterOcrPort;
import com.phonghub.domain.model.MeterType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Đọc chỉ số bằng lệnh {@code tesseract} (#67): ảnh coi như một dòng số ({@code --psm 7}), chỉ nhận chữ số và dấu
 * chấm. Độ tin cậy là độ tin cậy thấp nhất trong các cụm Tesseract nhận ra, nên chỉ cần một chữ số đọc không chắc là
 * cả kết quả rơi xuống dưới ngưỡng và người dùng phải nhập tay. Lỗi, quá thời gian hoặc đang bận đều trả về "không
 * đọc được" thay vì ném lỗi: OCR chỉ là gợi ý, không được chặn việc gửi chỉ số.
 */
public class TesseractMeterOcrAdapter implements MeterOcrPort {

    public static final String ENGINE = "tesseract";

    private static final Logger log = LoggerFactory.getLogger(TesseractMeterOcrAdapter.class);
    private static final Pattern READING = Pattern.compile("\\d+(\\.\\d+)?");
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;

    /** Chạy lệnh và trả stdout; tách ra để test phần phân tích kết quả mà không cần cài Tesseract. */
    @FunctionalInterface
    public interface CommandRunner {
        /** Rỗng khi lệnh lỗi hoặc quá thời gian. */
        Optional<String> run(List<String> command, Duration timeout);
    }

    private final String executable;
    private final Duration timeout;
    private final Semaphore slots;
    private final CommandRunner runner;

    public TesseractMeterOcrAdapter(String executable, Duration timeout, int maxConcurrent) {
        this(executable, timeout, maxConcurrent, TesseractMeterOcrAdapter::runProcess);
    }

    TesseractMeterOcrAdapter(String executable, Duration timeout, int maxConcurrent, CommandRunner runner) {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("Tesseract executable is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Tesseract timeout must be positive");
        }
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("Tesseract max concurrency must be at least 1");
        }
        this.executable = executable.trim();
        this.timeout = timeout;
        this.slots = new Semaphore(maxConcurrent);
        this.runner = runner;
    }

    /** Kiểm tra lệnh có chạy được không (lúc khởi động) để chọn engine; không có thì cấu hình về nhập tay. */
    public static boolean isAvailable(String executable, Duration timeout) {
        return runProcess(List.of(executable, "--version"), timeout).isPresent();
    }

    @Override
    public OcrResult read(MeterType type, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            return unreadable();
        }
        boolean acquired = false;
        Path dir = null;
        try {
            // Gói free trên Render chỉ có 512 MB: giới hạn số tiến trình Tesseract chạy cùng lúc, chờ quá lâu thì bỏ qua.
            acquired = slots.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Bỏ qua OCR chỉ số {}: đang có quá nhiều ảnh chờ đọc", type);
                return unreadable();
            }
            dir = Files.createTempDirectory("meter-ocr-");
            Path image = dir.resolve("meter." + extension(contentType));
            Files.write(image, content);
            List<String> command = List.of(executable, image.toString(), "stdout", "--psm", "7",
                "-c", "tessedit_char_whitelist=0123456789.", "tsv");
            return runner.run(command, timeout).map(TesseractMeterOcrAdapter::parseTsv).orElseGet(() -> {
                log.warn("Tesseract không đọc được ảnh chỉ số {} (lỗi hoặc quá {} giây)", type, timeout.toSeconds());
                return unreadable();
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unreadable();
        } catch (IOException | RuntimeException ex) {
            log.warn("OCR chỉ số {} bị lỗi: {}", type, ex.toString());
            return unreadable();
        } finally {
            if (acquired) {
                slots.release();
            }
            deleteQuietly(dir);
        }
    }

    /**
     * Ghép các cụm chữ (mức 5 trong TSV) theo thứ tự đọc. Kết quả phải là đúng một số dạng {@code 12345} hoặc
     * {@code 1234.5}; còn lại (rỗng, nhiều dấu chấm...) coi như không đọc được.
     */
    static OcrResult parseTsv(String tsv) {
        StringBuilder text = new StringBuilder();
        double minConfidence = Double.MAX_VALUE;
        for (String line : tsv.split("\\R")) {
            String[] cols = line.split("\t", -1);
            if (cols.length < 12 || !"5".equals(cols[0])) {
                continue;
            }
            String word = cols[11].trim();
            double confidence;
            try {
                confidence = Double.parseDouble(cols[10].trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (word.isEmpty() || confidence < 0) {
                continue;
            }
            text.append(word);
            minConfidence = Math.min(minConfidence, confidence);
        }
        String reading = text.toString();
        if (reading.isEmpty() || !READING.matcher(reading).matches()) {
            return unreadable();
        }
        BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(100, minConfidence)))
            .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        return new OcrResult(new BigDecimal(reading), confidence, ENGINE);
    }

    private static OcrResult unreadable() {
        return new OcrResult(null, BigDecimal.ZERO, ENGINE);
    }

    private static String extension(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (type.contains("png")) {
            return "png";
        }
        if (type.contains("webp")) {
            return "webp";
        }
        return "jpg";
    }

    private static Optional<String> runProcess(List<String> command, Duration timeout) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            // Tesseract mặc định dùng mọi nhân qua OpenMP; một luồng là đủ cho một dòng số và đỡ tốn RAM.
            builder.environment().put("OMP_THREAD_LIMIT", "1");
            process = builder.start();
        } catch (IOException ex) {
            return Optional.empty();
        }
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            return Optional.of(stdout.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            process.destroyForcibly();
            return Optional.empty();
        }
    }

    private static String readLimited(InputStream in) {
        try (in) {
            byte[] bytes = in.readNBytes(MAX_OUTPUT_BYTES);
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var files = Files.list(dir)) {
            List<Path> paths = new ArrayList<>(files.toList());
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
            Files.deleteIfExists(dir);
        } catch (IOException ex) {
            log.debug("Không xóa được thư mục tạm OCR {}", dir, ex);
        }
    }
}
