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

    /** Tenant đã đến hạn xóa (hoặc đã yêu cầu xóa) đang chờ owner/admin xác nhận; không gồm tenant đang legal hold. */
    java.util.List<PendingPurge> pendingPurges(UUID propertyId);

    /**
     * Owner/admin xác nhận xóa dữ liệu giấy tờ của tenant đã đến hạn. Hành động không hoàn tác nên cần nhập lại mật
     * khẩu của chính người xác nhận; tenant chưa đến hạn hoặc đang legal hold bị từ chối (409).
     */
    PrivacyStatus confirmPurge(UUID propertyId, UUID tenantId, String password);

    /** Dành cho job định kỳ, không gắn với người dùng: xóa dữ liệu giấy tờ đã quá hạn. Trả về số tenant đã xóa. */
    int purgeExpired(Instant now);

    enum ErasureOutcome { PURGED, PENDING_LEGAL_HOLD, PENDING_APPROVAL }

    /** AUTO: job tự xóa khi đến hạn. APPROVAL (mặc định): chỉ báo cho owner, xóa khi owner/admin xác nhận. */
    enum PurgeMode { AUTO, APPROVAL }

    record PendingPurge(UUID tenantId, String fullName, Instant endedAt, Instant dueAt, boolean erasureRequested) {}

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
