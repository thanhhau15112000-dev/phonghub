package com.phonghub.application;

import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.RoomAssetEventRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.RoomAssetService;
import com.phonghub.domain.exception.RoomAssetNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomAssetServiceUnitTest {

    private final UUID propertyId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "o@x.vn", "Owner", UserRole.OWNER);
    private final CurrentUser tenant = new CurrentUser(UUID.randomUUID(), "t@x.vn", "Tenant", UserRole.TENANT);

    private RoomAssetRepositoryPort assets;
    private RoomAssetEventRepositoryPort events;
    private RoomRepositoryPort rooms;
    private CurrentUserPort currentUserPort;
    private AuthorizationService authorization;
    private MaintenanceTicketRepositoryPort tickets;
    private FileStoragePort storage;
    private RoomAssetService service;

    @BeforeEach
    void setUp() {
        assets = mock(RoomAssetRepositoryPort.class);
        events = mock(RoomAssetEventRepositoryPort.class);
        rooms = mock(RoomRepositoryPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        authorization = mock(AuthorizationService.class);
        tickets = mock(MaintenanceTicketRepositoryPort.class);
        storage = mock(FileStoragePort.class);
        service = new RoomAssetService(assets, events, rooms, currentUserPort, authorization,
            mock(AuditPort.class), tickets, storage);

        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getPropertyId()).thenReturn(propertyId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assets.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RoomAsset asset(String imageUrl) {
        Instant now = Instant.now();
        return new RoomAsset(UUID.randomUUID(), roomId, "TS-1", "Tủ", null, 1, AssetStatus.GOOD, null, imageUrl,
            BigDecimal.ZERO, null, now, owner.id(), now, owner.id());
    }

    private RoomAssetUseCase.RecordEventCommand event(RoomAsset asset, UUID ticketId) {
        return new RoomAssetUseCase.RecordEventCommand(asset.id(), AssetEventType.DAMAGED, "Hỏng",
            BigDecimal.TEN, ticketId);
    }

    @Test
    void eventWithoutTicketIsRecordedAndUpdatesStatus() {
        RoomAsset asset = asset(null);
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));

        service.recordEvent(event(asset, null));

        verify(events).save(any());
        verify(assets).save(any(RoomAsset.class));
    }

    @Test
    void unknownTicketIsRejectedBeforeAnythingIsSaved() {
        RoomAsset asset = asset(null);
        UUID ticketId = UUID.randomUUID();
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));
        when(tickets.findById(ticketId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.recordEvent(event(asset, ticketId)));
        verify(events, never()).save(any());
        verify(assets, never()).save(any(RoomAsset.class));
    }

    @Test
    void ticketOfAnotherRoomIsRejected() {
        RoomAsset asset = asset(null);
        UUID ticketId = UUID.randomUUID();
        MaintenanceTicket ticket = mock(MaintenanceTicket.class);
        when(ticket.getRoomId()).thenReturn(UUID.randomUUID());
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class, () -> service.recordEvent(event(asset, ticketId)));
        verify(events, never()).save(any());
    }

    @Test
    void ticketOfTheSameRoomIsAccepted() {
        RoomAsset asset = asset(null);
        UUID ticketId = UUID.randomUUID();
        MaintenanceTicket ticket = mock(MaintenanceTicket.class);
        when(ticket.getRoomId()).thenReturn(roomId);
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));

        service.recordEvent(event(asset, ticketId));

        verify(events).save(any());
    }

    @Test
    void storedImageGetsAShortLivedSignedUrl() {
        RoomAsset asset = asset("properties/p/assets/a.png");
        URI signed = URI.create("https://storage.example/signed");
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));
        when(storage.createSignedUrl("properties/p/assets/a.png", Duration.ofMinutes(10))).thenReturn(signed);

        assertEquals(signed, service.imageSignedUrl(asset.id()));
    }

    @Test
    void externalImageLinkIsReturnedAsIsAndMissingImageIsNull() {
        RoomAsset external = asset("https://example.com/a.png");
        RoomAsset none = asset(null);
        when(assets.findById(external.id())).thenReturn(Optional.of(external));
        when(assets.findById(none.id())).thenReturn(Optional.of(none));

        assertEquals(URI.create("https://example.com/a.png"), service.imageSignedUrl(external.id()));
        assertNull(service.imageSignedUrl(none.id()));
        verify(storage, never()).createSignedUrl(any(), any());
    }

    @Test
    void tenantOutsideTheRoomCannotGetTheImage() {
        RoomAsset asset = asset("properties/p/assets/a.png");
        when(currentUserPort.getCurrentUser()).thenReturn(tenant);
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));
        when(authorization.canTenantViewRoom(tenant, roomId)).thenReturn(false);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.imageSignedUrl(asset.id()));
        verify(storage, never()).createSignedUrl(any(), any());
    }

    @Test
    void foreignOwnerCannotRecordEvents() {
        RoomAsset asset = asset(null);
        when(assets.findById(asset.id())).thenReturn(Optional.of(asset));
        doThrow(new UnauthorizedPropertyAccessException("no")).when(authorization)
            .assertCanManageRoomsAndAssets(owner, propertyId);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.recordEvent(event(asset, null)));
        verify(events, never()).save(any());
    }

    @Test
    void deletedAssetIsTreatedAsMissing() {
        RoomAsset deleted = asset(null).softDelete(owner.id());
        when(assets.findById(deleted.id())).thenReturn(Optional.of(deleted));

        assertThrows(RoomAssetNotFoundException.class, () -> service.get(deleted.id()));
    }
}
