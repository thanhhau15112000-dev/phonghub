package com.phonghub.application.port.in;

import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.InvoiceType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Xuất hóa đơn, công nợ, hợp đồng ra Excel/PDF (#85). Chỉ dữ liệu trong phạm vi quyền; số CCCD luôn được che.
 */
public interface ExportUseCase {

    ExportFile export(ExportRequest request);

    /**
     * Xuất theo bộ lọc (#227). {@code selectedIds} null hoặc rỗng nghĩa là mọi dòng khớp bộ lọc (server tự tính lại);
     * có giá trị thì chỉ xuất các dòng đó, mọi id phải thuộc phạm vi của người xuất, nếu không từ chối cả yêu cầu.
     */
    ExportFile export(ExportRequest request, ExportFilter filter, Set<String> selectedIds);

    /** Xem trước một trang kết quả lọc, đúng các cột sẽ xuất. */
    Preview preview(ExportRequest request, ExportFilter filter, int page, int size);

    /** Số dòng tối đa mỗi lần xuất, để không làm quá tải máy chủ nhỏ. */
    int MAX_ROWS = 5000;

    /** Trạng thái hóa đơn dùng để lọc; {@code OVERDUE} tính theo ngày xuất, ưu tiên hơn trạng thái lưu. */
    enum InvoiceState { DRAFT, UNPAID, PARTIAL, PAID, OVERDUE, VOIDED }

    /**
     * Bộ lọc nhiều tiêu chí; tiêu chí nào null hoặc rỗng thì không lọc. Hóa đơn/công nợ dùng {@code states},
     * {@code types}, {@code dueFrom/dueTo}, {@code onlyOutstanding}; hợp đồng dùng {@code contractStatuses},
     * {@code startFrom/startTo}, {@code endFrom/endTo}. {@code propertyIds}, {@code roomNumber}, {@code tenantName}
     * dùng chung.
     */
    record ExportFilter(
        Set<UUID> propertyIds,
        String roomNumber,
        String tenantName,
        Set<InvoiceState> states,
        Set<InvoiceType> types,
        LocalDate dueFrom,
        LocalDate dueTo,
        boolean onlyOutstanding,
        Set<ContractStatus> contractStatuses,
        LocalDate startFrom,
        LocalDate startTo,
        LocalDate endFrom,
        LocalDate endTo
    ) {
        public static ExportFilter none() {
            return new ExportFilter(null, null, null, null, null, null, null, false, null, null, null, null, null);
        }
    }

    record PreviewRow(String id, List<String> cells) {}

    record Preview(List<String> columns, List<PreviewRow> rows, int total, int page, int size) {}

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
