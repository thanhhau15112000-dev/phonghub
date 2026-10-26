package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPaymentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPropertyRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.in.SepayWebhookUseCase.SepayWebhookCommand;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.DemoFixturePort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.SepayPaymentService;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.exception.DuplicateActiveContractException;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.PaymentCode;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #79: request đồng thời và rollback trên PostgreSQL thật — thanh toán SePay (khóa dòng hóa đơn, chống trùng
 * webhook, lỗi giữa chừng không để lại giao dịch) và kích hoạt hai hợp đồng cùng phòng.
 */
@Testcontainers(disabledWithoutDocker = true)
class ConcurrencyAndRollbackPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";
    private static final String API_KEY = "concurrency-test-key";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private PGSimpleDataSource ds;
    private NamedParameterJdbcTemplate jdbc;
    private TenantDataProtectionPort protector;
    private TransactionTemplate tx;
    private Contract contract;

    @BeforeEach
    void setUp() throws Exception {
        ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();
        jdbc = new NamedParameterJdbcTemplate(ds);
        protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id FROM contracts WHERE status = 'ACTIVE' LIMIT 1");
            rs.next();
            contract = new PostgresContractRepository(jdbc).findById(UUID.fromString(rs.getString(1))).orElseThrow();
        }
    }

    private SepayPaymentService payments(InvoiceRepositoryPort invoices) {
        SepayProperties properties = new SepayProperties();
        properties.setWebhookApiKey(API_KEY);
        return new SepayPaymentService(new PostgresPaymentRepository(jdbc, protector), invoices,
            new PostgresMaintenanceTicketRepository(jdbc), new PostgresContractRepository(jdbc),
            new PostgresPropertyRepository(jdbc), new InMemoryNotificationRepository(),
            new PostgresUserRepository(jdbc, protector), mock(CurrentUserPort.class), mock(AuthorizationService.class),
            properties);
    }

    private Invoice invoice(int year, int month, String total) {
        Invoice invoice = new Invoice(UUID.randomUUID(), contract.getId(), contract.getRoomId(),
            contract.getPrimaryTenantId(), month, year, new BigDecimal(total), new BigDecimal(total), BigDecimal.ZERO,
            LocalDate.of(year, month, 5), InvoiceStatus.ISSUED, PaymentCode.generate(), InvoiceType.RENT, null, null,
            Instant.now(), Instant.now());
        return new PostgresInvoiceRepository(jdbc).save(invoice);
    }

    private static SepayWebhookCommand transfer(long sepayId, String code, String amount) {
        return new SepayWebhookCommand(sepayId, "MBBank", "2031-01-02 08:00:00", "0389999999", null, null,
            "Thanh toan " + code, "in", new BigDecimal(amount), null, "REF" + sepayId, null);
    }

    private BigDecimal paidAmount(UUID invoiceId) {
        return jdbc.queryForObject("SELECT paid_amount FROM invoices WHERE id = :id",
            new MapSqlParameterSource("id", invoiceId), BigDecimal.class);
    }

    private long transactions(long sepayId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sepay_transactions WHERE sepay_id = :id",
            new MapSqlParameterSource("id", sepayId), Long.class);
    }

    /** Chạy các tác vụ cùng lúc (cùng chờ một tín hiệu) và trả kết quả hoặc exception của từng tác vụ. */
    private static List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return task.call();
                } catch (Exception ex) {
                    return ex;
                }
            }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    @Test
    @DisplayName("hai chuyển khoản khác nhau cùng lúc vào một hóa đơn: không cộng quá số nợ, phần dư chờ xem xét")
    void concurrentTransfersDoNotOverpay() throws Exception {
        Invoice invoice = invoice(2031, 1, "3000000");
        SepayPaymentService service = payments(new PostgresInvoiceRepository(jdbc));

        List<Object> results = runConcurrently(List.of(
            () -> tx.execute(s -> service.processWebhook("Apikey " + API_KEY, transfer(790001, invoice.getPaymentCode(), "2000000"))),
            () -> tx.execute(s -> service.processWebhook("Apikey " + API_KEY, transfer(790002, invoice.getPaymentCode(), "2000000")))));

        results.forEach(r -> assertTrue(!(r instanceof Exception), String.valueOf(r)));
        assertEquals(0, new BigDecimal("3000000").compareTo(paidAmount(invoice.getId())), "chỉ cộng đến đủ số nợ");
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM invoices WHERE id = :id",
            new MapSqlParameterSource("id", invoice.getId()), String.class));
        BigDecimal applied = jdbc.queryForObject(
            "SELECT SUM(applied_amount) FROM sepay_transactions WHERE sepay_id IN (790001, 790002)",
            new MapSqlParameterSource(), BigDecimal.class);
        assertEquals(0, new BigDecimal("3000000").compareTo(applied));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sepay_transactions WHERE sepay_id IN (790001, 790002) "
            + "AND review_reason = 'OVERPAID' AND excess_amount = 1000000", new MapSqlParameterSource(), Long.class));
    }

    @Test
    @DisplayName("SePay gửi trùng một giao dịch cùng lúc: chỉ ghi một lần và chỉ cộng tiền một lần")
    void duplicateWebhooksAppliedOnce() throws Exception {
        Invoice invoice = invoice(2031, 2, "3000000");
        SepayPaymentService service = payments(new PostgresInvoiceRepository(jdbc));
        Callable<Object> send = () -> tx.execute(s -> service.processWebhook("Apikey " + API_KEY,
            transfer(790003, invoice.getPaymentCode(), "1000000")));

        List<Object> results = runConcurrently(List.of(send, send, send));

        results.forEach(r -> assertTrue(!(r instanceof Exception), String.valueOf(r)));
        assertEquals(1, transactions(790003));
        assertEquals(0, new BigDecimal("1000000").compareTo(paidAmount(invoice.getId())));
    }

    @Test
    @DisplayName("lỗi khi cập nhật hóa đơn: không còn vết giao dịch, hóa đơn giữ nguyên; SePay gửi lại thì xử lý được")
    void failureMidwayRollsBackAndRetrySucceeds() {
        Invoice invoice = invoice(2031, 3, "3000000");
        AtomicBoolean failOnSave = new AtomicBoolean(true);
        SepayPaymentService service = payments(new FailingInvoiceRepository(jdbc, failOnSave));

        assertThrows(IllegalStateException.class, () -> tx.execute(s -> service.processWebhook("Apikey " + API_KEY,
            transfer(790004, invoice.getPaymentCode(), "3000000"))));
        assertEquals(0, transactions(790004), "giao dịch đã ghi phải bị rollback");
        assertEquals(0, BigDecimal.ZERO.compareTo(paidAmount(invoice.getId())));

        failOnSave.set(false);
        tx.execute(s -> service.processWebhook("Apikey " + API_KEY, transfer(790004, invoice.getPaymentCode(), "3000000")));
        assertEquals(1, transactions(790004));
        assertEquals(0, new BigDecimal("3000000").compareTo(paidAmount(invoice.getId())));
    }

    @Test
    @DisplayName("kích hoạt hai hợp đồng của cùng một phòng cùng lúc: đúng một hợp đồng ACTIVE, phòng OCCUPIED")
    void concurrentActivationKeepsOneActiveContract() throws Exception {
        PostgresRoomRepository rooms = new PostgresRoomRepository(jdbc);
        PostgresContractRepository contracts = new PostgresContractRepository(jdbc);
        PostgresTenantRepository tenants = new PostgresTenantRepository(jdbc, protector);
        PostgresUserRepository users = new PostgresUserRepository(jdbc, protector);
        Room room = rooms.save(new Room(UUID.randomUUID(), contract.getPropertyId(), "CC-" + System.nanoTime() % 100000, 9,
            null, new BigDecimal("3000000"), 2, RoomStatus.AVAILABLE, Instant.now(), Instant.now()));
        Tenant a = activeTenant(tenants, users, "A", "079201007771", "0907770001");
        Tenant b = activeTenant(tenants, users, "B", "079201007772", "0907770002");
        Contract first = contracts.save(draft(room, a));
        Contract second = contracts.save(draft(room, b));
        PostgresHandoverRepository handovers = new PostgresHandoverRepository(jdbc);
        handovers.save(approvedCheckIn(first.getId()));
        handovers.save(approvedCheckIn(second.getId()));

        CurrentUserPort currentUser = mock(CurrentUserPort.class);
        when(currentUser.getCurrentUser()).thenReturn(new CurrentUser(UUID.randomUUID(), "admin@x", "Admin", UserRole.ADMIN));
        ContractService service = new ContractService(contracts, rooms, new PostgresPropertyRepository(jdbc), tenants,
            users, null, currentUser, mock(AuthorizationService.class), event -> { }, handovers);

        List<Object> results = runConcurrently(List.of(
            () -> tx.execute(s -> service.activateContract(first.getId())),
            () -> tx.execute(s -> service.activateContract(second.getId()))));

        assertEquals(1, results.stream().filter(Contract.class::isInstance).count(), results.toString());
        Object failure = results.stream().filter(Exception.class::isInstance).findFirst().orElseThrow();
        assertTrue(failure instanceof DuplicateActiveContractException || failure instanceof InvalidRoomStateException
            || failure instanceof DataIntegrityViolationException
            || failure instanceof OptimisticLockConflictException, failure.toString());

        List<Contract> after = contracts.findByRoomId(room.getId());
        assertEquals(1, after.stream().filter(c -> c.getStatus() == ContractStatus.ACTIVE).count());
        assertEquals(1, after.stream().filter(c -> c.getStatus() == ContractStatus.DRAFT).count(),
            "hợp đồng thua phải rollback về DRAFT");
        assertEquals(RoomStatus.OCCUPIED, rooms.findById(room.getId()).orElseThrow().getStatus());
    }

    private Contract draft(Room room, Tenant tenant) {
        return Contract.create(room.getPropertyId(), room.getId(), tenant.id(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5);
    }

    private Tenant activeTenant(PostgresTenantRepository tenants, PostgresUserRepository users,
                                String suffix, String cccd, String phone) {
        UUID userId = UUID.randomUUID();
        String username = "activation_" + suffix.toLowerCase() + "_" + userId.toString().substring(0, 8);
        String email = username + "@example.test";
        users.save(new User(userId, username, email, "Người Thuê " + suffix, phone, UserRole.TENANT,
            User.UserStatus.ACTIVE, false, Instant.now()));
        return tenants.save(Tenant.create(userId, "Người Thuê " + suffix, cccd, phone, email, null));
    }

    private static Handover approvedCheckIn(UUID contractId) {
        UUID actor = DemoFixturePort.ADMIN_ID;
        return Handover.draft(contractId, HandoverType.CHECK_IN, BigDecimal.ZERO, BigDecimal.ZERO,
            "Tình trạng đã ghi nhận", null, actor)
            .send("terms", null, null, actor)
            .tenantConfirm(actor)
            .approve(actor);
    }

    /**
     * Repository thật nhưng ném lỗi khi lưu hóa đơn để mô phỏng sự cố sau khi giao dịch đã được ghi. Kế thừa thay vì
     * bọc InvoiceRepositoryPort để không phải sửa lại mỗi khi port có thêm hàm.
     */
    private static final class FailingInvoiceRepository extends PostgresInvoiceRepository {

        private final AtomicBoolean fail;

        FailingInvoiceRepository(NamedParameterJdbcTemplate jdbc, AtomicBoolean fail) {
            super(jdbc);
            this.fail = fail;
        }

        @Override
        public Invoice save(Invoice invoice) {
            if (fail.get()) {
                throw new IllegalStateException("invoice store is down");
            }
            return super.save(invoice);
        }
    }
}
