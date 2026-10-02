package com.phonghub.application.port.in;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Phát hành hóa đơn tháng tự động (tác vụ hệ thống, không gắn với người dùng đăng nhập). */
public interface MonthlyInvoiceUseCase {

    /**
     * Hợp đồng đang hiệu lực cần có hóa đơn tháng {@code period} nhưng chưa có. Tháng đầu tiên của hợp đồng
     * không tự tạo (quản lý tạo tay nếu cần); tháng nằm ngoài thời hạn hợp đồng cũng không tạo.
     */
    List<UUID> findContractsNeedingInvoice(YearMonth period);

    /** Tạo hóa đơn tháng cho một hợp đồng; trả về false nếu đã có sẵn hoặc hợp đồng không còn đủ điều kiện. */
    boolean issueForContract(UUID contractId, YearMonth period);
}
