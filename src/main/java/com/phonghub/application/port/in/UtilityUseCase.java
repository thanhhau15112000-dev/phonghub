package com.phonghub.application.port.in;

import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Chỉ số điện nước theo kỳ và đơn giá dịch vụ theo nhà trọ. */
public interface UtilityUseCase {

    /**
     * Ghi chỉ số của một kỳ. Chỉ số kỳ trước tự lấy từ kỳ liền trước (hoặc chỉ số đầu kỳ trong biên bản check-in
     * đã duyệt); chỉ khi chưa có cả hai mới cần khai báo chỉ số gốc.
     */
    MeterReading recordReading(RecordReadingCommand command);

    List<MeterReading> listReadings(UUID roomId);

    /** Đọc chỉ số từ ảnh đồng hồ; chỉ gợi ý, không lưu gì. Độ tin cậy thấp thì không trả giá trị, buộc nhập tay. */
    OcrSuggestion readMeterPhoto(UUID roomId, MeterType meterType, String contentType, byte[] content);

    MeterReading attachPhoto(UUID readingId, String contentType, byte[] content);

    URI photoSignedUrl(UUID readingId);

    ServiceRate createRate(RateCommand command);

    ServiceRate updateRate(UUID rateId, RateCommand command);

    List<ServiceRate> listRates(UUID propertyId);

    record RecordReadingCommand(
        UUID roomId,
        MeterType meterType,
        BigDecimal currentValue,
        LocalDate readingDate,
        BigDecimal baselineValue,
        Replacement replacement,
        BigDecimal ocrValue,
        BigDecimal ocrConfidence
    ) {}

    record OcrSuggestion(BigDecimal value, BigDecimal confidence, boolean requiresManualEntry, String engine) {}

    record Replacement(BigDecimal oldFinalValue, BigDecimal newStartValue, String note) {}

    record RateCommand(UUID propertyId, String serviceName, ChargeType chargeType, BigDecimal pricePerUnit) {}
}
