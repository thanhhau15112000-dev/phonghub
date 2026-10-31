package com.phonghub.migration;

import com.phonghub.application.port.out.ContractRepositoryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceReminderRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresNotificationRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.NotificationType;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #86 trên Postgres thật: thông báo được mã hóa khi lưu, hộp thư cá nhân tách khỏi việc của admin, nhắc hóa đơn một lần. */
@Testcontainers(disabledWithoutDocker = true)
class NotificationPostgresTest {

    private static final UUID TENANT_USER = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final String SECRET = "Phạm Bí Mật";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Notifications are encrypted at rest, inbox and admin queries split correctly, invoice reminders dedupe")
    void notificationsAndInvoiceReminders() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);

        JpaTestContext jpa = JpaTestContext.over(ds);
        var protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        PostgresNotificationRepository notifications = new PostgresNotificationRepository(jdbc, protector);
        User tenant = new PostgresUserRepository(jdbc, protector).findById(TENANT_USER).orElseThrow();

        Notification personal = notifications.save(Notification.createForUser("Hóa đơn sắp đến hạn",
            SECRET + " còn nợ 3.000.000 đ", tenant, "/invoices/abc/pay"));
        Notification reset = notifications.save(Notification.createPasswordResetRequest(tenant));
        notifications.save(new Notification(UUID.randomUUID(), NotificationType.PAYMENT_RECEIVED, "Tiền về không khớp",
            "STK 9704123456789", null, null, null, UserRole.ADMIN, Instant.now(), false, null, null));

        // mã hóa khi lưu: bản rõ không nằm trong DB
        List<Map<String, Object>> raw = jdbc.queryForList("SELECT title, message FROM notifications", Map.of());
        assertEquals(3, raw.size());
        assertTrue(raw.stream().noneMatch(row -> row.toString().contains(SECRET) || row.toString().contains("9704123456789")
            || row.toString().contains("Hóa đơn sắp đến hạn")), raw.toString());

        // hộp thư cá nhân: chỉ thông báo cá nhân, giải mã đúng, tên người nhận lấy từ bảng users
        List<Notification> inbox = notifications.findByRecipient(tenant.id(), false, 0, 10);
        assertEquals(List.of(personal.id()), inbox.stream().map(Notification::id).toList());
        Notification loaded = inbox.getFirst();
        assertEquals(SECRET + " còn nợ 3.000.000 đ", loaded.message());
        assertEquals("/invoices/abc/pay", loaded.link());
        assertEquals(tenant.username(), loaded.targetUsername());
        assertEquals(1, notifications.countByRecipient(tenant.id(), true));

        // việc của admin: yêu cầu cấp lại mật khẩu và thông báo không gửi riêng ai
        assertEquals(2, notifications.findForAdmins().size());
        assertEquals(2, notifications.countUnresolvedForAdmins());
        reset.markResolved("Đã cấp lại mật khẩu tạm cho tài khoản " + tenant.username());
        notifications.save(reset);
        assertEquals(1, notifications.countUnresolvedForAdmins());
        assertEquals(reset.resolutionNote(), notifications.findById(reset.id()).orElseThrow().resolutionNote());

        // đã đọc
        assertEquals(1, notifications.markAllRead(tenant.id(), Instant.now()));
        assertEquals(0, notifications.markAllRead(tenant.id(), Instant.now()));
        assertEquals(0, notifications.countByRecipient(tenant.id(), true));
        assertTrue(notifications.findById(personal.id()).orElseThrow().isRead());
        assertNull(notifications.findById(reset.id()).orElseThrow().readAt(), "markAllRead không đụng việc của admin");

        // DB cũng chặn liên kết ra ngoài
        assertThrows(DataAccessException.class, () -> jdbc.update("""
            INSERT INTO notifications (id, type, title, message, link) VALUES (:id, 'GENERAL', 't', 'm', '//evil.example')
            """, Map.of("id", UUID.randomUUID())));

        // hóa đơn còn phải trả theo hạn thanh toán; mỗi (hóa đơn, hạn) chỉ nhắc một lần
        ContractRepositoryPort contracts = jpa.contracts();
        UUID contractId = jdbc.queryForObject("SELECT id FROM contracts WHERE status = 'ACTIVE' LIMIT 1", Map.of(),
            UUID.class);
        Contract contract = contracts.findById(contractId).orElseThrow();
        PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
        Invoice invoice = invoices.save(Invoice.issueMonthlyRent(contract, YearMonth.now().plusMonths(1), "PHPGDUE1",
            List.of()));
        List<Invoice> due = invoices.findPayableDueBetween(invoice.getDueDate(), invoice.getDueDate());
        assertTrue(due.stream().anyMatch(i -> i.getId().equals(invoice.getId())));
        assertTrue(invoices.findPayableDueBetween(invoice.getDueDate().plusDays(1), invoice.getDueDate().plusDays(9))
            .stream().noneMatch(i -> i.getId().equals(invoice.getId())));

        PostgresInvoiceReminderRepository reminders = new PostgresInvoiceReminderRepository(jdbc);
        assertTrue(reminders.markDueReminder(invoice.getId(), invoice.getDueDate()));
        assertFalse(reminders.markDueReminder(invoice.getId(), invoice.getDueDate()));
    }
}
