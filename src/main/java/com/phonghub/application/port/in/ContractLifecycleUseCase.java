package com.phonghub.application.port.in;

import java.time.LocalDate;

/** Job hằng ngày của hợp đồng (#87): chuyển hết hạn và nhắc trước hạn. */
public interface ContractLifecycleUseCase {

    /**
     * Idempotent: chạy lại trong cùng ngày không đổi thêm gì. Hợp đồng ACTIVE đã qua ngày kết thúc chuyển EXPIRED
     * (phòng giữ nguyên, phải check-out); hợp đồng sắp hết hạn trong số ngày cấu hình được nhắc một lần cho mỗi ngày
     * kết thúc.
     */
    RunResult runDaily(LocalDate today);

    record RunResult(int expired, int reminded) {}
}
