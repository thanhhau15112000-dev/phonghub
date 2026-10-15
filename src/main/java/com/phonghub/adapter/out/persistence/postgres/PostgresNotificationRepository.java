package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.adapter.out.security.PersonalDataFields;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import com.phonghub.domain.model.UserRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Thông báo trên Postgres (#86). Tiêu đề, nội dung, ghi chú xử lý được mã hóa; tên đăng nhập/họ tên người nhận đọc từ
 * bảng users (cũng đã mã hóa) thay vì lưu thêm một bản.
 */
public class PostgresNotificationRepository implements NotificationRepositoryPort {

    private static final String SELECT = """
        SELECT n.id, n.type, n.title, n.message, n.target_user_id, n.target_role, n.link, n.created_at, n.read_at,
               n.resolved, n.resolved_at, n.resolution_note, u.username, u.full_name
        FROM notifications n LEFT JOIN users u ON u.id = n.target_user_id
        """;
    /** Khớp {@link Notification#isForAdmins()}. */
    private static final String FOR_ADMINS = "(n.type = 'PASSWORD_RESET_REQUEST' OR n.target_user_id IS NULL)";
    /** Khớp {@link Notification#isAddressedTo(UUID)}. */
    private static final String INBOX = "n.target_user_id = :userId AND n.type <> 'PASSWORD_RESET_REQUEST'";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TenantDataProtectionPort protector;

    public PostgresNotificationRepository(NamedParameterJdbcTemplate jdbcTemplate, TenantDataProtectionPort protector) {
        this.jdbcTemplate = jdbcTemplate;
        this.protector = protector;
    }

    @Override
    public Notification save(Notification n) {
        jdbcTemplate.update("""
            INSERT INTO notifications (id, type, title, message, target_user_id, target_role, link, created_at, read_at,
                                       resolved, resolved_at, resolution_note)
            VALUES (:id, :type, :title, :message, :targetUserId, :targetRole, :link, :createdAt, :readAt,
                    :resolved, :resolvedAt, :resolutionNote)
            ON CONFLICT (id) DO UPDATE SET
                read_at = EXCLUDED.read_at,
                resolved = EXCLUDED.resolved,
                resolved_at = EXCLUDED.resolved_at,
                resolution_note = EXCLUDED.resolution_note
            """, new MapSqlParameterSource()
            .addValue("id", n.id())
            .addValue("type", n.type().name())
            .addValue("title", protector.encryptField(PersonalDataFields.NOTIFICATIONS_TITLE, n.title()))
            .addValue("message", protector.encryptField(PersonalDataFields.NOTIFICATIONS_MESSAGE, n.message()))
            .addValue("targetUserId", n.targetUserId())
            .addValue("targetRole", n.targetRole() == null ? null : n.targetRole().name())
            .addValue("link", n.link())
            .addValue("createdAt", Timestamp.from(n.createdAt()))
            .addValue("readAt", n.readAt() == null ? null : Timestamp.from(n.readAt()))
            .addValue("resolved", n.isResolved())
            .addValue("resolvedAt", n.resolvedAt() == null ? null : Timestamp.from(n.resolvedAt()))
            .addValue("resolutionNote",
                protector.encryptField(PersonalDataFields.NOTIFICATIONS_RESOLUTION_NOTE, n.resolutionNote())));
        return n;
    }

    @Override
    public List<Notification> findAll() {
        return jdbcTemplate.query(SELECT + " ORDER BY n.created_at DESC", this::map);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jdbcTemplate.query(SELECT + " WHERE n.id = :id", Map.of("id", id), this::map).stream().findFirst();
    }

    @Override
    public void deleteById(UUID id) {
        jdbcTemplate.update("DELETE FROM notifications WHERE id = :id", Map.of("id", id));
    }

    @Override
    public List<Notification> findForAdmins() {
        return jdbcTemplate.query(SELECT + " WHERE " + FOR_ADMINS + " ORDER BY n.created_at DESC", this::map);
    }

    @Override
    public long countUnresolvedForAdmins() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notifications n WHERE " + FOR_ADMINS + " AND NOT n.resolved", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<Notification> findByRecipient(UUID userId, boolean unreadOnly, int offset, int limit) {
        String sql = SELECT + " WHERE " + INBOX + (unreadOnly ? " AND n.read_at IS NULL" : "")
            + " ORDER BY n.created_at DESC, n.id LIMIT :limit OFFSET :offset";
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
            .addValue("userId", userId).addValue("limit", limit).addValue("offset", offset), this::map);
    }

    @Override
    public long countByRecipient(UUID userId, boolean unreadOnly) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM notifications n WHERE " + INBOX
            + (unreadOnly ? " AND n.read_at IS NULL" : ""), Map.of("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public int markAllRead(UUID userId, Instant at) {
        return jdbcTemplate.update("UPDATE notifications n SET read_at = :at WHERE " + INBOX + " AND n.read_at IS NULL",
            new MapSqlParameterSource().addValue("userId", userId).addValue("at", Timestamp.from(at)));
    }

    private Notification map(ResultSet rs, int rowNum) throws SQLException {
        String targetUserId = rs.getString("target_user_id");
        String targetRole = rs.getString("target_role");
        return new Notification(
            UUID.fromString(rs.getString("id")),
            NotificationType.valueOf(rs.getString("type")),
            protector.decryptField(PersonalDataFields.NOTIFICATIONS_TITLE, rs.getString("title")),
            protector.decryptField(PersonalDataFields.NOTIFICATIONS_MESSAGE, rs.getString("message")),
            targetUserId == null ? null : UUID.fromString(targetUserId),
            protector.decryptField(PersonalDataFields.USERS_USERNAME, rs.getString("username")),
            protector.decryptField(PersonalDataFields.USERS_FULL_NAME, rs.getString("full_name")),
            targetRole == null ? null : UserRole.valueOf(targetRole),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("resolved"),
            toInstant(rs.getTimestamp("resolved_at")),
            protector.decryptField(PersonalDataFields.NOTIFICATIONS_RESOLUTION_NOTE, rs.getString("resolution_note")),
            rs.getString("link"),
            toInstant(rs.getTimestamp("read_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
