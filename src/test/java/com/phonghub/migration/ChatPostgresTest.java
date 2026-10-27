package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresChatMessageRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresChatRoomRepository;
import com.phonghub.domain.model.ChatAttachment;
import com.phonghub.domain.model.ChatMessage;
import com.phonghub.domain.model.ChatRoom;
import com.phonghub.domain.model.ChatRoomMember;
import com.phonghub.domain.model.ChatRoomType;
import com.phonghub.domain.model.UserRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #90 trên Postgres thật: phòng chat chung/riêng, rời phòng, mốc đã đọc, phân trang tin nhắn và đếm chưa đọc. */
@Testcontainers(disabledWithoutDocker = true)
class ChatPostgresTest {

    private static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STAFF = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID TENANT = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Chat rooms and messages round-trip: direct lookup, leaving, last read and unread counts")
    void chatRoomsAndMessages() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresChatRoomRepository rooms = new PostgresChatRoomRepository(jdbc);
        PostgresChatMessageRepository messages = new PostgresChatMessageRepository(jdbc);

        // kênh chung do V33 seed sẵn, chưa có thành viên
        ChatRoom global = rooms.findById(ChatRoom.GLOBAL_ROOM_ID).orElseThrow();
        assertEquals(ChatRoomType.GLOBAL, global.type());
        assertTrue(global.members().isEmpty());
        assertTrue(rooms.findById(UUID.randomUUID()).isEmpty());

        // phòng riêng staff - tenant
        UUID directId = UUID.randomUUID();
        ChatRoomMember staffMember = new ChatRoomMember(UUID.randomUUID(), directId, STAFF, "Nhân viên Q7",
            UserRole.STAFF, T0, null, T0);
        ChatRoomMember tenantMember = new ChatRoomMember(UUID.randomUUID(), directId, TENANT, "Nguyễn Văn A",
            UserRole.TENANT, T0, null, null);
        rooms.save(new ChatRoom(directId, ChatRoomType.DIRECT, "Staff - Tenant", T0,
            List.of(staffMember, tenantMember)));

        ChatRoom direct = rooms.findById(directId).orElseThrow();
        assertEquals("Staff - Tenant", direct.name());
        assertEquals(2, direct.members().size());
        assertEquals("Nguyễn Văn A", direct.getPeerMember(STAFF).orElseThrow().displayName());
        assertEquals(directId, rooms.findDirectRoomBetween(STAFF, TENANT).orElseThrow().id());
        assertEquals(directId, rooms.findDirectRoomBetween(TENANT, STAFF).orElseThrow().id());
        assertTrue(rooms.findDirectRoomBetween(STAFF, ADMIN).isEmpty());

        // danh sách phòng: ai cũng thấy kênh chung, chỉ thành viên thấy phòng riêng
        assertEquals(List.of(ChatRoom.GLOBAL_ROOM_ID, directId).stream().sorted().toList(),
            rooms.findRoomsByUserId(TENANT).stream().map(ChatRoom::id).sorted().toList());
        assertEquals(List.of(ChatRoom.GLOBAL_ROOM_ID), rooms.findRoomsByUserId(ADMIN).stream().map(ChatRoom::id).toList());

        // mốc đã đọc: lưu lại phòng với last_read_at null không xóa mốc cũ
        assertEquals(T0, rooms.getLastRead(directId, STAFF));
        assertNull(rooms.getLastRead(directId, TENANT));
        assertNull(rooms.getLastRead(directId, ADMIN));
        rooms.save(new ChatRoom(directId, ChatRoomType.DIRECT, "Đổi tên", T0,
            List.of(staffMember.withLastReadAt(null), tenantMember)));
        assertEquals(T0, rooms.getLastRead(directId, STAFF));
        assertEquals("Đổi tên", rooms.findById(directId).orElseThrow().name());

        // tin nhắn: trang mới nhất trả về theo thứ tự cũ -> mới, phân trang bằng mốc before
        ChatMessage m1 = messages.save(new ChatMessage(UUID.randomUUID(), directId, STAFF, "Nhân viên Q7", "Chào anh",
            T0.plusSeconds(60), null));
        ChatMessage m2 = messages.save(new ChatMessage(UUID.randomUUID(), directId, TENANT, "Nguyễn Văn A",
            "Phòng em bị dột", T0.plusSeconds(120), null));
        ChatMessage m3 = messages.save(new ChatMessage(UUID.randomUUID(), directId, TENANT, "Nguyễn Văn A", "",
            "https://cdn.example/leak.jpg", "IMAGE", T0.plusSeconds(180), null));
        assertEquals(List.of(m2.id(), m3.id()), messages.findByRoomId(directId, null, 2).stream()
            .map(ChatMessage::id).toList());
        assertEquals(List.of(m1.id(), m2.id()), messages.findByRoomId(directId, m3.createdAt(), 10).stream()
            .map(ChatMessage::id).toList());

        ChatMessage latest = messages.findLatestMessageByRoomId(directId).orElseThrow();
        assertEquals(m3.id(), latest.id());
        assertTrue(latest.isImage());
        assertEquals("https://cdn.example/leak.jpg", latest.mediaUrl());
        assertTrue(messages.findLatestMessageByRoomId(ChatRoom.GLOBAL_ROOM_ID).isEmpty());

