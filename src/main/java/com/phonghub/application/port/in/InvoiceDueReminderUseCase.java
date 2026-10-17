package com.phonghub.application.port.in;

import java.time.LocalDate;

/** Job hằng ngày nhắc người thuê hóa đơn sắp đến hạn (#86). */
public interface InvoiceDueReminderUseCase {

    /**
     * Idempotent: hóa đơn còn phải trả có hạn thanh toán trong số ngày cấu hình (tính cả hôm nay) được nhắc một lần cho
     * mỗi hạn thanh toán. Trả về số hóa đơn được nhắc.
     */
    int runDaily(LocalDate today);
}
