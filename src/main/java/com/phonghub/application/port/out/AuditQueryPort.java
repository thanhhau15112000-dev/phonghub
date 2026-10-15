package com.phonghub.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Đọc nhật ký kiểm toán để hiển thị; việc giới hạn theo nhà trọ do tầng use-case quyết định. */
public interface AuditQueryPort {

    /**
     * @param propertyIds null = không giới hạn theo nhà trọ (chỉ admin); rỗng = không có nhà trọ nào được xem
     */
    record Filter(
        Collection<UUID> propertyIds,
        UUID actorId,
        String targetType,
        String action,
        Instant from,
        Instant to,
        int limit,
        int offset
    ) {}

    record Entry(
        UUID id,
        String action,
        UUID actorId,
        String actorName,
        String targetType,
        String targetId,
        UUID propertyId,
        String details,
        Instant createdAt
    ) {}

    List<Entry> search(Filter filter);
}
