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
            INSERT INTO audit_logs (id, action, actor_id, target_type, target_id, details, property_id,
                                    room_id, contract_id, subject_user_id, created_at)
            VALUES (:id, :action, :actorId, :targetType, :targetId, :details, :propertyId,
                    :roomId, :contractId, :subjectUserId, :createdAt)
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

        Refs refs = resolveRefs(event, details);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("action", event.action())
            .addValue("actorId", event.actorId())
            .addValue("targetType", event.targetType())
            .addValue("targetId", event.targetId())
            .addValue("details", protector.encryptField(PersonalDataFields.AUDIT_DETAILS, detailsJson))
            .addValue("propertyId", refs.propertyId())
            .addValue("roomId", refs.roomId())
            .addValue("contractId", refs.contractId())
            .addValue("subjectUserId", refs.subjectUserId())
            .addValue("createdAt", Timestamp.from(event.timestamp()));

        jdbcTemplate.update(sql, params);
    }

    /** Nhà trọ, phòng, hợp đồng và người dùng mà sự kiện liên quan (cột dùng để lọc theo vai trò, #250). */
    private record Refs(UUID propertyId, UUID roomId, UUID contractId, UUID subjectUserId) {}

    /** Lấy từ sự kiện, nếu không có thì suy ra từ đối tượng bị tác động hoặc chi tiết. */
    private Refs resolveRefs(AuditEvent event, Map<String, Object> details) {
        UUID target = parseUuid(event.targetId());
        String sql = target == null || event.targetType() == null ? null : switch (event.targetType()) {
            case "PROPERTY" -> "SELECT id, NULL::uuid, NULL::uuid, NULL::uuid FROM properties WHERE id = :id";
            case "ROOM" -> "SELECT property_id, id, NULL::uuid, NULL::uuid FROM rooms WHERE id = :id";
            case "CONTRACT" -> "SELECT property_id, room_id, id, NULL::uuid FROM contracts WHERE id = :id";
            case "CONTRACT_OCCUPANT" -> """
                SELECT c.property_id, c.room_id, c.id, NULL::uuid
                FROM contract_occupants o JOIN contracts c ON c.id = o.contract_id WHERE o.id = :id""";
            case "MAINTENANCE_TICKET" -> """
                SELECT r.property_id, r.id, NULL::uuid, NULL::uuid
                FROM maintenance_tickets t JOIN rooms r ON r.id = t.room_id WHERE t.id = :id""";
            case "ROOM_ASSET" -> """
                SELECT r.property_id, r.id, NULL::uuid, NULL::uuid
                FROM room_assets a JOIN rooms r ON r.id = a.room_id WHERE a.id = :id""";
            case "HANDOVER" -> """
                SELECT c.property_id, c.room_id, c.id, NULL::uuid
                FROM handovers h JOIN contracts c ON c.id = h.contract_id WHERE h.id = :id""";
            case "SERVICE_RATE" -> "SELECT property_id, NULL::uuid, NULL::uuid, NULL::uuid FROM service_rates WHERE id = :id";
            case "INVOICE" -> """
                SELECT c.property_id, i.room_id, i.contract_id, NULL::uuid
                FROM invoices i JOIN contracts c ON c.id = i.contract_id WHERE i.id = :id""";
            case "PAYMENT_CLAIM" -> """
                SELECT p.property_id, c.room_id, p.contract_id, NULL::uuid
                FROM payment_claims p JOIN contracts c ON c.id = p.contract_id WHERE p.id = :id""";
            case "USER" -> "SELECT NULL::uuid, NULL::uuid, NULL::uuid, id FROM users WHERE id = :id";
            case "TENANT", "TENANT_DOCUMENT" -> """
                SELECT (SELECT tp.property_id FROM tenant_properties tp WHERE tp.tenant_id = t.id
                        ORDER BY tp.created_at DESC LIMIT 1),
                       NULL::uuid, NULL::uuid, t.user_id
                FROM tenants t WHERE t.id = :id""";
            default -> null;
        };
        Refs found = new Refs(null, null, null, null);
        if (sql != null) {
            List<Refs> rows = jdbcTemplate.query(sql, Map.of("id", target), (rs, rowNum) -> new Refs(
                uuidOrNull(rs.getString(1)), uuidOrNull(rs.getString(2)),
                uuidOrNull(rs.getString(3)), uuidOrNull(rs.getString(4))));
            if (!rows.isEmpty()) {
                found = rows.getFirst();
            }
        }
        UUID propertyId = event.propertyId() != null ? event.propertyId() : found.propertyId();
        if (propertyId == null && details != null && details.get("propertyId") != null) {
            propertyId = parseUuid(String.valueOf(details.get("propertyId")));
        }
        return new Refs(propertyId, found.roomId(), found.contractId(), found.subjectUserId());
    }

    private static UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
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
        if (filter.scope() != null) {
            appendScope(sql, params, filter.scope());
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


    /** Loại đối tượng người thuê được xem ở nhóm "giao dịch của mình" (#250). */
    private static final List<String> TENANT_MONEY_TYPES = List.of("INVOICE", "PAYMENT_CLAIM");
    /** Loại đối tượng của phòng đang thuê mà người thuê được xem; không gồm sự kiện nội bộ như đơn giá, phân công. */
    private static final List<String> TENANT_ROOM_TYPES = List.of(
        "CONTRACT", "CONTRACT_OCCUPANT", "HANDOVER", "ROOM_ASSET", "MAINTENANCE_TICKET", "ROOM");

    /** Hợp các điều kiện phạm vi bằng OR; không có điều kiện nào thì không thấy gì. */
    private static void appendScope(StringBuilder sql, MapSqlParameterSource params, Scope scope) {
        List<String> clauses = new ArrayList<>();
        if (scope.propertyIds() != null && !scope.propertyIds().isEmpty()) {
            clauses.add("a.property_id IN (:scopeProperties)");
            params.addValue("scopeProperties", scope.propertyIds());
        }
        if (scope.selfUserId() != null) {
            clauses.add("(a.actor_id = :scopeSelf OR a.subject_user_id = :scopeSelf)");
            params.addValue("scopeSelf", scope.selfUserId());
        }
        if (scope.technicianId() != null) {
            clauses.add("""
                (a.target_type = 'MAINTENANCE_TICKET' AND a.target_id IN (
                    SELECT t.id::text FROM maintenance_tickets t WHERE t.assigned_technician_id = :scopeTech))""");
            params.addValue("scopeTech", scope.technicianId());
        }
        if (scope.tenantUserId() != null) {
            params.addValue("scopeTenant", scope.tenantUserId())
                .addValue("moneyTypes", TENANT_MONEY_TYPES)
                .addValue("roomTypes", TENANT_ROOM_TYPES);
            // Hợp đồng mình đứng tên hoặc ở cùng, mọi trạng thái.
            String myContracts = """
                SELECT c.id FROM contracts c
                WHERE c.primary_tenant_id IN (SELECT id FROM tenants WHERE user_id = :scopeTenant)
                   OR c.id IN (SELECT o.contract_id FROM contract_occupants o
                               JOIN tenants t ON t.id = o.tenant_id WHERE t.user_id = :scopeTenant)""";
            // 1. giao dịch của mình
            clauses.add("(a.target_type IN (:moneyTypes) AND a.contract_id IN (" + myContracts + "))");
            // 2. tài khoản của mình (không gồm việc người khác xem hoặc tải giấy tờ)
            clauses.add("""
                ((a.subject_user_id = :scopeTenant
                    AND a.action NOT LIKE '%VIEWED%' AND a.action NOT LIKE '%DOWNLOADED%')
                 OR (a.actor_id = :scopeTenant AND a.target_type = 'USER'))""");
            // 3. phòng của hợp đồng đang hiệu lực, từ ngày hợp đồng kích hoạt
            clauses.add("(a.target_type IN (:roomTypes) AND EXISTS (SELECT 1 FROM contracts c"
                + " WHERE c.status = 'ACTIVE' AND c.room_id = a.room_id"
                + " AND a.created_at >= COALESCE(c.activated_at, c.created_at)"
                + " AND c.id IN (" + myContracts + ")))");
        }
        sql.append(clauses.isEmpty() ? " AND FALSE" : " AND (" + String.join(" OR ", clauses) + ")");
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
