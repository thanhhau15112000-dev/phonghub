package com.phonghub.application.port.in;

import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lịch sử thanh toán của người thuê (#248): tiền SePay đã gắn vào hóa đơn, yêu cầu báo chuyển khoản, khoản chủ trọ
 * ghi nhận tay và khoản cấn trừ tiền cọc, gom theo các hợp đồng (kể cả đã kết thúc) mà người đó là người thuê chính.
 */
public interface PaymentHistoryUseCase {

    /**
     * Người thuê xem của chính mình ({@code tenantId} null hoặc là chính họ). Chủ trọ và nhân viên có quyền thu tiền
     * xem của một người thuê, chỉ trong các nhà trọ họ được thu tiền. Quản trị viên (#163), kỹ thuật viên và người
     * thuê khác bị 403.
     */
    PaymentHistory history(UUID tenantId, Filter filter);

    enum Method {
        /** Chuyển khoản tự khớp qua SePay. */
        SEPAY,
        /** Người thuê báo đã chuyển khoản hoặc đã trả tiền mặt, chủ trọ xác nhận (#205). */
        TENANT_CLAIM,
        /** Chủ trọ hoặc nhân viên ghi nhận trực tiếp. */
        MANAGER_RECORD,
        /** Cấn trừ từ tiền cọc khi check-out hoặc hủy giữa kỳ. */
        DEPOSIT_OFFSET
    }

    enum Status {
        /** Đã ghi nhận vào hóa đơn. */
        SUCCESS,
        /** Chờ chủ trọ xác nhận. */
        PENDING,
        /** Chủ trọ từ chối, kèm lý do. */
        REJECTED,
        /** Số tiền không khớp hóa đơn (chuyển dư hoặc hóa đơn không còn nhận tiền): ghi nhận một phần hoặc chờ xử lý. */
        MISMATCH
    }

    record Filter(UUID contractId, Status status, LocalDate from, LocalDate to, int page, int size) {
        public Filter {
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc.");
            }
            page = Math.max(page, 1);
            size = size <= 0 ? 20 : Math.min(size, 100);
        }

        public static Filter all() {
            return new Filter(null, null, null, null, 1, 20);
        }
    }

    record Row(
        String id,
        Instant at,
        UUID contractId,
        String propertyName,
        String roomNumber,
        UUID invoiceId,
        InvoiceType invoiceType,
        Integer month,
        Integer year,
        BigDecimal amount,
        Method method,
        String reference,
        Status status,
        String rejectReason,
        boolean hasReceipt
    ) {}

    record PaymentHistory(
        List<Row> rows,
        int page,
        int totalPages,
        long totalRows,
        /** Tổng các khoản đã ghi nhận (trạng thái SUCCESS) trong bộ lọc, trên mọi trang. */
        BigDecimal totalRecorded,
        List<ContractOption> contracts
    ) {}

    record ContractOption(UUID id, String label, boolean ended) {}
}
