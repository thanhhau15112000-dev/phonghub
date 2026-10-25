package com.phonghub.application;

import com.phonghub.application.port.in.ContractUseCase;
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
import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.HandoverService;
import com.phonghub.domain.exception.FileStorageUnavailableException;
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
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private RoomConditionRepositoryPort roomConditions;
    private TenantRepositoryPort tenants;
    private TenantDocumentRepositoryPort documents;
    private ContractUseCase contractUseCase;
    private CurrentUserPort currentUser;
    private AuthorizationService authorization;
    private FileStoragePort storage;
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
        roomConditions = mock(RoomConditionRepositoryPort.class);
        tenants = mock(TenantRepositoryPort.class);
        documents = mock(TenantDocumentRepositoryPort.class);
        contractUseCase = mock(ContractUseCase.class);
        currentUser = mock(CurrentUserPort.class);
        authorization = mock(AuthorizationService.class);
        storage = mock(FileStoragePort.class);

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

        service = new HandoverService(handovers, photos, items, rules, contracts, assets, tenants, documents,
            contractUseCase, storage, currentUser, authorization, mock(AuditPort.class),
            1024 * 1024, null, roomConditions);
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
    void approveCheckOutRequiresOwnerOrAdmin() {
        doThrow(new UnauthorizedPropertyAccessException("not owner"))
            .when(authorization).assertOwnerOrAdmin(staff, propertyId);

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

    // ---- lỗ hổng đã xác nhận trong audit; bật lại khi đã sửa ----

    @Disabled("L1: finalDeduction chưa bị chặn trên bởi tiền cọc (cọc 3.000.000)")
    @Test
    void approveCheckOutMustNotDeductMoreThanDeposit() {
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Handover checkOut = existing(HandoverType.CHECK_OUT, HandoverStatus.PENDING_APPROVAL);
        givenHandover(checkOut);
        when(items.findByHandoverId(checkOut.id())).thenReturn(List.of(item(checkOut.id(), AssetStatus.DAMAGED, "0")));

        assertThrows(IllegalArgumentException.class,
            () -> service.approve(HandoverType.CHECK_OUT, contract.getId(), new BigDecimal("9000000")));
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
