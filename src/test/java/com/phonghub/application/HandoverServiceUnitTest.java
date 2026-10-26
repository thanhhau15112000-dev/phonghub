package com.phonghub.application;

import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.in.HandoverUseCase.HandoverCommand;
import com.phonghub.application.port.in.HandoverUseCase.ItemCommand;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.HandoverService;
import com.phonghub.domain.exception.FileStorageUnavailableException;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.domain.exception.HandoverAlreadyExistsException;
import com.phonghub.domain.exception.InvalidHandoverStateException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test nghiệp vụ của HandoverService bằng mock port; không cần Spring hay DB. */
class HandoverServiceUnitTest {

    private final UUID propertyId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final CurrentUser staff = new CurrentUser(UUID.randomUUID(), "s@x.vn", "Staff", UserRole.STAFF);
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "o@x.vn", "Owner", UserRole.OWNER);

    private HandoverRepositoryPort handovers;
    private HandoverPhotoRepositoryPort photos;
    private HandoverItemRepositoryPort items;
    private PropertyHouseRulesRepositoryPort rules;
    private ContractRepositoryPort contracts;
    private RoomAssetRepositoryPort assets;
    private RoomAssetUseCase assetUseCase;
    private RoomConditionRepositoryPort roomConditions;
    private TenantRepositoryPort tenants;
    private TenantDocumentRepositoryPort documents;
    private ContractUseCase contractUseCase;
    private CurrentUserPort currentUser;
    private AuthorizationService authorization;
    private FileStoragePort storage;
    private InvoiceRepositoryPort invoices;
    private InvoicePaymentRecordRepositoryPort paymentRecords;
    private HandoverService service;
    private Contract contract;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        handovers = mock(HandoverRepositoryPort.class);
        photos = mock(HandoverPhotoRepositoryPort.class);
        items = mock(HandoverItemRepositoryPort.class);
        rules = mock(PropertyHouseRulesRepositoryPort.class);
        contracts = mock(ContractRepositoryPort.class);
        assets = mock(RoomAssetRepositoryPort.class);
        assetUseCase = mock(RoomAssetUseCase.class);
        roomConditions = mock(RoomConditionRepositoryPort.class);
        tenants = mock(TenantRepositoryPort.class);
        documents = mock(TenantDocumentRepositoryPort.class);
        contractUseCase = mock(ContractUseCase.class);
        currentUser = mock(CurrentUserPort.class);
        authorization = mock(AuthorizationService.class);
        storage = mock(FileStoragePort.class);
        invoices = mock(InvoiceRepositoryPort.class);
        paymentRecords = mock(InvoicePaymentRecordRepositoryPort.class);

        tenant = new Tenant(UUID.randomUUID(), UUID.randomUUID(), "Nguyen A", "079201001111", "0900000001",
            null, null, Instant.now());
        contract = Contract.create(propertyId, roomId, tenant.id(), new BigDecimal("3000000"),
            new BigDecimal("2500000"), LocalDate.now().minusDays(10), LocalDate.now().plusMonths(6), 5);
        contract.activate();

        when(currentUser.getCurrentUser()).thenReturn(staff);
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(handovers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(photos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(items.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(assets.findActiveByRoomId(any())).thenReturn(List.of());
        when(contracts.findByRoomId(any())).thenReturn(List.of());
        when(invoices.findPayableByContractIdForUpdate(any())).thenReturn(List.of());
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRecords.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new HandoverService(handovers, photos, items, rules, contracts, assets, tenants, documents,
            contractUseCase, storage, currentUser, authorization, mock(AuditPort.class),
            1024 * 1024, null, roomConditions, assetUseCase, invoices, paymentRecords, null, null);
    }

    private HandoverCommand command() {
        return new HandoverCommand(contract.getId(), new BigDecimal("100"), new BigDecimal("50"), "Phòng sạch", "2 chìa");
    }

    private Handover existing(HandoverType type, HandoverStatus status) {
        Handover base = Handover.draft(contract.getId(), type, BigDecimal.ONE, BigDecimal.ONE, "ok", null, staff.id());
        return new Handover(base.id(), base.contractId(), type, status, base.initialElectricMeter(),
            base.initialWaterMeter(), base.roomCondition(), null, null, null, null, null, null,
            status == HandoverStatus.PENDING_APPROVAL, null, null, null, null, base.createdAt(), staff.id(),
            base.updatedAt(), staff.id());
    }

    private void givenHandover(Handover handover) {
        when(handovers.find(contract.getId(), handover.type())).thenReturn(Optional.of(handover));
        when(handovers.findForUpdate(contract.getId(), handover.type())).thenReturn(Optional.of(handover));
    }

    private HandoverItem item(UUID handoverId, AssetStatus status, String deduction) {
        return new HandoverItem(UUID.randomUUID(), handoverId, UUID.randomUUID(), "Tủ lạnh", 1, status, null,
            new BigDecimal(deduction));
    }

    // ---- create ----

    @Test
    void createRejectsContractThatIsNotActive() {
        contract.terminate();

        assertThrows(InvalidHandoverStateException.class, () -> service.create(HandoverType.CHECK_IN, command()));
        verify(handovers, never()).save(any());
    }

    @Test
    void prepareCheckInUsesLatestRoomConditionAndReturnsExistingDraftOnRepeat() {
        contract = Contract.create(propertyId, roomId, tenant.id(), new BigDecimal("3000000"),
            new BigDecimal("2500000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5);
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        RoomConditionSnapshot baseline = new RoomConditionSnapshot(UUID.randomUUID(), roomId,
            new BigDecimal("123.5"), new BigDecimal("45.25"), "Tường vừa sơn", null, null, Instant.now(), staff.id());
        RoomConditionItem baselineAsset = new RoomConditionItem(UUID.randomUUID(), baseline.id(), UUID.randomUUID(),
            0, "Máy lạnh", 1, AssetStatus.GOOD, "Mới vệ sinh", new BigDecimal("5000000"));
        when(roomConditions.findLatestByRoomId(roomId)).thenReturn(Optional.of(baseline));
        when(roomConditions.findItemsBySnapshotId(baseline.id())).thenReturn(List.of(baselineAsset));
        java.util.concurrent.atomic.AtomicReference<Handover> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(handovers.find(contract.getId(), HandoverType.CHECK_IN)).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(handovers.save(any())).thenAnswer(inv -> {
            Handover handover = inv.getArgument(0);
            saved.set(handover);
            return handover;
        });

        Handover first = service.prepareCheckIn(contract.getId());
        Handover second = service.prepareCheckIn(contract.getId());

        assertEquals(new BigDecimal("123.5"), first.initialElectricMeter());
        assertEquals(new BigDecimal("45.25"), first.initialWaterMeter());
        assertEquals("Tường vừa sơn", first.roomCondition());
        assertEquals(first.id(), second.id());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HandoverItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(items, times(1)).replaceAll(eq(first.id()), captor.capture(), eq(staff.id()));
        assertEquals("Máy lạnh", captor.getValue().getFirst().assetName());
    }

    @Test
    void addingCheckInAssetCreatesRoomAssetAndHandoverSnapshot() {
        contract = Contract.create(propertyId, roomId, tenant.id(), new BigDecimal("3000000"),
            new BigDecimal("2500000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5);
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        Handover draft = existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT);
        givenHandover(draft);
        RoomAsset asset = new RoomAsset(UUID.randomUUID(), roomId, "A-1", "Tủ lạnh", null, 1, AssetStatus.GOOD,
            "Hoạt động", null, new BigDecimal("2000000"), null, Instant.now(), staff.id(), Instant.now(), staff.id());
        when(assetUseCase.create(any())).thenReturn(asset);

        HandoverItem saved = service.addCheckInAsset(contract.getId(),
            new com.phonghub.application.port.in.HandoverUseCase.NewAssetCommand("Tủ lạnh", 1, AssetStatus.GOOD,
                "Hoạt động", new BigDecimal("2000000")));

        assertEquals(asset.id(), saved.assetId());
        assertEquals("Tủ lạnh", saved.assetName());
        verify(assetUseCase).create(any());
        verify(items).save(saved, staff.id());
    }

    @Test
    void createCheckOutRequiresApprovedCheckIn() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_APPROVAL));

        assertThrows(InvalidHandoverStateException.class, () -> service.create(HandoverType.CHECK_OUT, command()));
    }

    @Test
    void createRejectsDuplicateHandoverOfSameType() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.APPROVED));

        assertThrows(HandoverAlreadyExistsException.class, () -> service.create(HandoverType.CHECK_IN, command()));
    }

    @Test
    void createCheckOutCopiesCheckInItemsWithZeroDeduction() {
        Handover checkIn = existing(HandoverType.CHECK_IN, HandoverStatus.APPROVED);
        givenHandover(checkIn);
        when(items.findByHandoverId(checkIn.id())).thenReturn(List.of(item(checkIn.id(), AssetStatus.GOOD, "0")));

        Handover created = service.create(HandoverType.CHECK_OUT, command());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HandoverItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(items).replaceAll(eq(created.id()), captor.capture(), eq(staff.id()));
        assertEquals(1, captor.getValue().size());
        assertEquals(BigDecimal.ZERO, captor.getValue().get(0).deductionAmount());
    }

    @Test
    void sendingFirstCheckInCopiesAssetsFromLatestRoomConditionBaseline() {
        Handover checkIn = existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT);
        givenHandover(checkIn);
        when(photos.findByHandoverId(checkIn.id())).thenReturn(java.util.Arrays.stream(HandoverPhotoKind.values())
            .map(kind -> new HandoverPhoto(UUID.randomUUID(), checkIn.id(), kind, "photo-" + kind, "image/png",
                Instant.now(), staff.id())).toList());
        UUID snapshotId = UUID.randomUUID();
        when(roomConditions.findLatestByRoomId(roomId)).thenReturn(Optional.of(new RoomConditionSnapshot(
            snapshotId, roomId, new BigDecimal("100"), new BigDecimal("20"), "Phòng mới", null, null,
            Instant.now(), owner.id())));
        UUID baselineAssetId = UUID.randomUUID();
        when(roomConditions.findItemsBySnapshotId(snapshotId)).thenReturn(List.of(new RoomConditionItem(
            UUID.randomUUID(), snapshotId, baselineAssetId, 0, "Giường ban đầu", 2, AssetStatus.GOOD,
            "Còn mới", new BigDecimal("2500000"))));

        service.send(HandoverType.CHECK_IN, contract.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HandoverItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(items).replaceAll(eq(checkIn.id()), captor.capture(), eq(staff.id()));
        assertEquals(1, captor.getValue().size());
        assertEquals("Giường ban đầu", captor.getValue().getFirst().assetName());
        assertEquals(2, captor.getValue().getFirst().quantity());
        verify(assets, never()).findActiveByRoomId(roomId);
    }

    // ---- photos / items ----

    @Test
    void addPhotoRejectsNonImageContent() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT));

        assertThrows(IllegalArgumentException.class, () -> service.addPhoto(HandoverType.CHECK_IN,
            contract.getId(), HandoverPhotoKind.ROOM, "application/pdf", new byte[]{1}));
    }

    @Test
    void addPhotoRejectsWhenHandoverIsNotDraft() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_TENANT));

        assertThrows(InvalidHandoverStateException.class, () -> service.addPhoto(HandoverType.CHECK_IN,
            contract.getId(), HandoverPhotoKind.ROOM, "image/png", new byte[]{1}));
    }

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    void addPhotoReportsStorageFailureWithoutCreatingARecord() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT));
        doThrow(new IllegalStateException("404 Bucket not found")).when(storage).put(anyString(), anyString(), any());

        FileStorageUnavailableException ex = assertThrows(FileStorageUnavailableException.class, () ->
            service.addPhoto(HandoverType.CHECK_IN, contract.getId(), HandoverPhotoKind.ROOM, "image/png", PNG));

        assertEquals(true, ex.getMessage().startsWith("Không lưu được ảnh vào kho lưu trữ"));
        verify(photos, never()).save(any());
    }

    @Test
    void addPhotoRemovesStoredFileWhenTheRecordCannotBeSaved() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT));
        when(photos.save(any())).thenThrow(new IllegalStateException("db down"));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        assertThrows(IllegalStateException.class, () ->
            service.addPhoto(HandoverType.CHECK_IN, contract.getId(), HandoverPhotoKind.ROOM, "image/png", PNG));

        verify(storage).put(key.capture(), eq("image/png"), any());
        verify(storage).delete(key.getValue());
    }

    @Test
    void viewingAPhotoReportsStorageFailure() {
        Handover handover = existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT);
        givenHandover(handover);
        HandoverPhoto photo = new HandoverPhoto(UUID.randomUUID(), handover.id(), HandoverPhotoKind.ROOM, "k.png",
            "image/png", Instant.now(), staff.id());
        when(photos.findById(photo.id())).thenReturn(Optional.of(photo));
        when(storage.createSignedUrl(eq("k.png"), any())).thenThrow(new IllegalStateException("timeout"));
        when(storage.get("k.png")).thenThrow(new IllegalStateException("timeout"));

        assertThrows(FileStorageUnavailableException.class, () ->
            service.photoSignedUrl(HandoverType.CHECK_IN, contract.getId(), photo.id()));
        assertThrows(FileStorageUnavailableException.class, () ->
            service.photoFile(HandoverType.CHECK_IN, contract.getId(), photo.id()));
    }

    @Test
    void updateItemOnlyAllowedForCheckOut() {
        assertThrows(InvalidHandoverStateException.class, () -> service.updateItem(HandoverType.CHECK_IN,
            contract.getId(), UUID.randomUUID(), new ItemCommand(AssetStatus.DAMAGED, null, BigDecimal.TEN)));
    }

    @Test
    void updateItemRejectsDeductionForGoodAsset() {
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.DRAFT);
        givenHandover(checkOut);
        HandoverItem original = item(checkOut.id(), AssetStatus.GOOD, "0");
        when(items.findById(original.id())).thenReturn(Optional.of(original));

        assertThrows(IllegalArgumentException.class, () -> service.updateItem(HandoverType.CHECK_OUT,
            contract.getId(), original.id(), new ItemCommand(AssetStatus.GOOD, null, BigDecimal.TEN)));
    }

    @Test
    void updateItemRejectsItemFromAnotherHandover() {
        givenHandover(existing(HandoverType.CHECK_OUT, HandoverStatus.DRAFT));
        HandoverItem foreign = item(UUID.randomUUID(), AssetStatus.GOOD, "0");
        when(items.findById(foreign.id())).thenReturn(Optional.of(foreign));

        assertThrows(RuntimeException.class, () -> service.updateItem(HandoverType.CHECK_OUT, contract.getId(),
            foreign.id(), new ItemCommand(AssetStatus.DAMAGED, "vỡ", BigDecimal.TEN)));
    }

    // ---- send ----

    @Test
    void sendRequiresEveryPhotoKind() {
        Handover draft = existing(HandoverType.CHECK_IN, HandoverStatus.DRAFT);
        givenHandover(draft);
        when(photos.findByHandoverId(draft.id())).thenReturn(List.of(
            new HandoverPhoto(UUID.randomUUID(), draft.id(), HandoverPhotoKind.ROOM, "k", "image/png",
                Instant.now(), staff.id())));

        assertThrows(IllegalArgumentException.class, () -> service.send(HandoverType.CHECK_IN, contract.getId()));
    }

    // ---- confirm ----

    @Test
    void confirmByNonPrimaryTenantIsDenied() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_TENANT));
        CurrentUser other = new CurrentUser(UUID.randomUUID(), "t@x.vn", "Other", UserRole.TENANT);
        when(currentUser.getCurrentUser()).thenReturn(other);
        when(tenants.findByUserId(other.id())).thenReturn(Optional.of(
            new Tenant(UUID.randomUUID(), other.id(), "Khac", "079201009999", "0900000009", null, null, Instant.now())));

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.confirm(HandoverType.CHECK_IN, contract.getId()));
    }

    @Test
    void confirmCheckInWithoutIdCardPhotoIsRejected() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_TENANT));
        CurrentUser tenantUser = new CurrentUser(tenant.userId(), "t@x.vn", "Nguyen A", UserRole.TENANT);
        when(currentUser.getCurrentUser()).thenReturn(tenantUser);
        when(tenants.findByUserId(tenantUser.id())).thenReturn(Optional.of(tenant));

        assertThrows(IllegalArgumentException.class, () -> service.confirm(HandoverType.CHECK_IN, contract.getId()));
        verify(handovers, never()).save(any());
    }

    @Test
    void confirmRejectsWhenNotPendingTenant() {
        givenHandover(existing(HandoverType.CHECK_OUT, HandoverStatus.DRAFT));
        CurrentUser tenantUser = new CurrentUser(tenant.userId(), "t@x.vn", "Nguyen A", UserRole.TENANT);
        when(currentUser.getCurrentUser()).thenReturn(tenantUser);
        when(tenants.findByUserId(tenantUser.id())).thenReturn(Optional.of(tenant));

        assertThrows(InvalidHandoverStateException.class,
            () -> service.confirm(HandoverType.CHECK_OUT, contract.getId()));
    }

    // ---- approve ----

    @Test
    void approveCheckOutRequiresContractManagementPermission() {
        doThrow(new UnauthorizedPropertyAccessException("not owner"))
            .when(authorization).assertCanManageContracts(staff, propertyId);

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.approve(HandoverType.CHECK_OUT, contract.getId(), null));
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    @Test
    void approveCheckOutDefaultsToSumOfProposedDeductionsAndFlagsRepair() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        when(items.findByHandoverId(checkOut.id())).thenReturn(List.of(
            item(checkOut.id(), AssetStatus.DAMAGED, "300000"), item(checkOut.id(), AssetStatus.GOOD, "0")));

        Handover approved = service.approve(HandoverType.CHECK_OUT, contract.getId(), null);

        assertEquals(0, new BigDecimal("300000").compareTo(approved.finalDeduction()));
        verify(contractUseCase).terminateContract(contract.getId(), true);
    }

    @Test
    void approveCheckOutKeepsRoomAvailableWhenNothingNeedsRepair() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        when(items.findByHandoverId(checkOut.id())).thenReturn(List.of(item(checkOut.id(), AssetStatus.GOOD, "0")));

        service.approve(HandoverType.CHECK_OUT, contract.getId(), null);

        verify(contractUseCase).terminateContract(contract.getId(), false);
    }

    @Test
    void approveCheckOutBlocksUnpaidInvoicesUnlessEveryDebtIsPaidOrOffset() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        Invoice debt = payableInvoice("1000000");
        when(invoices.findPayableByContractIdForUpdate(contract.getId())).thenReturn(List.of(debt));

        ConflictException error = assertThrows(ConflictException.class,
            () -> service.approve(HandoverType.CHECK_OUT, contract.getId(), null, List.of()));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("10/2026"));
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
        verify(invoices, never()).save(debt);
    }

    @Test
    void approveCheckOutOffsetsSelectedDebtAndRecordsTheDepositPayment() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        Invoice debt = payableInvoice("1000000");
        when(invoices.findPayableByContractIdForUpdate(contract.getId())).thenReturn(List.of(debt));

        Handover approved = service.approve(HandoverType.CHECK_OUT, contract.getId(), new BigDecimal("500000"),
            List.of(debt.getId()));

        assertEquals(InvoiceStatus.PAID, debt.getStatus());
        verify(invoices).save(debt);
        verify(paymentRecords).save(org.mockito.ArgumentMatchers.argThat(record ->
            record.invoiceId().equals(debt.getId()) && record.amount().compareTo(new BigDecimal("1000000")) == 0
                && record.type() == com.phonghub.domain.model.InvoicePaymentRecord.Type.DEPOSIT_OFFSET));
        assertEquals(0, approved.finalDeduction().compareTo(new BigDecimal("500000")));
        verify(contractUseCase).terminateContract(contract.getId(), false);
    }

    @Test
    void approveCheckOutReportsShortfallWhenDepositCannotCoverSelectedDebt() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        Invoice debt = payableInvoice("1000000");
        when(invoices.findPayableByContractIdForUpdate(contract.getId())).thenReturn(List.of(debt));

        ConflictException error = assertThrows(ConflictException.class,
            () -> service.approve(HandoverType.CHECK_OUT, contract.getId(), new BigDecimal("2500000"),
                List.of(debt.getId())));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("500000"));
        verify(invoices, never()).save(debt);
        verify(paymentRecords, never()).save(any());
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    private Invoice payableInvoice(String amount) {
        Instant now = Instant.now();
        return new Invoice(UUID.randomUUID(), contract.getId(), roomId, tenant.id(), 10, 2026, BigDecimal.ZERO,
            new BigDecimal(amount), BigDecimal.ZERO, LocalDate.of(2026, 10, 31), InvoiceStatus.ISSUED, "PH12345678",
            InvoiceType.RENT, null, null, now, now);
    }

    @Test
    void approveCheckOutRejectsNegativeFinalDeduction() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        when(items.findByHandoverId(checkOut.id())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
            () -> service.approve(HandoverType.CHECK_OUT, contract.getId(), new BigDecimal("-1")));
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    @Test
    void approveCheckInDoesNotTerminateContract() {
        Handover checkIn = existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkIn);

        Handover approved = service.approve(HandoverType.CHECK_IN, contract.getId(), null);

        assertEquals(HandoverStatus.APPROVED, approved.status());
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    @Test
    void approvingCheckInActivatesDraftContract() {
        contract = Contract.create(propertyId, roomId, tenant.id(), new BigDecimal("3000000"),
            new BigDecimal("2500000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5);
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_APPROVAL));

        service.approve(HandoverType.CHECK_IN, contract.getId(), null);

        verify(contractUseCase).activateContract(contract.getId());
    }

    // ---- lỗ hổng đã xác nhận trong audit; bật lại khi đã sửa ----

    @Test
    void approveCheckOutMustNotDeductMoreThanDeposit() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        when(items.findByHandoverId(checkOut.id())).thenReturn(List.of(item(checkOut.id(), AssetStatus.DAMAGED, "0")));

        assertThrows(ConflictException.class,
            () -> service.approve(HandoverType.CHECK_OUT, contract.getId(), new BigDecimal("9000000")));
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    // ---- skip ----

    /** Repository giả giữ biên bản theo loại, để skip đọc lại được bản vừa tạo. */
    private Map<HandoverType, Handover> statefulHandovers() {
        Map<HandoverType, Handover> store = new EnumMap<>(HandoverType.class);
        when(handovers.save(any())).thenAnswer(inv -> {
            Handover h = inv.getArgument(0);
            store.put(h.type(), h);
            return h;
        });
        when(handovers.find(eq(contract.getId()), any()))
            .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<HandoverType>getArgument(1))));
        when(handovers.findForUpdate(eq(contract.getId()), any()))
            .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<HandoverType>getArgument(1))));
        return store;
    }

    @Test
    void skipCheckInWithoutHandoverCreatesSampleAndApprovesWithoutPhotosOrTenant() {
        statefulHandovers();

        Handover skipped = service.skip(HandoverType.CHECK_IN, contract.getId());

        assertEquals(HandoverStatus.APPROVED, skipped.status());
        assertFalse(skipped.confirmedByTenant());
        assertEquals(BigDecimal.ZERO, skipped.initialElectricMeter());
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    @Test
    void skipCheckOutCopiesCheckInMetersAndTerminatesContract() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Map<HandoverType, Handover> store = statefulHandovers();
        Handover checkIn = existing(HandoverType.CHECK_IN, HandoverStatus.APPROVED);
        store.put(HandoverType.CHECK_IN, checkIn);
        when(items.findByHandoverId(any())).thenReturn(List.of());

        Handover skipped = service.skip(HandoverType.CHECK_OUT, contract.getId());

        assertEquals(HandoverStatus.APPROVED, skipped.status());
        assertEquals(checkIn.initialElectricMeter(), skipped.initialElectricMeter());
        assertEquals(checkIn.initialWaterMeter(), skipped.initialWaterMeter());
        assertEquals(BigDecimal.ZERO, skipped.finalDeduction());
        verify(authorization, atLeastOnce()).assertCanManageContracts(owner, propertyId);
        verify(contractUseCase).terminateContract(contract.getId(), false);
    }

    @Test
    void skipRequiresContractManager() {
        doThrow(new UnauthorizedPropertyAccessException("no")).when(authorization)
            .assertCanManageContracts(staff, propertyId);

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.skip(HandoverType.CHECK_OUT, contract.getId()));
        verify(handovers, never()).save(any());
    }

    @Test
    void skipFromPendingTenantDoesNotMarkTenantConfirmed() {
        Map<HandoverType, Handover> store = statefulHandovers();
        store.put(HandoverType.CHECK_IN, existing(HandoverType.CHECK_IN, HandoverStatus.PENDING_TENANT));

        Handover skipped = service.skip(HandoverType.CHECK_IN, contract.getId());

        assertEquals(HandoverStatus.APPROVED, skipped.status());
        assertFalse(skipped.confirmedByTenant());
    }

    @Test
    void skipRejectsAlreadyApprovedHandover() {
        givenHandover(existing(HandoverType.CHECK_IN, HandoverStatus.APPROVED));

        assertThrows(InvalidHandoverStateException.class,
            () -> service.skip(HandoverType.CHECK_IN, contract.getId()));
    }

    @Test
    void skipCheckOutWithoutApprovedCheckInFails() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        statefulHandovers();

        assertThrows(InvalidHandoverStateException.class,
            () -> service.skip(HandoverType.CHECK_OUT, contract.getId()));
        verify(contractUseCase, never()).terminateContract(any(), anyBoolean());
    }

    @Disabled("L3: send check-out không làm mới danh sách tài sản, bỏ sót tài sản thêm sau check-in")
    @Test
    void sendCheckOutIncludesAssetsAddedAfterCheckIn() {
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.DRAFT);
        givenHandover(checkOut);
        when(photos.findByHandoverId(checkOut.id())).thenReturn(List.of(
            new HandoverPhoto(UUID.randomUUID(), checkOut.id(), HandoverPhotoKind.ROOM, "a", "image/png", Instant.now(), staff.id()),
            new HandoverPhoto(UUID.randomUUID(), checkOut.id(), HandoverPhotoKind.METER_ELECTRIC, "b", "image/png", Instant.now(), staff.id()),
            new HandoverPhoto(UUID.randomUUID(), checkOut.id(), HandoverPhotoKind.METER_WATER, "c", "image/png", Instant.now(), staff.id())));
        RoomAsset added = mock(RoomAsset.class);
        when(added.id()).thenReturn(UUID.randomUUID());
        when(added.name()).thenReturn("Máy lạnh mới");
        when(added.quantity()).thenReturn(1);
        when(added.status()).thenReturn(AssetStatus.GOOD);
        when(assets.findActiveByRoomId(roomId)).thenReturn(List.of(added));
        when(items.findByHandoverId(checkOut.id())).thenReturn(List.of());

        service.send(HandoverType.CHECK_OUT, contract.getId());

        verify(items).replaceAll(eq(checkOut.id()), any(), any());
    }
}
