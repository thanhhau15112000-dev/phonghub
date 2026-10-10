package com.phonghub.application.port.out;

import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;

/** Đọc chỉ số từ ảnh đồng hồ. Kết quả chỉ là gợi ý, người dùng luôn phải xác nhận trước khi lưu. */
public interface MeterOcrPort {

    /** {@code value} là null khi engine không đọc được; {@code confidence} nằm trong [0, 1]. */
    OcrResult read(MeterType type, String contentType, byte[] content);

    record OcrResult(BigDecimal value, BigDecimal confidence, String engine) {}
}
