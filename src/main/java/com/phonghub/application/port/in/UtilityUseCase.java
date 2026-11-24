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

    /**
     * Mốc chỉ số đầu kỳ tới của từng đồng hồ, lấy đúng như lúc ghi chỉ số (#366): chỉ số kỳ gần nhất của hợp đồng
     * đang thuê, hoặc chỉ số lúc nhận phòng. Chưa có mốc thì không có khóa đó. Người thuê của phòng và người quản lý
     * nhà trọ xem được; kỹ thuật viên thì không.
     */
    java.util.Map<MeterType, PreviousReading> previousReadings(UUID roomId);

    /**
     * Đọc chỉ số từ ảnh đồng hồ; chỉ gợi ý, không lưu gì. Độ tin cậy thấp thì không trả giá trị, buộc nhập tay.
     * Người quản lý nhà trọ và người thuê đang ở phòng (để tự điền số khi gửi chỉ số, #366) được gọi.
     */
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

    /** {@code period} là kỳ của lần ghi gần nhất; null khi mốc lấy từ biên bản nhận phòng. */
    record PreviousReading(BigDecimal value, java.time.YearMonth period) {
        public boolean fromCheckIn() {
            return period == null;
        }
    }

    record OcrSuggestion(BigDecimal value, BigDecimal confidence, boolean requiresManualEntry, String engine) {}

    record Replacement(BigDecimal oldFinalValue, BigDecimal newStartValue, String note) {}

    record RateCommand(UUID propertyId, String serviceName, ChargeType chargeType, BigDecimal pricePerUnit) {}
}
