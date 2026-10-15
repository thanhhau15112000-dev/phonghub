package com.phonghub.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

/** Ghi nhận đã nhắc hợp đồng sắp hết hạn (#87) để mỗi ngày kết thúc chỉ nhắc một lần, kể cả khi job chạy lại. */
public interface ContractReminderPort {

    /** Đánh dấu đã nhắc cho (hợp đồng, ngày kết thúc); trả false nếu đã nhắc trước đó. */
    boolean markExpiryReminder(UUID contractId, LocalDate endDate);
}