        // tin chỉ có ảnh lưu content NULL vẫn đọc ra chuỗi rỗng
        UUID mediaOnly = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO chat_messages (id, chat_room_id, sender_id, sender_name, content, media_url, media_type, created_at)
            VALUES (:id, :room, :sender, 'Nhân viên Q7', NULL, 'https://cdn.example/v.mp4', 'VIDEO', :at)
            """, Map.of("id", mediaOnly, "room", directId, "sender", STAFF,
            "at", Timestamp.from(T0.plusSeconds(240))));
        ChatMessage video = messages.findLatestMessageByRoomId(directId).orElseThrow();
        assertEquals(mediaOnly, video.id());
        assertEquals("", video.content());
        assertTrue(video.isVideo());

        // chưa đọc: không tính tin của chính mình, chỉ tính sau mốc đã đọc
        assertEquals(2, messages.countUnreadMessages(directId, STAFF, null));
        assertEquals(2, messages.countUnreadMessages(directId, STAFF, T0));
        assertEquals(1, messages.countUnreadMessages(directId, STAFF, m2.createdAt()));
        assertEquals(2, messages.countUnreadMessages(directId, TENANT, null));
        assertEquals(1, messages.countUnreadMessages(directId, TENANT, m1.createdAt()));

        rooms.updateLastRead(directId, TENANT, m3.createdAt());
        assertEquals(m3.createdAt(), rooms.getLastRead(directId, TENANT));

        // kênh chung: người chưa có dòng thành viên vẫn ghi được mốc đã đọc
        rooms.updateLastRead(ChatRoom.GLOBAL_ROOM_ID, ADMIN, T0.plusSeconds(300));
        assertEquals(T0.plusSeconds(300), rooms.getLastRead(ChatRoom.GLOBAL_ROOM_ID, ADMIN));
        rooms.updateLastRead(ChatRoom.GLOBAL_ROOM_ID, ADMIN, T0.plusSeconds(360));
        assertEquals(T0.plusSeconds(360), rooms.getLastRead(ChatRoom.GLOBAL_ROOM_ID, ADMIN));

        // tenant rời phòng: không còn tìm thấy phòng riêng, không còn trong danh sách của tenant
        rooms.save(new ChatRoom(directId, ChatRoomType.DIRECT, "Đổi tên", T0, List.of(staffMember,
            new ChatRoomMember(tenantMember.id(), directId, TENANT, "Nguyễn Văn A", UserRole.TENANT, T0,
                T0.plusSeconds(400), null))));
        assertTrue(rooms.findDirectRoomBetween(STAFF, TENANT).isEmpty());
        assertEquals(List.of(ChatRoom.GLOBAL_ROOM_ID), rooms.findRoomsByUserId(TENANT).stream().map(ChatRoom::id).toList());
        assertEquals(T0.plusSeconds(400), rooms.findById(directId).orElseThrow().members().stream()
            .filter(m -> m.userId().equals(TENANT)).findFirst().orElseThrow().leftAt());
    }

    @Test
    @DisplayName("#251: a message and its attachments are written in one statement and read back in order")
    void chatAttachments() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresChatMessageRepository messages = new PostgresChatMessageRepository(jdbc);
        // Phòng riêng không thành viên để không đụng dữ liệu của test kia (cùng container)
        UUID room = UUID.randomUUID();
        new PostgresChatRoomRepository(jdbc).save(new ChatRoom(room, ChatRoomType.DIRECT, "Ảnh", T0, List.of()));
        Instant at = T0.plusSeconds(3600);

        ChatAttachment video = new ChatAttachment(UUID.randomUUID(), 1, "chat/" + room + "/" + UUID.randomUUID() + ".mp4",
            "video/mp4", ChatAttachment.VIDEO, 4000);
        ChatAttachment image = new ChatAttachment(UUID.randomUUID(), 0, "chat/" + room + "/" + UUID.randomUUID() + ".jpg",
            "image/jpeg", ChatAttachment.IMAGE, 1200);
        ChatMessage sent = messages.save(new ChatMessage(UUID.randomUUID(), room, STAFF, "Nhân viên Q7", "", null, null,
            at, null, List.of(video, image)));
        ChatMessage plain = messages.save(new ChatMessage(UUID.randomUUID(), room, STAFF, "Nhân viên Q7", "chữ",
            at.plusSeconds(1), null));

        ChatMessage loaded = messages.findById(sent.id()).orElseThrow();
        assertEquals("", loaded.content());
        assertEquals(List.of(image, video), loaded.attachments());
        assertTrue(messages.findById(plain.id()).orElseThrow().attachments().isEmpty());
        List<ChatMessage> page = messages.findByRoomId(room, null, 10);
        assertEquals(List.of(sent.id(), plain.id()), page.stream().map(ChatMessage::id).toList());
        assertEquals(2, page.get(0).attachments().size());
        assertTrue(messages.findById(UUID.randomUUID()).isEmpty());

        // Ghi tệp lỗi (trùng khóa) thì tin cũng không được ghi
        UUID failedId = UUID.randomUUID();
        ChatAttachment duplicate = new ChatAttachment(UUID.randomUUID(), 0, image.objectKey(), "image/jpeg",
            ChatAttachment.IMAGE, 10);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> messages.save(new ChatMessage(
            failedId, room, STAFF, "Nhân viên Q7", "x", null, null, at.plusSeconds(2), null, List.of(duplicate))));
        assertTrue(messages.findById(failedId).isEmpty());

        // Loại tệp ngoài IMAGE/VIDEO bị CHECK chặn; xóa tin thì xóa luôn dòng tệp
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
            INSERT INTO chat_message_attachments (id, message_id, position, object_key, content_type, media_type, size_bytes)
            VALUES (:id, :message, 5, 'chat/x/y.gif', 'image/gif', 'GIF', 10)
            """, Map.of("id", UUID.randomUUID(), "message", sent.id())));
        jdbc.update("DELETE FROM chat_messages WHERE id = :id", Map.of("id", sent.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_message_attachments WHERE message_id = :id",
            Map.of("id", sent.id()), Integer.class));
    }
}
