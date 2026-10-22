package com.phonghub.migration;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverItemRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPropertyHouseRulesRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomAssetRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantDocumentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.adapter.out.storage.InMemoryFileStorageAdapter;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.HandoverService;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class CheckOutPostgresTest {

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
    @DisplayName("Approving a check-out ends the contract and frees the room atomically; any failure rolls both back")
    void approveCheckOutIsAtomic() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        UUID contractId;
        UUID roomId;
        UUID userId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            contractId = UUID.fromString(text(st, "SELECT id FROM contracts WHERE status = 'ACTIVE' LIMIT 1"));
            roomId = UUID.fromString(text(st, "SELECT room_id FROM contracts WHERE id = '" + contractId + "'"));
            userId = UUID.fromString(text(st, "SELECT id FROM users LIMIT 1"));
            st.execute("DELETE FROM handovers WHERE contract_id = '" + contractId + "'");
        }

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);

        JpaTestContext jpa = JpaTestContext.over(ds);
        var protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        PostgresHandoverRepository handovers = new PostgresHandoverRepository(jdbc);
        PostgresHandoverItemRepository items = new PostgresHandoverItemRepository(jdbc);
        ContractRepositoryPort contractRepo = jpa.contracts();
        RoomRepositoryPort roomRepo = jpa.rooms();

        // an approved check-in with one asset, then a check-out draft that reports it as damaged
        Handover checkIn = handovers.save(Handover.draft(contractId, HandoverType.CHECK_IN, BigDecimal.TEN,
            BigDecimal.ONE, "Sạch", null, userId).send("hash", null, null, userId).tenantConfirm(userId)
            .approve(userId));
        items.replaceAll(checkIn.id(), List.of(new HandoverItem(UUID.randomUUID(), checkIn.id(), null, "Tủ",
            1, AssetStatus.GOOD, null)), userId);

        CurrentUserPort currentUser = mock(CurrentUserPort.class);
        when(currentUser.getCurrentUser()).thenReturn(new CurrentUser(userId, "a@x.vn", "Admin", UserRole.ADMIN));
        AtomicBoolean failAudit = new AtomicBoolean(true);
        AuditPort audit = event -> {
            if (event.action().equals("CHECKOUT_APPROVED") && failAudit.get()) {
                throw new IllegalStateException("audit store is down");
            }
        };
        AtomicBoolean failTerminate = new AtomicBoolean(false);
        ContractUseCase contracts = mock(ContractUseCase.class);
        when(contracts.terminateContract(any(), anyBoolean())).thenAnswer(invocation -> {
            if (failTerminate.get()) {
                throw new IllegalStateException("cannot terminate");
            }
            Contract contract = contractRepo.findById(invocation.getArgument(0)).orElseThrow();
            Room room = roomRepo.findById(contract.getRoomId()).orElseThrow();
            contract.terminate();
            room.vacate(invocation.getArgument(1));
            roomRepo.save(room);
            return contractRepo.save(contract);
        });
        HandoverService service = new HandoverService(handovers, new PostgresHandoverPhotoRepository(jdbc), items,
            new PostgresPropertyHouseRulesRepository(jdbc), contractRepo, new PostgresRoomAssetRepository(jdbc),
            new PostgresTenantRepository(jdbc, protector), new PostgresTenantDocumentRepository(jdbc), contracts,
            new InMemoryFileStorageAdapter(), currentUser, mock(AuthorizationService.class), audit, 1024 * 1024);
        TransactionTemplate tx = new TransactionTemplate(jpa.transactionManager());

        tx.execute(status -> service.create(HandoverType.CHECK_OUT, new HandoverUseCase.HandoverCommand(contractId,
            new BigDecimal("20"), new BigDecimal("5"), "Trả phòng", null)));
        for (HandoverPhotoKind kind : HandoverPhotoKind.values()) {
            tx.executeWithoutResult(status -> service.addPhoto(HandoverType.CHECK_OUT, contractId, kind,
                "image/png", PNG));
        }
        Handover draft = handovers.find(contractId, HandoverType.CHECK_OUT).orElseThrow();
        HandoverItem copied = items.findByHandoverId(draft.id()).getFirst();
        assertEquals("Tủ", copied.assetName(), "the check-out starts from the check-in snapshot");
        tx.execute(status -> service.updateItem(HandoverType.CHECK_OUT, contractId, copied.id(),
            new HandoverUseCase.ItemCommand(AssetStatus.DAMAGED, "Vỡ", new BigDecimal("200000"))));
        tx.execute(status -> service.send(HandoverType.CHECK_OUT, contractId));
        handovers.save(handovers.find(contractId, HandoverType.CHECK_OUT).orElseThrow().tenantConfirm(userId));

        // 1) the audit store fails after the contract was already terminated: everything is rolled back
        assertThrows(IllegalStateException.class,
            () -> tx.execute(status -> service.approve(HandoverType.CHECK_OUT, contractId, null)));
        assertUntouched(ds, contractId, roomId, handovers);

        // 2) terminating the contract fails: the handover stays pending
        failAudit.set(false);
        failTerminate.set(true);
        assertThrows(IllegalStateException.class,
            () -> tx.execute(status -> service.approve(HandoverType.CHECK_OUT, contractId, null)));
        assertUntouched(ds, contractId, roomId, handovers);

        // 3) success: handover approved, contract terminated and the room goes to maintenance (damaged item)
        failTerminate.set(false);
        Handover approved = tx.execute(status ->
            service.approve(HandoverType.CHECK_OUT, contractId, new BigDecimal("150000")));
        assertEquals(HandoverStatus.APPROVED, approved.status());
        assertEquals(0, new BigDecimal("150000").compareTo(approved.finalDeduction()));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals("TERMINATED", text(st, "SELECT status FROM contracts WHERE id = '" + contractId + "'"));
            assertEquals("MAINTENANCE", text(st, "SELECT status FROM rooms WHERE id = '" + roomId + "'"));
            assertEquals("APPROVED", text(st, "SELECT status FROM handovers WHERE contract_id = '" + contractId
                + "' AND type = 'CHECK_OUT'"));
            assertEquals("1", text(st, "SELECT count(*) FROM handovers WHERE contract_id = '" + contractId
                + "' AND type = 'CHECK_IN'"), "check-in and check-out coexist for one contract");
        }
    }

    private void assertUntouched(PGSimpleDataSource ds, UUID contractId, UUID roomId,
                                 PostgresHandoverRepository handovers) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals("ACTIVE", text(st, "SELECT status FROM contracts WHERE id = '" + contractId + "'"));
            assertEquals("OCCUPIED", text(st, "SELECT status FROM rooms WHERE id = '" + roomId + "'"));
        }
        Handover current = handovers.find(contractId, HandoverType.CHECK_OUT).orElseThrow();
        assertEquals(HandoverStatus.PENDING_APPROVAL, current.status());
    }
}
