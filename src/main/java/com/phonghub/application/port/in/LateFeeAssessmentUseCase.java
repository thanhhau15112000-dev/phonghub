package com.phonghub.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tính phạt trả trễ tự động (tác vụ hệ thống, không gắn với người dùng đăng nhập). */
public interface LateFeeAssessmentUseCase {

    /** Hóa đơn tiền thuê còn phải thanh toán và đã quá hạn vào ngày {@code today}. */
    List<UUID> findInvoicesToAssess(LocalDate today);

    /**
     * Tính và đặt dòng phạt cho một hóa đơn theo chính sách của nhà trọ. Đặt chứ không cộng dồn nên chạy lặp lại
     * không tính thêm. Trả về true nếu hóa đơn thực sự thay đổi.
     */
    boolean assessInvoice(UUID invoiceId, LocalDate today);
}
