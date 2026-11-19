package com.phonghub.application.port.in;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phát hiện bất thường hóa đơn và công tơ bằng quy tắc (#156). Chạy được cả khi AI tắt: mọi số liệu do code tính.
 * Chỉ chủ trọ xem (admin không xem số liệu tiền, #163).
 */
public interface InvoiceAnomalyUseCase {

    /** Tăng vượt ngưỡng này (%) so với trung bình các kỳ trước thì coi là bất thường. */
    int SPIKE_PERCENT = 50;
    /** Số kỳ trước dùng để tính mức trung bình. */
    int BASELINE_PERIODS = 3;
    /** Giao dịch SePay cần xem xét quá số ngày này mà chưa xử lý thì báo. */
    int STALE_REVIEW_DAYS = 7;

    /** {@code propertyId} null là mọi nhà trọ (chưa ẩn) của chủ trọ. */
    AnomalyReport analyze(UUID propertyId, YearMonth period);

    enum Type {
        UTILITY_SPIKE("Điện/nước tăng vọt"),
        METER_ZERO("Chỉ số không đổi"),
        METER_BACKWARD("Chỉ số lùi"),
        MISSING_READING("Thiếu chỉ số"),
        DUPLICATE_RENT_INVOICE("Trùng hóa đơn tiền thuê"),
        STALE_PAYMENT_REVIEW("Giao dịch chờ xử lý lâu");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    enum Severity { HIGH, MEDIUM, LOW }

    /**
     * {@code evidence} là số liệu nguồn để đối chiếu (khóa → giá trị đã định dạng), {@code action} là việc nên làm.
     */
    record Anomaly(
        Type type, Severity severity, UUID propertyId, UUID roomId, String roomNumber,
        String message, Map<String, String> evidence, String action
    ) {}

    record AnomalyReport(YearMonth period, UUID propertyId, List<Anomaly> anomalies) {
        public long count(Severity severity) {
            return anomalies.stream().filter(a -> a.severity() == severity).count();
        }
    }
}
