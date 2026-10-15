package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.ChatRoomRepositoryPort;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.ChatRoomMember;
import com.phonghub.domain.model.ChatRoomType;
import com.phonghub.domain.model.UserRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresChatRoomRepository implements ChatRoomRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresChatRoomRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private final RowMapper<ChatRoomMember> memberRowMapper = (rs, rowNum) -> new ChatRoomMember(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("chat_room_id")),
        UUID.fromString(rs.getString("user_id")),
        rs.getString("display_name"),
        UserRole.valueOf(rs.getString("role")),
        toInstant(rs.getTimestamp("joined_at")),
        toInstant(rs.getTimestamp("left_at")),
        toInstant(rs.getTimestamp("last_read_at"))
    );

    private List<ChatRoomMember> loadMembers(UUID roomId) {
        String sql = """
            SELECT id, chat_room_id, user_id, display_name, role, joined_at, left_at, last_read_at
            FROM chat_room_members
            WHERE chat_room_id = :roomId
            """;
        return jdbcTemplate.query(sql, Map.of("roomId", roomId), memberRowMapper);
    }

    @Override
    public Optional<ChatRoom> findById(UUID id) {
        String sql = """
            SELECT id, type, name, created_at
            FROM chat_rooms
            WHERE id = :id
            """;
        List<ChatRoom> rooms = jdbcTemplate.query(sql, Map.of("id", id), (rs, rowNum) -> {
            UUID roomId = UUID.fromString(rs.getString("id"));
            ChatRoomType type = ChatRoomType.valueOf(rs.getString("type"));
            String name = rs.getString("name");
            Instant createdAt = toInstant(rs.getTimestamp("created_at"));
            return new ChatRoom(roomId, type, name, createdAt, loadMembers(roomId));
        });

        return rooms.stream().findFirst();
    }

    @Override
    public Optional<ChatRoom> findDirectRoomBetween(UUID user1, UUID user2) {
        String sql = """
            SELECT r.id, r.type, r.name, r.created_at
            FROM chat_rooms r
            JOIN chat_room_members m1 ON r.id = m1.chat_room_id AND m1.user_id = :u1 AND m1.left_at IS NULL
            JOIN chat_room_members m2 ON r.id = m2.chat_room_id AND m2.user_id = :u2 AND m2.left_at IS NULL
            WHERE r.type = 'DIRECT'
            LIMIT 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("u1", user1)
            .addValue("u2", user2);

        List<ChatRoom> rooms = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            UUID roomId = UUID.fromString(rs.getString("id"));
            ChatRoomType type = ChatRoomType.valueOf(rs.getString("type"));
            String name = rs.getString("name");
            Instant createdAt = toInstant(rs.getTimestamp("created_at"));
            return new ChatRoom(roomId, type, name, createdAt, loadMembers(roomId));
        });

        return rooms.stream().findFirst();
    }

    @Override
    public List<ChatRoom> findRoomsByUserId(UUID userId) {
        String sql = """
            SELECT DISTINCT r.id, r.type, r.name, r.created_at
            FROM chat_rooms r
            LEFT JOIN chat_room_members m ON r.id = m.chat_room_id
            WHERE r.type = 'GLOBAL' OR (r.type = 'DIRECT' AND m.user_id = :userId AND m.left_at IS NULL)
            ORDER BY r.created_at DESC
            """;

        return jdbcTemplate.query(sql, Map.of("userId", userId), (rs, rowNum) -> {
            UUID roomId = UUID.fromString(rs.getString("id"));
            ChatRoomType type = ChatRoomType.valueOf(rs.getString("type"));
            String name = rs.getString("name");
            Instant createdAt = toInstant(rs.getTimestamp("created_at"));
            return new ChatRoom(roomId, type, name, createdAt, loadMembers(roomId));
        });
    }

    @Override
    public ChatRoom save(ChatRoom room) {
        String roomSql = """
            INSERT INTO chat_rooms (id, type, name, created_at)
            VALUES (:id, :type, :name, :createdAt)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", room.id())
            .addValue("type", room.type().name())
            .addValue("name", room.name())
            .addValue("createdAt", Timestamp.from(room.createdAt()));

        jdbcTemplate.update(roomSql, params);

        String memberSql = """
            INSERT INTO chat_room_members (id, chat_room_id, user_id, display_name, role, joined_at, left_at, last_read_at)
            VALUES (:id, :roomId, :userId, :displayName, :role, :joinedAt, :leftAt, :lastReadAt)
            ON CONFLICT (chat_room_id, user_id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                role = EXCLUDED.role,
                left_at = EXCLUDED.left_at,
                last_read_at = COALESCE(EXCLUDED.last_read_at, chat_room_members.last_read_at)
            """;

        for (ChatRoomMember m : room.members()) {
            MapSqlParameterSource mParams = new MapSqlParameterSource()
                .addValue("id", m.id())
                .addValue("roomId", room.id())
                .addValue("userId", m.userId())
                .addValue("displayName", m.displayName())
                .addValue("role", m.role().name())
                .addValue("joinedAt", Timestamp.from(m.joinedAt()))
                .addValue("leftAt", m.leftAt() != null ? Timestamp.from(m.leftAt()) : null)
                .addValue("lastReadAt", m.lastReadAt() != null ? Timestamp.from(m.lastReadAt()) : null);

            jdbcTemplate.update(memberSql, mParams);
        }

        return room;
    }

    @Override
    public void updateLastRead(UUID roomId, UUID userId, Instant lastReadAt) {
        String sql = """
            INSERT INTO chat_room_members (id, chat_room_id, user_id, display_name, role, joined_at, last_read_at)
            VALUES (gen_random_uuid(), :roomId, :userId, '', 'TENANT', NOW(), :lastReadAt)
            ON CONFLICT (chat_room_id, user_id) DO UPDATE SET
                last_read_at = EXCLUDED.last_read_at
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roomId", roomId)
            .addValue("userId", userId)
            .addValue("lastReadAt", Timestamp.from(lastReadAt));
        jdbcTemplate.update(sql, params);
    }

    @Override
    public Instant getLastRead(UUID roomId, UUID userId) {
        String sql = """
            SELECT last_read_at FROM chat_room_members
            WHERE chat_room_id = :roomId AND user_id = :userId
            """;
        List<Timestamp> list = jdbcTemplate.query(
            sql,
            Map.of("roomId", roomId, "userId", userId),
            (rs, rowNum) -> rs.getTimestamp("last_read_at")
        );
        return list.isEmpty() || list.get(0) == null ? null : list.get(0).toInstant();
    }
}
