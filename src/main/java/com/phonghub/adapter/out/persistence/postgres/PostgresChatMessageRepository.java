package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.ChatMessageRepositoryPort;
import com.phonghub.domain.model.ChatMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresChatMessageRepository implements ChatMessageRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresChatMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private final RowMapper<ChatMessage> rowMapper = (rs, rowNum) -> new ChatMessage(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("chat_room_id")),
        UUID.fromString(rs.getString("sender_id")),
        rs.getString("sender_name"),
        rs.getString("content") != null ? rs.getString("content") : "",
        rs.getString("media_url"),
        rs.getString("media_type"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("deleted_at"))
    );

    @Override
    public ChatMessage save(ChatMessage message) {
        String sql = """
            INSERT INTO chat_messages (id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at, deleted_at)
            VALUES (:id, :roomId, :senderId, :senderName, :content, :mediaUrl, :mediaType, :createdAt, :deletedAt)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", message.id())
            .addValue("roomId", message.chatRoomId())
            .addValue("senderId", message.senderId())
            .addValue("senderName", message.senderName())
            .addValue("content", message.content())
            .addValue("mediaUrl", message.mediaUrl())
            .addValue("mediaType", message.mediaType())
            .addValue("createdAt", Timestamp.from(message.createdAt()))
            .addValue("deletedAt", message.deletedAt() != null ? Timestamp.from(message.deletedAt()) : null);

        jdbcTemplate.update(sql, params);
        return message;
    }

    @Override
    public List<ChatMessage> findByRoomId(UUID roomId, Instant before, int limit) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roomId", roomId)
            .addValue("limit", limit);

        if (before != null) {
            sql = """
                SELECT id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at, deleted_at
                FROM chat_messages
                WHERE chat_room_id = :roomId AND created_at < :before
                ORDER BY created_at DESC
                LIMIT :limit
                """;
            params.addValue("before", Timestamp.from(before));
        } else {
            sql = """
                SELECT id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at, deleted_at
                FROM chat_messages
                WHERE chat_room_id = :roomId
                ORDER BY created_at DESC
                LIMIT :limit
                """;
        }

        List<ChatMessage> list = jdbcTemplate.query(sql, params, rowMapper);
        // Sắp xếp lại theo thời gian tăng dần để UI render từ trên xuống dưới
        Collections.reverse(list);
        return list;
    }

    @Override
    public Optional<ChatMessage> findLatestMessageByRoomId(UUID roomId) {
        String sql = """
            SELECT id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at, deleted_at
            FROM chat_messages
            WHERE chat_room_id = :roomId
            ORDER BY created_at DESC
            LIMIT 1
            """;
        List<ChatMessage> list = jdbcTemplate.query(sql, Map.of("roomId", roomId), rowMapper);
        return list.stream().findFirst();
    }

    @Override
    public int countUnreadMessages(UUID roomId, UUID userId, Instant since) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("roomId", roomId)
            .addValue("userId", userId);

        if (since != null) {
            sql = """
                SELECT COUNT(*) FROM chat_messages
                WHERE chat_room_id = :roomId AND sender_id <> :userId AND created_at > :since
                """;
            params.addValue("since", Timestamp.from(since));
        } else {
            sql = """
                SELECT COUNT(*) FROM chat_messages
                WHERE chat_room_id = :roomId AND sender_id <> :userId
                """;
        }

        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }
}
