package com.phonghub.adapter.out.audit;

import com.phonghub.adapter.out.security.PersonalDataFields;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.application.service.AuditSanitizer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public class PostgresAuditAdapter implements AuditPort, AuditQueryPort {

    private static final String UNREADABLE_DETAILS = "[không đọc được chi tiết]";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TenantDataProtectionPort protector;

    public PostgresAuditAdapter(
        NamedParameterJdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        TenantDataProtectionPort protector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.protector = protector;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void recordEvent(AuditEvent event) {
        String sql = """
            INSERT INTO audit_logs (id, action, actor_id, target_type, target_id, details, property_id, created_at)
            VALUES (:id, :action, :actorId, :targetType, :targetId, :details, :propertyId, :createdAt)
            """;

        String detailsJson = null;
        Map<String, Object> details = AuditSanitizer.sanitize(event.details());
        if (details != null && !details.isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (Exception ignored) {
                detailsJson = details.toString();
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("action", event.action())
            .addValue("actorId", event.actorId())
            .addValue("targetType", event.targetType())
            .addValue("targetId", event.targetId())
            .addValue("details", protector.encryptField(PersonalDataFields.AUDIT_DETAILS, detailsJson))
            .addValue("propertyId", resolvePropertyId(event, details))
            .addValue("createdAt", Timestamp.from(event.timestamp()));

        jdbcTemplate.update(sql, params);
    }

    /** Nhà trọ của sự kiện: lấy từ sự kiện, nếu không có thì suy ra từ đối tượng bị tác động hoặc chi tiết. */
    private UUID resolvePropertyId(AuditEvent event, Map<String, Object> details) {
        if (event.propertyId() != null) {
            return event.propertyId();
        }
        UUID target = parseUuid(event.targetId());
        String sql = target == null || event.targetType() == null ? null : switch (event.targetType()) {
            case "PROPERTY" -> "SELECT id FROM properties WHERE id = :id";
            case "ROOM" -> "SELECT property_id FROM rooms WHERE id = :id";
            case "CONTRACT" -> "SELECT property_id FROM contracts WHERE id = :id";
            case "CONTRACT_OCCUPANT" -> """
                SELECT c.property_id FROM contract_occupants o JOIN contracts c ON c.id = o.contract_id WHERE o.id = :id""";
            case "MAINTENANCE_TICKET" -> """
                SELECT r.property_id FROM maintenance_tickets t JOIN rooms r ON r.id = t.room_id WHERE t.id = :id""";
            case "ROOM_ASSET" -> """
                SELECT r.property_id FROM room_assets a JOIN rooms r ON r.id = a.room_id WHERE a.id = :id""";
            case "HANDOVER" -> """
                SELECT c.property_id FROM handovers h JOIN contracts c ON c.id = h.contract_id WHERE h.id = :id""";
            case "SERVICE_RATE" -> "SELECT property_id FROM service_rates WHERE id = :id";
            case "TENANT", "TENANT_DOCUMENT" -> """
                SELECT property_id FROM tenant_properties WHERE tenant_id = :id ORDER BY created_at DESC LIMIT 1""";
            default -> null;
        };
        if (sql != null) {
            List<UUID> found = jdbcTemplate.query(sql, Map.of("id", target),
                (rs, rowNum) -> UUID.fromString(rs.getString(1)));
            if (!found.isEmpty()) {
                return found.getFirst();
            }
        }
        if (details != null && details.get("propertyId") != null) {
            return parseUuid(String.valueOf(details.get("propertyId")));
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public List<Entry> search(Filter filter) {
        if (filter.propertyIds() != null && filter.propertyIds().isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
            SELECT a.id, a.action, a.actor_id, u.full_name AS actor_name, a.target_type, a.target_id,
                   a.property_id, a.details, a.created_at
            FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter.propertyIds() != null) {
            sql.append(" AND a.property_id IN (:propertyIds)");
            params.addValue("propertyIds", filter.propertyIds());
        }
        if (filter.actorId() != null) {
            sql.append(" AND a.actor_id = :actorId");
            params.addValue("actorId", filter.actorId());
        }
        if (filter.targetType() != null && !filter.targetType().isBlank()) {
            sql.append(" AND a.target_type = :targetType");
            params.addValue("targetType", filter.targetType());
        }
        if (filter.action() != null && !filter.action().isBlank()) {
            sql.append(" AND a.action = :action");
            params.addValue("action", filter.action());
        }
        if (filter.from() != null) {
            sql.append(" AND a.created_at >= :from");
            params.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND a.created_at < :to");
            params.addValue("to", Timestamp.from(filter.to()));
        }
        sql.append(" ORDER BY a.created_at DESC, a.id LIMIT :limit OFFSET :offset");
        params.addValue("limit", filter.limit()).addValue("offset", filter.offset());

        List<Entry> entries = new ArrayList<>();
        jdbcTemplate.query(sql.toString(), params, rs -> {
            String propertyId = rs.getString("property_id");
            String actorId = rs.getString("actor_id");
            entries.add(new Entry(
                UUID.fromString(rs.getString("id")),
                rs.getString("action"),
                actorId != null ? UUID.fromString(actorId) : null,
                readActorName(rs.getString("actor_name")),
                rs.getString("target_type"),
                rs.getString("target_id"),
                propertyId != null ? UUID.fromString(propertyId) : null,
                readDetails(rs.getString("details")),
                rs.getTimestamp("created_at").toInstant()
            ));
        });
        return entries;
    }

    /** {@code users.full_name} được mã hóa (V17); đọc không được thì để null, trang hiện mã người dùng thay tên. */
    private String readActorName(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return protector.decryptField(PersonalDataFields.USERS_FULL_NAME, stored);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String readDetails(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return protector.decryptField(PersonalDataFields.AUDIT_DETAILS, stored);
        } catch (RuntimeException ex) {
            return UNREADABLE_DETAILS;
        }
    }
}
