package com.phonghub.application.port.in;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Xuất hóa đơn, công nợ, hợp đồng ra Excel/PDF (#85). Chỉ dữ liệu trong phạm vi quyền; số CCCD luôn được che.
 */
public interface ExportUseCase {

    ExportFile export(ExportRequest request);

    enum Dataset {
        INVOICES, DEBTS, CONTRACTS;

        /** Tên trên URL: invoices | debts | contracts (không phân biệt hoa thường). */
        public static Dataset parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IllegalArgumentException("Loại dữ liệu xuất không hợp lệ (invoices, debts, contracts).");
            }
        }
    }

    enum Format {
        XLSX, PDF;

        public static Format parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IllegalArgumentException("Định dạng xuất không hợp lệ (xlsx, pdf).");
            }
        }
    }

    /**
     * Khoảng kỳ chỉ áp dụng cho hóa đơn (kỳ của hóa đơn, mặc định 12 tháng gần nhất, tối đa 60 tháng); công nợ là
     * số còn nợ tại thời điểm xuất, hợp đồng lấy tất cả.
     */
    record ExportRequest(Dataset dataset, Format format, UUID propertyId, YearMonth from, YearMonth to) {
        public static final int MAX_MONTHS = 60;

        public ExportRequest {
            if (dataset == null) {
                throw new IllegalArgumentException("Cần chọn loại dữ liệu xuất.");
            }
            if (format == null) {
                format = Format.XLSX;
            }
            if (from != null && to != null) {
                if (from.isAfter(to)) {
                    throw new IllegalArgumentException("Tháng bắt đầu không được sau tháng kết thúc.");
                }
                if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
                    throw new IllegalArgumentException("Khoảng xuất tối đa " + MAX_MONTHS + " tháng.");
                }
            }
        }
    }

    record ExportFile(String filename, String contentType, byte[] content) {}
}
