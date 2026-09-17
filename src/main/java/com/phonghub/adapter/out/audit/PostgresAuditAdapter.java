package com.phonghub.adapter.out.audit;

import com.phonghub.application.port.out.AuditPort;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public class PostgresAuditAdapter implements AuditPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresAuditAdapter(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void recordEvent(AuditEvent event) {
        String sql = """
            INSERT INTO audit_logs (id, action, actor_id, target_type, target_id, details, created_at)
            VALUES (:id, :action, :actorId, :targetType, :targetId, :details, :createdAt)
            """;

        String detailsJson = null;
        if (event.details() != null && !event.details().isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(event.details());
            } catch (Exception ignored) {
                detailsJson = event.details().toString();
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("action", event.action())
            .addValue("actorId", event.actorId())
            .addValue("targetType", event.targetType())
            .addValue("targetId", event.targetId())
            .addValue("details", detailsJson)
            .addValue("createdAt", Timestamp.from(event.timestamp()));

        jdbcTemplate.update(sql, params);
    }
}
