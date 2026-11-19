package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.ChatMessageRepositoryPort;
import com.phonghub.domain.model.ChatAttachment;
import com.phonghub.domain.model.ChatMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

    /** Dòng chat_messages chưa có tệp đính kèm: tin chỉ có ảnh/video chỉ hợp lệ sau khi gắn tệp (#251). */
    private record Row(UUID id, UUID chatRoomId, UUID senderId, String senderName, String content, String mediaUrl,
                       String mediaType, Instant createdAt, Instant deletedAt) {
        ChatMessage toMessage(List<ChatAttachment> attachments) {
            return new ChatMessage(id, chatRoomId, senderId, senderName, content, mediaUrl, mediaType, createdAt,
                deletedAt, attachments);
        }
    }

    private final RowMapper<Row> rowMapper = (rs, rowNum) -> new Row(
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

    private static final String MESSAGE_INSERT = """
        INSERT INTO chat_messages (id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at, deleted_at)
        VALUES (:id, :roomId, :senderId, :senderName, :content, :mediaUrl, :mediaType, :createdAt, :deletedAt)
        """;

    @Override
    public ChatMessage save(ChatMessage message) {
        String sql = MESSAGE_INSERT;
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

        if (message.hasAttachments()) {
            // Một câu lệnh duy nhất (CTE) để tin và tệp đính kèm cùng được ghi hoặc cùng không được ghi
            List<String> rows = new ArrayList<>();
            for (int i = 0; i < message.attachments().size(); i++) {
                ChatAttachment attachment = message.attachments().get(i);
                String p = "a" + i;
                rows.add("(:" + p + "Id, :id, :" + p + "Position, :" + p + "Key, :" + p + "ContentType, :" + p
                    + "MediaType, :" + p + "Size, :createdAt)");
                params.addValue(p + "Id", attachment.id())
                    .addValue(p + "Position", attachment.position())
                    .addValue(p + "Key", attachment.objectKey())
                    .addValue(p + "ContentType", attachment.contentType())
                    .addValue(p + "MediaType", attachment.mediaType())
                    .addValue(p + "Size", attachment.sizeBytes());
            }
            sql = "WITH inserted_message AS (" + MESSAGE_INSERT.strip() + " RETURNING id) "
                + "INSERT INTO chat_message_attachments (id, message_id, position, object_key, content_type, media_type, "
                + "size_bytes, created_at) VALUES " + String.join(", ", rows);
        }
        jdbcTemplate.update(sql, params);
        return message;
    }

    @Override
    public Optional<ChatMessage> findById(UUID id) {
        String sql = """
            SELECT id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at, deleted_at
            FROM chat_messages
            WHERE id = :id
            """;
        return withAttachments(jdbcTemplate.query(sql, Map.of("id", id), rowMapper)).stream().findFirst();
    }

    /** Nạp tệp đính kèm cho cả trang tin bằng một truy vấn rồi mới dựng tin. */
    private List<ChatMessage> withAttachments(List<Row> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<UUID, List<ChatAttachment>> byMessage = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, message_id, position, object_key, content_type, media_type, size_bytes
                FROM chat_message_attachments
                WHERE message_id IN (:ids)
                ORDER BY message_id, position
                """,
            Map.of("ids", rows.stream().map(Row::id).collect(Collectors.toList())),
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                byMessage.computeIfAbsent(UUID.fromString(rs.getString("message_id")), ignored -> new ArrayList<>())
                    .add(new ChatAttachment(UUID.fromString(rs.getString("id")), rs.getInt("position"),
                        rs.getString("object_key"), rs.getString("content_type"), rs.getString("media_type"),
                        rs.getLong("size_bytes"))));
        return rows.stream()
            .map(row -> row.toMessage(byMessage.getOrDefault(row.id(), List.of())))
            .collect(Collectors.toList());
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

        List<ChatMessage> list = withAttachments(jdbcTemplate.query(sql, params, rowMapper));
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
        List<ChatMessage> list = withAttachments(jdbcTemplate.query(sql, Map.of("roomId", roomId), rowMapper));
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
