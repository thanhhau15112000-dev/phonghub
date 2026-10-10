package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Trạng thái quyền riêng tư của một tenant: giữ pháp lý, yêu cầu xóa sớm, đã xóa dữ liệu giấy tờ. */
public record TenantPrivacy(
    UUID tenantId,
    boolean legalHold,
    String legalHoldReason,
    UUID legalHoldSetBy,
    Instant legalHoldSetAt,
    Instant erasureRequestedAt,
    Instant purgedAt
) {
    public TenantPrivacy {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        if (legalHold && (legalHoldReason == null || legalHoldReason.isBlank())) {
            throw new IllegalArgumentException("Legal hold needs a reason");
        }
    }

    public static TenantPrivacy none(UUID tenantId) {
        return new TenantPrivacy(tenantId, false, null, null, null, null, null);
    }

    public boolean purged() {
        return purgedAt != null;
    }

    public TenantPrivacy withLegalHold(String reason, UUID actorId, Instant now) {
        return new TenantPrivacy(tenantId, true, reason.trim(), actorId, now, erasureRequestedAt, purgedAt);
    }

    public TenantPrivacy withoutLegalHold() {
        return new TenantPrivacy(tenantId, false, null, null, null, erasureRequestedAt, purgedAt);
    }

    public TenantPrivacy withErasureRequested(Instant now) {
        return new TenantPrivacy(tenantId, legalHold, legalHoldReason, legalHoldSetBy, legalHoldSetAt,
            erasureRequestedAt != null ? erasureRequestedAt : now, purgedAt);
    }

    public TenantPrivacy withPurged(Instant now) {
        return new TenantPrivacy(tenantId, legalHold, legalHoldReason, legalHoldSetBy, legalHoldSetAt,
            erasureRequestedAt, now);
    }
}
