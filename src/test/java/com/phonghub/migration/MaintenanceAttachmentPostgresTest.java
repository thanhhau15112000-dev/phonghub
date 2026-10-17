package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceAttachmentRepository;
import com.phonghub.domain.exception.AttachmentLimitException;
import com.phonghub.domain.model.MaintenanceAttachment;
import com.phonghub.domain.model.MaintenanceAttachment.Kind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #73 trên Postgres thật: lưu/đọc ảnh và video đính kèm phiếu bảo trì, mỗi phiếu tối đa một video. */
@Testcontainers(disabledWithoutDocker = true)
class MaintenanceAttachmentPostgresTest {

    private static final UUID TICKET = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TECHNICIAN = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Attachments round-trip in upload order and the database allows one video per ticket")
    void attachmentsAndOneVideoPerTicket() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        PostgresMaintenanceAttachmentRepository attachments =
            new PostgresMaintenanceAttachmentRepository(new NamedParameterJdbcTemplate(ds));

        MaintenanceAttachment video = attachments.save(attachment(Kind.VIDEO, "video/mp4", TECHNICIAN, 120));
        MaintenanceAttachment photo1 = attachments.save(attachment(Kind.IMAGE, "image/jpeg", TECHNICIAN, 0));
        MaintenanceAttachment photo2 = attachments.save(attachment(Kind.IMAGE, "image/png", null, 60));

        // đọc đúng metadata, kể cả người tải lên đã bị gỡ (NULL)
        assertEquals(video, attachments.findById(video.id()).orElseThrow());
        assertNull(attachments.findById(photo2.id()).orElseThrow().uploadedBy());
        assertTrue(attachments.findById(UUID.randomUUID()).isEmpty());
        assertEquals(List.of(photo1.id(), photo2.id(), video.id()),
            attachments.findByTicketId(TICKET).stream().map(MaintenanceAttachment::id).toList());
        assertTrue(attachments.findByTicketId(UUID.randomUUID()).isEmpty());

        // video thứ hai bị chỉ mục unique chặn và đổi thành lỗi nghiệp vụ
        AttachmentLimitException ex = assertThrows(AttachmentLimitException.class,
            () -> attachments.save(attachment(Kind.VIDEO, "video/webm", TECHNICIAN, 180)));
        assertEquals("Phiếu đã có video", ex.getMessage());
        assertEquals(3, attachments.findByTicketId(TICKET).size());

        // xóa video cũ thì tải video mới được
        attachments.delete(video.id());
        assertTrue(attachments.findById(video.id()).isEmpty());
        MaintenanceAttachment replacement = attachments.save(attachment(Kind.VIDEO, "video/webm", TECHNICIAN, 240));
        assertEquals(List.of(photo1.id(), photo2.id(), replacement.id()),
            attachments.findByTicketId(TICKET).stream().map(MaintenanceAttachment::id).toList());
    }

    private static MaintenanceAttachment attachment(Kind kind, String contentType, UUID uploadedBy, long secondsAfter) {
        UUID id = UUID.randomUUID();
        return new MaintenanceAttachment(id, TICKET, kind, "maintenance/" + TICKET + "/" + id, contentType,
            1024 + secondsAfter, uploadedBy, T0.plusSeconds(secondsAfter));
    }
}
