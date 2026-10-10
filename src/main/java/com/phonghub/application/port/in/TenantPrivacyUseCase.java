package com.phonghub.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Khóa hồ sơ, giữ pháp lý, yêu cầu xóa sớm và xóa dữ liệu giấy tờ theo thời hạn lưu trữ. */
public interface TenantPrivacyUseCase {

    /** Owner/staff/admin của nhà trọ xem trạng thái khóa và hạn xóa của tenant. */
    PrivacyStatus status(UUID propertyId, UUID tenantId);

    /** Chỉ owner/admin: chặn xóa tenant này khi có tranh chấp hoặc nghĩa vụ lưu trữ. */
    PrivacyStatus setLegalHold(UUID propertyId, UUID tenantId, String reason);

    PrivacyStatus clearLegalHold(UUID propertyId, UUID tenantId);

    /** Tenant tự yêu cầu xóa sớm giấy tờ của mình; chỉ khi không còn hợp đồng hiệu lực. */
    ErasureOutcome requestErasure();

    /** Dành cho job định kỳ, không gắn với người dùng: xóa dữ liệu giấy tờ đã quá hạn. Trả về số tenant đã xóa. */
    int purgeExpired(Instant now);

    enum ErasureOutcome { PURGED, PENDING_LEGAL_HOLD }

    record PrivacyStatus(
        boolean locked,
        Instant lockedSince,
        Instant purgeDueAt,
        boolean legalHold,
        String legalHoldReason,
        boolean purged,
        Instant erasureRequestedAt
    ) {}
}
