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
        int offset,
        Scope scope
    ) {
        /** Không giới hạn theo vai trò (dùng cho admin và cho kiểm thử). */
        public Filter(
            Collection<UUID> propertyIds, UUID actorId, String targetType, String action,
            Instant from, Instant to, int limit, int offset
        ) {
            this(propertyIds, actorId, targetType, action, from, to, limit, offset, null);
        }
    }

    /**
     * Phạm vi sự kiện một vai trò được xem (#250); các điều kiện được hợp bằng OR, null/rỗng = không áp dụng.
     *
     * @param propertyIds  mọi sự kiện của các nhà trọ này (chủ trọ, nhân viên)
     * @param selfUserId   sự kiện do người này thực hiện hoặc nói về tài khoản của họ
     * @param technicianId sự kiện của các phiếu bảo trì đang được giao cho kỹ thuật viên này, kể cả bước trước khi được giao
     * @param tenantUserId ba nhóm của người thuê: giao dịch của mình, tài khoản của mình, phòng của hợp đồng đang hiệu lực
     */
    record Scope(Collection<UUID> propertyIds, UUID selfUserId, UUID technicianId, UUID tenantUserId) {}

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
