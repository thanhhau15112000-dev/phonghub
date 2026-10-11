package com.phonghub.domain.model;

/** Lý do một giao dịch SePay cần người quản lý xem xét (tiền nhận được nhưng không khớp hoàn toàn với hóa đơn). */
public enum PaymentReviewReason {
    /** Chuyển nhiều hơn số còn nợ của hóa đơn: hóa đơn đã đủ, phần dư chờ hoàn hoặc cấn trừ. */
    OVERPAID,
    /** Hóa đơn đã trả đủ, đã hủy hoặc đã gộp vào hóa đơn tháng nên không nhận thêm tiền. */
    INVOICE_NOT_PAYABLE,
    /** Không khớp được hóa đơn nào theo nội dung chuyển khoản. */
    UNMATCHED
}
