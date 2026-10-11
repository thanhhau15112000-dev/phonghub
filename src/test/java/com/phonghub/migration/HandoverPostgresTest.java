package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverItemRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPropertyHouseRulesRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomAssetRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantDocumentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.adapter.out.storage.InMemoryFileStorageAdapter;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.HandoverService;
import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class HandoverPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private String text(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("V18 keeps legacy rows approved, enforces one check-in per contract, and approval is atomic and exclusive")
    void v18AndTransactionalBehaviour() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).target("17").load().migrate();

        UUID contractId;
        UUID userId;
        UUID legacyId = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            contractId = UUID.fromString(text(st, "SELECT id FROM contracts WHERE status = 'ACTIVE' LIMIT 1"));
            userId = UUID.fromString(text(st, "SELECT id FROM users LIMIT 1"));
            st.execute("INSERT INTO handovers (id, contract_id, type, initial_electric_meter, initial_water_meter, "
                + "room_condition) VALUES ('" + legacyId + "', '" + contractId + "', 'CHECK_IN', 10, 5, 'cu')");
        }

        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals("APPROVED", text(st, "SELECT status FROM handovers WHERE id = '" + legacyId + "'"),
                "rows that existed before V18 were real handovers");
            assertThrows(Exception.class, () -> st.execute("INSERT INTO handovers (contract_id, type, "
                + "initial_electric_meter, initial_water_meter, room_condition) VALUES ('" + contractId
                + "', 'CHECK_IN', 1, 1, 'trùng')"), "one check-in per contract");
            st.execute("INSERT INTO handovers (contract_id, type, initial_electric_meter, initial_water_meter, "
                + "room_condition) VALUES ('" + contractId + "', 'CHECK_OUT', 1, 1, 'trả phòng')");
            assertThrows(Exception.class, () -> st.execute("UPDATE handovers SET status = 'NOPE' WHERE id = '"
                + legacyId + "'"), "unknown status must be rejected");
            st.execute("DELETE FROM handovers WHERE contract_id = '" + contractId + "'");
        }

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        var protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        PostgresHandoverRepository handovers = new PostgresHandoverRepository(jdbc);
        PostgresHandoverPhotoRepository photos = new PostgresHandoverPhotoRepository(jdbc);
        CurrentUserPort currentUser = mock(CurrentUserPort.class);
        when(currentUser.getCurrentUser()).thenReturn(
            new CurrentUser(userId, "admin@x.vn", "Admin", UserRole.ADMIN));
        AuthorizationService authorization = mock(AuthorizationService.class);
        AtomicInteger auditFailures = new AtomicInteger();
        AuditPort failingAudit = event -> {
            if (event.action().equals("HANDOVER_APPROVED") && auditFailures.get() > 0) {
                auditFailures.decrementAndGet();
                throw new IllegalStateException("audit store is down");
            }
        };
        HandoverService service = new HandoverService(handovers, photos, new PostgresHandoverItemRepository(jdbc),
            new PostgresPropertyHouseRulesRepository(jdbc), new PostgresContractRepository(jdbc),
            new PostgresRoomAssetRepository(jdbc), new PostgresTenantRepository(jdbc, protector),
            new PostgresTenantDocumentRepository(jdbc), mock(com.phonghub.application.port.in.ContractUseCase.class),
            new InMemoryFileStorageAdapter(), currentUser,
            authorization, failingAudit, 1024 * 1024);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));

        Handover draft = tx.execute(status -> service.create(HandoverType.CHECK_IN, new com.phonghub.application.port.in.HandoverUseCase
            .HandoverCommand(contractId, new BigDecimal("12.5"), new BigDecimal("3"), "Sạch", "1 chìa")));
        for (HandoverPhotoKind kind : HandoverPhotoKind.values()) {
            tx.executeWithoutResult(status -> service.addPhoto(HandoverType.CHECK_IN, contractId, kind, "image/png", PNG));
        }
        Handover sent = tx.execute(status -> service.send(HandoverType.CHECK_IN, contractId));
        assertEquals(HandoverStatus.PENDING_TENANT, sent.status());
        assertTrue(sent.contractTermsHash().matches("[0-9a-f]{64}"));
        // advance to PENDING_APPROVAL as the tenant step needs a tenant login; the domain method is what the service uses
        handovers.save(sent.tenantConfirm(userId));

        // 1) a failure while approving rolls back everything in the same transaction
        auditFailures.set(1);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> service.approve(HandoverType.CHECK_IN, contractId, null)));
        Handover afterRollback = handovers.find(contractId, HandoverType.CHECK_IN).orElseThrow();
        assertEquals(HandoverStatus.PENDING_APPROVAL, afterRollback.status());
        assertNull(afterRollback.approvedAt());
        assertNull(afterRollback.approvedBy());

        // 2) two approvals at the same time: exactly one succeeds, the other is refused with a conflict
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            start.await();
            try {
                tx.execute(status -> service.approve(HandoverType.CHECK_IN, contractId, null));
                return "ok";
            } catch (InvalidHandoverStateException ex) {
                return "conflict";
            }
        };
        Future<String> first = pool.submit(attempt);
        Future<String> second = pool.submit(attempt);
        start.countDown();
        String a = first.get(30, TimeUnit.SECONDS);
        String b = second.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, java.util.stream.Stream.of(a, b).filter("ok"::equals).count(), a + "/" + b);
        assertEquals(1, java.util.stream.Stream.of(a, b).filter("conflict"::equals).count(), a + "/" + b);
        Handover approved = handovers.find(contractId, HandoverType.CHECK_IN).orElseThrow();
        assertEquals(HandoverStatus.APPROVED, approved.status());
        assertEquals(userId, approved.approvedBy());

        // 3) deleting the handover removes its photos and item snapshot
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals("3", text(st, "SELECT count(*) FROM handover_photos WHERE handover_id = '"
                + draft.id() + "'"));
            st.execute("DELETE FROM handovers WHERE id = '" + draft.id() + "'");
            assertEquals("0", text(st, "SELECT count(*) FROM handover_photos WHERE handover_id = '"
                + draft.id() + "'"));
        }
    }
}
