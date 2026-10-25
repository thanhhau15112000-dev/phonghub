package com.phonghub.application.port.in;

import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Đối soát giao dịch SePay và biên lai thanh toán (#82). */
public interface PaymentReconciliationUseCase {

    /**
     * Giao dịch trong phạm vi được thu tiền: admin mọi giao dịch (kể cả chưa khớp hợp đồng), owner nhà của mình,
     * staff nhà được phân công có quyền thu tiền. Kỹ thuật viên và người thuê bị 403.
     */
    ReconciliationReport listTransactions(ReconciliationFilter filter);

    /**
     * Biên lai của hóa đơn đã có tiền thanh toán: người thuê của hợp đồng hoặc người quản lý nhà trọ (kỹ thuật viên
     * không xem). Hóa đơn chưa có khoản thanh toán nào thì chưa có biên lai (409).
     */
    Receipt getReceipt(UUID invoiceId);

    /** Trạng thái đối soát của một giao dịch, suy ra từ kết quả khớp lúc nhận webhook. */
    enum ReconciliationStatus {
        /** Đã cộng đủ vào đúng hóa đơn. */
        MATCHED,
        /** Khớp hóa đơn nhưng có tiền dư hoặc hóa đơn không còn nhận tiền: cần người quản lý xử lý. */
        NEEDS_REVIEW,
        /** Không khớp được hóa đơn nào theo nội dung chuyển khoản. */
        UNMATCHED,
        /** Tiền ra hoặc giao dịch trùng: không ảnh hưởng hóa đơn. */
        IGNORED;

        public static ReconciliationStatus of(PaymentTransaction tx) {
            if (tx.status() != PaymentStatus.SUCCESS) {
                return IGNORED;
            }
            if (tx.reviewReason() == PaymentReviewReason.UNMATCHED || tx.invoiceId() == null) {
                return UNMATCHED;
            }
            return tx.reviewReason() == null ? MATCHED : NEEDS_REVIEW;
        }
    }

    record ReconciliationFilter(UUID propertyId, ReconciliationStatus status, LocalDate from, LocalDate to) {
        public ReconciliationFilter {
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc.");
            }
        }
    }

    /** Các trường số tiền là null khi người xem là quản trị viên (#163). */
    record ReconciliationRow(
        UUID id,
        Long sepayId,
        Instant receivedAt,
        String gateway,
        String transactionType,
        String referenceCode,
        BigDecimal amount,
        BigDecimal appliedAmount,
        BigDecimal excessAmount,
        ReconciliationStatus status,
        PaymentReviewReason reviewReason,
        UUID contractId,
        UUID invoiceId,
        UUID propertyId,
        String propertyName,
        String roomNumber
    ) {}

    /**
     * {@code counts} đếm theo từng trạng thái trong phạm vi và khoảng ngày (trước khi lọc trạng thái) để làm thẻ lọc;
     * {@code rows} và các tổng tiền đã áp dụng cả bộ lọc trạng thái. Quản trị viên nhận tổng tiền null (#163).
     */
    record ReconciliationReport(
        List<ReconciliationRow> rows,
        Map<ReconciliationStatus, Long> counts,
        BigDecimal totalReceived,
        BigDecimal totalApplied,
        BigDecimal totalExcess
    ) {
        public long count(String status) {
            return counts.getOrDefault(ReconciliationStatus.valueOf(status), 0L);
        }
    }

    record Receipt(
        String receiptNumber,
        UUID invoiceId,
        UUID contractId,
        InvoiceType type,
        int month,
        int year,
        String paymentCode,
        String propertyName,
        String propertyAddress,
        String roomNumber,
        String tenantName,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        InvoiceStatus status,
        Instant paidAt,
        List<ReceiptPayment> payments
    ) {
        public boolean fullyPaid() {
            return remainingAmount.signum() == 0;
        }
    }

    /** Một lần tiền về được cộng vào hóa đơn; không kèm số tài khoản hay nội dung chuyển khoản của người thuê. */
    record ReceiptPayment(Instant receivedAt, String gateway, String referenceCode, BigDecimal appliedAmount) {}
}
