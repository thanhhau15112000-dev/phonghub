package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.TenantPrivacyRepositoryPort;
import com.phonghub.domain.model.TenantPrivacy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresTenantPrivacyRepository implements TenantPrivacyRepositoryPort {

    private static final RowMapper<TenantPrivacy> ROW_MAPPER = (rs, rowNum) -> new TenantPrivacy(
        rs.getObject("tenant_id", UUID.class),
        rs.getBoolean("legal_hold"),
        rs.getString("legal_hold_reason"),
        rs.getObject("legal_hold_set_by", UUID.class),
        instant(rs.getTimestamp("legal_hold_set_at")),
        instant(rs.getTimestamp("erasure_requested_at")),
        instant(rs.getTimestamp("purged_at")),
        instant(rs.getTimestamp("purge_notified_at")));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresTenantPrivacyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Override
    public Optional<TenantPrivacy> find(UUID tenantId) {
        List<TenantPrivacy> rows = jdbcTemplate.query(
            "SELECT * FROM tenant_privacy WHERE tenant_id = :tenantId", Map.of("tenantId", tenantId), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public TenantPrivacy save(TenantPrivacy privacy) {
        jdbcTemplate.update("""
            INSERT INTO tenant_privacy (tenant_id, legal_hold, legal_hold_reason, legal_hold_set_by,
                legal_hold_set_at, erasure_requested_at, purged_at, purge_notified_at)
            VALUES (:tenantId, :legalHold, :reason, :setBy, :setAt, :erasureAt, :purgedAt, :notifiedAt)
            ON CONFLICT (tenant_id) DO UPDATE SET
                legal_hold = EXCLUDED.legal_hold,
                legal_hold_reason = EXCLUDED.legal_hold_reason,
                legal_hold_set_by = EXCLUDED.legal_hold_set_by,
                legal_hold_set_at = EXCLUDED.legal_hold_set_at,
                erasure_requested_at = EXCLUDED.erasure_requested_at,
                purged_at = EXCLUDED.purged_at,
                purge_notified_at = EXCLUDED.purge_notified_at
            """, new MapSqlParameterSource()
            .addValue("tenantId", privacy.tenantId())
            .addValue("legalHold", privacy.legalHold())
            .addValue("reason", privacy.legalHoldReason())
            .addValue("setBy", privacy.legalHoldSetBy())
            .addValue("setAt", ts(privacy.legalHoldSetAt()))
            .addValue("erasureAt", ts(privacy.erasureRequestedAt()))
            .addValue("purgedAt", ts(privacy.purgedAt()))
            .addValue("notifiedAt", ts(privacy.purgeNotifiedAt())));
        return privacy;
    }
}
