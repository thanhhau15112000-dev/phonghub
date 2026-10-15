package com.phonghub.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

/** Ghi nhận đã nhắc hóa đơn sắp đến hạn (#86) để mỗi hạn thanh toán chỉ nhắc một lần, kể cả khi job chạy lại. */
public interface InvoiceReminderPort {

    /** Đánh dấu đã nhắc cho (hóa đơn, hạn thanh toán); trả false nếu đã nhắc trước đó. */
    boolean markDueReminder(UUID invoiceId, LocalDate dueDate);
}
