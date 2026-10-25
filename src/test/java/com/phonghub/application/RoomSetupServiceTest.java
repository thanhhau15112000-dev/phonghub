package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.in.RoomSetupUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.RoomSetupService;
import com.phonghub.domain.exception.DuplicateRoomNumberException;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RoomSetupServiceTest {
    private final UUID propertyId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "owner", "Owner", UserRole.OWNER);
    private RoomUseCase roomUseCase;
    private RoomAssetUseCase assetUseCase;
    private RoomConditionRepositoryPort conditionRepository;
    private RoomRepositoryPort roomRepository;
    private PropertyRepositoryPort propertyRepository;
    private CurrentUserPort currentUserPort;
    private AuthorizationService authorizationService;
    private RoomSetupService service;

    @BeforeEach
    void setUp() {
        roomUseCase = mock(RoomUseCase.class);
        assetUseCase = mock(RoomAssetUseCase.class);
        conditionRepository = mock(RoomConditionRepositoryPort.class);
        roomRepository = mock(RoomRepositoryPort.class);
        propertyRepository = mock(PropertyRepositoryPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        authorizationService = mock(AuthorizationService.class);
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        when(conditionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property(100)));
        when(roomRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(roomRepository.search(any(RoomSearchCriteria.class))).thenReturn(new RoomPage(List.of(), 0, 0, null));
        service = new RoomSetupService(roomUseCase, assetUseCase, conditionRepository, roomRepository,
            propertyRepository, currentUserPort, authorizationService);
    }

    @Test
    void initialConditionRequiresNonnegativeMetersAndEitherAssetsOrExplicitNoAssets() {
        assertThrows(IllegalArgumentException.class, () -> createCommand(BigDecimal.valueOf(-1), BigDecimal.ZERO,
            "Phòng mới", true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> createCommand(BigDecimal.ZERO, BigDecimal.ZERO,
            "Phòng mới", false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> createCommand(BigDecimal.ZERO, BigDecimal.ZERO,
            "Phòng mới", true, List.of(new RoomSetupUseCase.AssetInput("Giường", 1, AssetStatus.GOOD, null, null))));
        assertThrows(IllegalArgumentException.class, () -> createCommand(BigDecimal.ZERO, BigDecimal.ZERO,
            " ", true, List.of()));
    }

    @Test
    void createRoomSavesSnapshotAndAssetConditionCopy() {
        Room room = room("P101");
        RoomAsset asset = new RoomAsset(UUID.randomUUID(), room.getId(), "TS-A", "Giường", null, 1,
            AssetStatus.DAMAGED, "Trầy cạnh", null, BigDecimal.valueOf(1000), null,
            Instant.now(), owner.id(), Instant.now(), owner.id());
        when(roomUseCase.createRoom(any())).thenReturn(room);
        when(assetUseCase.create(any())).thenReturn(asset);

        var result = service.createRoom(createCommand(BigDecimal.valueOf(12), BigDecimal.valueOf(3), "Tường mới",
            false, List.of(new RoomSetupUseCase.AssetInput("Giường", 1, AssetStatus.DAMAGED, "Trầy cạnh",
                BigDecimal.valueOf(1000)))));

        assertEquals(room.getId(), result.room().getId());
        assertEquals(BigDecimal.valueOf(12), result.snapshot().electricMeter());
        assertEquals(1, result.items().size());
        assertEquals(asset.id(), result.items().getFirst().assetId());
        assertEquals(AssetStatus.DAMAGED, result.items().getFirst().status());
        ArgumentCaptor<List<RoomConditionItem>> items = ArgumentCaptor.forClass(List.class);
        verify(conditionRepository).saveItems(eq(result.snapshot().id()), items.capture());
        assertEquals("Giường", items.getValue().getFirst().name());
    }

    @Test
    void createRoomCanUseAnAssetTemplateForTheInitialCondition() {
        Room room = room("P101");
        RoomAsset bed = new RoomAsset(UUID.randomUUID(), room.getId(), "TS-A", "Giường", null, 1,
            AssetStatus.GOOD, null, null, BigDecimal.ZERO, null, Instant.now(), owner.id(), Instant.now(), owner.id());
        when(roomUseCase.createRoom(any())).thenReturn(room);
        when(assetUseCase.createFromTemplate(room.getId(), "BASIC")).thenReturn(List.of(bed));
        var command = new RoomSetupUseCase.CreateRoomCommand(propertyId, "P101", 1, BigDecimal.valueOf(20),
            BigDecimal.valueOf(1000), 2, BigDecimal.TEN, BigDecimal.ONE, "Tường mới", false, "BASIC",
            List.of(), null, null);

        var result = service.createRoom(command);

        assertEquals("Giường", result.items().getFirst().name());
        verify(assetUseCase).createFromTemplate(room.getId(), "BASIC");
        verify(assetUseCase, never()).create(any());
    }

    @Test
    void bulkRejectsDuplicatesAndCapacityBeforeSavingAnyRooms() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property(5)), Optional.of(property(2)));
        when(roomRepository.findByPropertyId(propertyId)).thenReturn(List.of(room("P100")));
        when(roomRepository.search(any(RoomSearchCriteria.class))).thenReturn(new RoomPage(List.of(), 0, 0, null));
        when(roomRepository.findByPropertyIdAndRoomNumber(propertyId, "P101")).thenReturn(Optional.empty());
        var duplicate = bulk(List.of(reading("P101"), reading("p101")));
        assertThrows(DuplicateRoomNumberException.class, () -> service.createRooms(duplicate));
        verify(roomUseCase, never()).createRoom(any());
        verify(conditionRepository, never()).save(any());

        var overCapacity = bulk(List.of(reading("P101"), reading("P102")));
        assertThrows(IllegalArgumentException.class, () -> service.createRooms(overCapacity));
        verify(roomUseCase, never()).createRoom(any());
    }

    @Test
    void bulkCreatesOneSnapshotForEachRoomUsingSeparateMeterReadings() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property(5)));
        when(roomRepository.findByPropertyId(propertyId)).thenReturn(List.of(room("P100")));
        when(roomRepository.search(any(RoomSearchCriteria.class))).thenReturn(new RoomPage(List.of(), 0, 0, null));
        when(roomRepository.findByPropertyIdAndRoomNumber(any(), any())).thenReturn(Optional.empty());
        when(roomUseCase.createRoom(any())).thenAnswer(invocation -> {
            RoomUseCase.CreateRoomCommand command = invocation.getArgument(0);
            return room(command.roomNumber());
        });

        var created = service.createRooms(bulk(List.of(reading("P101"), reading("P102"))));

        assertEquals(2, created.size());
        assertEquals(BigDecimal.valueOf(101), created.getFirst().snapshot().electricMeter());
        assertEquals(BigDecimal.valueOf(102), created.getLast().snapshot().waterMeter());
        verify(roomUseCase, org.mockito.Mockito.times(2)).createRoom(any());
        verify(conditionRepository, org.mockito.Mockito.times(2)).save(any());
        assertTrue(created.stream().allMatch(result -> result.items().isEmpty()));
    }

    private RoomSetupUseCase.CreateRoomCommand createCommand(BigDecimal electric, BigDecimal water, String note,
                                                              boolean noAssets, List<RoomSetupUseCase.AssetInput> assets) {
        return new RoomSetupUseCase.CreateRoomCommand(propertyId, "P101", 1, BigDecimal.valueOf(20),
            BigDecimal.valueOf(1000), 2, electric, water, note, noAssets, assets, null, null);
    }

    private RoomSetupUseCase.BulkCreateCommand bulk(List<RoomSetupUseCase.RoomReadingInput> readings) {
        return new RoomSetupUseCase.BulkCreateCommand(propertyId, 1, BigDecimal.valueOf(20),
            BigDecimal.valueOf(1000), 2, "Hiện trạng ổn", "NONE", readings);
    }

    private RoomSetupUseCase.RoomReadingInput reading(String number) {
        return new RoomSetupUseCase.RoomReadingInput(number, BigDecimal.valueOf(101), BigDecimal.valueOf(102));
    }

    private Room room(String number) {
        return new Room(UUID.randomUUID(), propertyId, number, 1, BigDecimal.valueOf(20), BigDecimal.valueOf(1000),
            2, RoomStatus.AVAILABLE, Instant.now(), Instant.now());
    }

    private Property property(int total) {
        return new Property(propertyId, "Nhà", "Địa chỉ", "", total, owner.id(), PropertyApprovalStatus.VERIFIED,
            null, Instant.now());
    }
}
