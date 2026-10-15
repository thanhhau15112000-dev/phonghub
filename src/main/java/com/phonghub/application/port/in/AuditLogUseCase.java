package com.phonghub.application.port.in;

import com.phonghub.application.port.out.AuditQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Xem nhật ký kiểm toán: admin xem tất cả, owner chỉ xem sự kiện thuộc nhà trọ của mình. */
public interface AuditLogUseCase {

    int PAGE_SIZE = 50;

    /** Các nhà trọ mà người dùng hiện tại được chọn để lọc (admin: tất cả). */
    List<PropertyOption> selectableProperties();

    List<AuditQueryPort.Entry> search(SearchCommand command);

    record PropertyOption(UUID id, String name) {}

    /**
     * @param propertyId lọc theo một nhà trọ (owner chỉ được chọn nhà trọ của mình)
     * @param from       ngày bắt đầu (gồm); to: ngày kết thúc (gồm)
     * @param page       bắt đầu từ 0
     */
    record SearchCommand(
        UUID propertyId,
        UUID actorId,
        String targetType,
        String action,
        LocalDate from,
        LocalDate to,
        int page
    ) {}
}
