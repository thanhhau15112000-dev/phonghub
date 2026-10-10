package com.phonghub.adapter.out.ocr;

import com.phonghub.application.port.out.MeterOcrPort;
import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;

/** Chưa gắn engine OCR: không đọc được gì nên luôn buộc nhập tay. Thay bằng adapter thật sau khi benchmark. */
public class DisabledMeterOcrAdapter implements MeterOcrPort {

    @Override
    public OcrResult read(MeterType type, String contentType, byte[] content) {
        return new OcrResult(null, BigDecimal.ZERO, "disabled");
    }
}
