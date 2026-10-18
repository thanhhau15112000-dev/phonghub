package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresMeterSubmissionRepository;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterSubmission.MatchStatus;
import com.phonghub.domain.model.MeterSubmission.Status;
import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #67 trên Postgres thật: tenant gửi chỉ số kèm ảnh, mỗi đồng hồ một lượt chờ duyệt, duyệt chỉ đổi phần duyệt. */
@Testcontainers(disabledWithoutDocker = true)
class MeterSubmissionPostgresTest {

    private static final UUID ROOM = UUID.fromString("10101010-1010-1010-1010-101010101010");
    private static final UUID OTHER_ROOM = UUID.fromString("20102010-2010-2010-2010-201020102010");
    private static final UUID TENANT_USER = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID STAFF = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Submissions round-trip, one pending per meter, review updates only review fields")
    void submissionsAndReview() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresMeterSubmissionRepository submissions = new PostgresMeterSubmissionRepository(jdbc);

        MeterSubmission electric = submissions.save(pending(ROOM, MeterType.ELECTRIC, "1250.50", "1250.00", "0.930", 0));
        MeterSubmission water = submissions.save(pending(ROOM, MeterType.WATER, "88.00", null, null, 60));
        MeterSubmission other = submissions.save(pending(OTHER_ROOM, MeterType.ELECTRIC, "10.00", null, null, 30));

        // đọc lại đủ cột, kể cả OCR không đọc được (NULL)
        assertEquals(electric, submissions.findById(electric.id()).orElseThrow());
        MeterSubmission loadedWater = submissions.findById(water.id()).orElseThrow();
        assertNull(loadedWater.ocrValue());
        assertEquals(MatchStatus.UNVERIFIED, loadedWater.matchStatus());
        assertTrue(submissions.findById(UUID.randomUUID()).isEmpty());

        // mỗi phòng + loại đồng hồ chỉ một lượt PENDING
        MeterReadingConflictException conflict = assertThrows(MeterReadingConflictException.class,
            () -> submissions.save(pending(ROOM, MeterType.ELECTRIC, "1251.00", null, null, 90)));
        assertTrue(conflict.getMessage().contains("đang chờ duyệt"));

        // duyệt: chỉ cập nhật phần duyệt, số khai và ảnh giữ nguyên
        UUID readingId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO meter_readings (id, room_id, meter_type, previous_value, current_value, consumption,
                reading_date, period_year, period_month)
            VALUES (:id, :room, 'ELECTRIC', 1200, 1250.50, 50.50, DATE '2026-10-01', 2026, 10)
            """, Map.of("id", readingId, "room", ROOM));
        MeterSubmission accepted = electric.accepted(STAFF, T0.plusSeconds(300), new BigDecimal("1250.50"), readingId);
        submissions.save(new MeterSubmission(accepted.id(), accepted.roomId(), accepted.meterType(),
            new BigDecimal("9999.00"), "meter/tampered.jpg", accepted.photoContentType(), accepted.ocrValue(),
            accepted.ocrConfidence(), accepted.ocrEngine(), accepted.matchStatus(), accepted.status(),
            accepted.submittedBy(), accepted.submittedAt(), accepted.reviewedBy(), accepted.reviewedAt(),
            accepted.reviewNote(), accepted.finalValue(), accepted.readingId()));
        assertEquals(accepted, submissions.findById(electric.id()).orElseThrow());

        MeterSubmission rejected = water.rejected(STAFF, T0.plusSeconds(360), "  Ảnh mờ, chụp lại  ");
        submissions.save(rejected);
        MeterSubmission loadedRejected = submissions.findById(water.id()).orElseThrow();
        assertEquals(Status.REJECTED, loadedRejected.status());
        assertEquals("Ảnh mờ, chụp lại", loadedRejected.reviewNote());
        assertNull(loadedRejected.finalValue());

        // đã xử lý xong thì gửi lượt mới cho cùng đồng hồ được
        MeterSubmission resubmitted = submissions.save(pending(ROOM, MeterType.ELECTRIC, "1300.00", null, null, 600));

        // danh sách theo phòng: mới nhất trước
        assertEquals(List.of(resubmitted.id(), water.id(), electric.id()),
            submissions.findByRoomId(ROOM).stream().map(MeterSubmission::id).toList());
        assertEquals(List.of(resubmitted.id(), water.id(), other.id(), electric.id()),
            submissions.findByRoomIds(Set.of(ROOM, OTHER_ROOM)).stream().map(MeterSubmission::id).toList());
        assertTrue(submissions.findByRoomIds(Set.of()).isEmpty());
    }

    private static MeterSubmission pending(UUID roomId, MeterType type, String declared, String ocr, String confidence,
                                           long secondsAfter) {
        BigDecimal ocrValue = ocr == null ? null : new BigDecimal(ocr);
        BigDecimal ocrConfidence = confidence == null ? null : new BigDecimal(confidence);
        MatchStatus match = MeterSubmission.match(new BigDecimal(declared), ocrValue, ocrConfidence,
            new BigDecimal("0.800"), new BigDecimal("1.00"));
        return new MeterSubmission(UUID.randomUUID(), roomId, type, new BigDecimal(declared),
            "meter/" + UUID.randomUUID() + ".jpg", "image/jpeg", ocrValue, ocrConfidence,
            ocr == null ? null : "tesseract", match, Status.PENDING, TENANT_USER, T0.plusSeconds(secondsAfter),
            null, null, null, null, null);
    }
}
