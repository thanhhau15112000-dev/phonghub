package com.phonghub.application;

import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.RoomService;
import com.phonghub.domain.exception.DuplicateRoomNumberException;
import com.phonghub.domain.exception.InvalidRoomCapacityException;
import com.phonghub.domain.exception.RoomNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceUpdateUnitTest {

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private PropertyRepositoryPort propertyRepository;

    @Mock
    private MaintenanceTicketRepositoryPort maintenanceTicketRepository;

    @Mock
    private ContractRepositoryPort contractRepository;

    @Mock
    private TenantRepositoryPort tenantRepository;

    @Mock
    private CurrentUserPort currentUserPort;

    @Mock
    private AuthorizationService authorizationService;

    private RoomService roomService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private CurrentUser ownerUser;
    private Room existingRoom;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
            roomRepository,
            propertyRepository,
            maintenanceTicketRepository,
            contractRepository,
            tenantRepository,
            currentUserPort,
            authorizationService
        );

        ownerUser = new CurrentUser(ownerId, "owner1@phonghub.local", "Owner One", UserRole.OWNER);
        existingRoom = new Room(
            roomId,
            propertyId,
            "101",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2,
            RoomStatus.AVAILABLE,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    @DisplayName("updateRoom successfully updates room when owner and valid data")
    void testUpdateRoomSuccess() {
        when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.findByPropertyIdAndRoomNumber(propertyId, "102")).thenReturn(Optional.empty());
        when(contractRepository.findActiveByRoomId(roomId)).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "102",
            2,
            new BigDecimal("30.0"),
            new BigDecimal("4000000"),
            3
        );

        Room updated = roomService.updateRoom(cmd);

        assertEquals("102", updated.getRoomNumber());
        assertEquals(2, updated.getFloor());
        assertEquals(new BigDecimal("30.0"), updated.getAreaSqm());
        assertEquals(new BigDecimal("4000000"), updated.getBasePrice());
        assertEquals(3, updated.getMaxOccupants());

        verify(authorizationService).assertOwnerOrAdmin(ownerUser, propertyId);
        verify(roomRepository).save(existingRoom);
    }

    @Test
    @DisplayName("updateRoom keeping same room number does not trigger duplicate error")
    void testUpdateRoomSameNumberSuccess() {
        when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.findByPropertyIdAndRoomNumber(propertyId, "101")).thenReturn(Optional.of(existingRoom));
        when(contractRepository.findActiveByRoomId(roomId)).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "101",
            1,
            new BigDecimal("26.0"),
            new BigDecimal("3600000"),
            2
        );

        Room updated = roomService.updateRoom(cmd);

        assertEquals("101", updated.getRoomNumber());
        assertEquals(new BigDecimal("3600000"), updated.getBasePrice());
    }

    @Test
    @DisplayName("updateRoom throws RoomNotFoundException when room does not exist")
    void testRoomNotFoundThrows() {
        when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "102",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2
        );

        assertThrows(RoomNotFoundException.class, () -> roomService.updateRoom(cmd));
    }

    @Test
    @DisplayName("updateRoom throws UnauthorizedPropertyAccessException when user is not owner/admin")
    void testUnauthorizedThrows() {
        CurrentUser staffUser = new CurrentUser(UUID.randomUUID(), "staff1@phonghub.local", "Staff One", UserRole.STAFF);
        when(currentUserPort.getCurrentUser()).thenReturn(staffUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        doThrow(new UnauthorizedPropertyAccessException("Access denied"))
            .when(authorizationService).assertOwnerOrAdmin(staffUser, propertyId);

        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "102",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2
        );

        assertThrows(UnauthorizedPropertyAccessException.class, () -> roomService.updateRoom(cmd));
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoom throws DuplicateRoomNumberException when number matches another room")
    void testDuplicateRoomNumberThrows() {
        when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));

        Room anotherRoom = new Room(
            UUID.randomUUID(),
            propertyId,
            "102",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2,
            RoomStatus.AVAILABLE,
            Instant.now(),
            Instant.now()
        );
        when(roomRepository.findByPropertyIdAndRoomNumber(propertyId, "102")).thenReturn(Optional.of(anotherRoom));

        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "102",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2
        );

        assertThrows(DuplicateRoomNumberException.class, () -> roomService.updateRoom(cmd));
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoom throws InvalidRoomCapacityException when reducing capacity below active occupants")
    void testReducingCapacityBelowActiveOccupantsThrows() {
        when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.findByPropertyIdAndRoomNumber(propertyId, "101")).thenReturn(Optional.of(existingRoom));

        // Create active contract with 2 occupants (1 primary + 1 additional)
        UUID contractId = UUID.randomUUID();
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        List<ContractOccupant> occupants = new ArrayList<>();
        occupants.add(new ContractOccupant(UUID.randomUUID(), contractId, tenant1, true, LocalDate.now().minusMonths(1), null));
        occupants.add(new ContractOccupant(UUID.randomUUID(), contractId, tenant2, false, LocalDate.now().minusMonths(1), null));

        Contract activeContract = new Contract(
            contractId,
            propertyId,
            roomId,
            tenant1,
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            LocalDate.now().minusMonths(1),
            LocalDate.now().plusMonths(11),
            5,
            ContractStatus.ACTIVE,
            occupants,
            Instant.now(),
            Instant.now()
        );

        when(contractRepository.findActiveByRoomId(roomId)).thenReturn(Optional.of(activeContract));

        // Attempt to reduce maxOccupants to 1 while 2 occupants reside
        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "101",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            1
        );

        InvalidRoomCapacityException ex = assertThrows(
            InvalidRoomCapacityException.class,
            () -> roomService.updateRoom(cmd)
        );
        assertTrue(ex.getMessage().contains("Không thể giảm số người tối đa (1) xuống dưới số người đang ở"));
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Changing room basePrice does not affect active contract rent amount")
    void testPriceUpdateDoesNotAffectActiveContractRent() {
        when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.findByPropertyIdAndRoomNumber(propertyId, "101")).thenReturn(Optional.of(existingRoom));

        UUID contractId = UUID.randomUUID();
        UUID tenant1 = UUID.randomUUID();
        BigDecimal originalRent = new BigDecimal("3500000");

        Contract activeContract = new Contract(
            contractId,
            propertyId,
            roomId,
            tenant1,
            originalRent,
            originalRent,
            LocalDate.now().minusMonths(1),
            LocalDate.now().plusMonths(11),
            5,
            ContractStatus.ACTIVE,
            List.of(new ContractOccupant(UUID.randomUUID(), contractId, tenant1, true, LocalDate.now().minusMonths(1), null)),
            Instant.now(),
            Instant.now()
        );

        when(contractRepository.findActiveByRoomId(roomId)).thenReturn(Optional.of(activeContract));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Update room basePrice to 4,500,000
        var cmd = new RoomUseCase.UpdateRoomCommand(
            roomId,
            "101",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("4500000"),
            2
        );

        Room updated = roomService.updateRoom(cmd);

        assertEquals(new BigDecimal("4500000"), updated.getBasePrice());
        // Contract rentAmount remains strictly originalRent (3,500,000)
        assertEquals(originalRent, activeContract.getRentAmount());
        verify(contractRepository, never()).save(any());
    }
}
