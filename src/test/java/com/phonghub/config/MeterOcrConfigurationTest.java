package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.phonghub.adapter.out.ocr.DisabledMeterOcrAdapter;
import com.phonghub.adapter.out.ocr.TesseractMeterOcrAdapter;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** #67: chọn engine OCR theo cấu hình; thiếu lệnh tesseract thì về nhập tay thay vì làm app lỗi khi khởi động. */
class MeterOcrConfigurationTest {

    private final PhongHubConfiguration configuration = new PhongHubConfiguration();

    @Test
    @DisplayName("Mặc định (disabled) hoặc giá trị lạ: không dùng OCR")
    void disabledByDefault() {
        assertInstanceOf(DisabledMeterOcrAdapter.class, configuration.meterOcrPort("disabled", "tesseract", 15, 1));
        assertInstanceOf(DisabledMeterOcrAdapter.class, configuration.meterOcrPort("google-vision", "tesseract", 15, 1));
    }

    @Test
    @DisplayName("Chọn tesseract nhưng máy không có lệnh: về nhập tay")
    void fallsBackWhenCommandMissing() {
        assertInstanceOf(DisabledMeterOcrAdapter.class,
            configuration.meterOcrPort(" Tesseract ", "phonghub-khong-co-lenh-nay", 2, 1));
    }

    @Test
    @DisplayName("Chọn tesseract và máy có lệnh: dùng Tesseract")
    void usesTesseractWhenAvailable() {
        assumeTrue(TesseractMeterOcrAdapter.isAvailable("tesseract", Duration.ofSeconds(5)),
            "Bỏ qua: máy không có lệnh tesseract");
        assertInstanceOf(TesseractMeterOcrAdapter.class, configuration.meterOcrPort("tesseract", "tesseract", 15, 1));
    }
}
