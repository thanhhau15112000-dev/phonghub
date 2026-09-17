package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MaintenanceNoPartialWriteTest {

    @Autowired
    private MaintenanceUseCase maintenanceUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private MaintenanceTicketRepositoryPort maintenanceTicketRepository;

    @Autowired
    private DemoActorPort demoActorPort;

    @BeforeEach
    void setUp() {
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);
    }

    @Test
    void createTicketOnReservedRoomWithSetMaintenanceFailsWithNoPhantomTickets() {
        // Arrange: ROOM_103 is initially AVAILABLE. Transition it to RESERVED.
        Room room = roomRepository.findById(DataSeeder.ROOM_103_ID).orElseThrow();
        room.transitionTo(RoomStatus.RESERVED);
        roomRepository.save(room);

        int initialTicketCount = maintenanceTicketRepository.findByRoomId(DataSeeder.ROOM_103_ID).size();
        assertEquals(0, initialTicketCount);

        // Act & Assert: Attempting to create a ticket with setRoomMaintenance=true on RESERVED room must fail
        assertThrows(
            InvalidRoomStateException.class,
            () -> maintenanceUseCase.createTicket(new MaintenanceUseCase.CreateMaintenanceTicketCommand(
                DataSeeder.ROOM_103_ID,
                "Broken Window",
                "Needs immediate replacement",
                MaintenancePriority.HIGH,
                true // setRoomMaintenance=true
            ))
        );

        // Verify: 0 phantom tickets were saved
        int afterTicketCount = maintenanceTicketRepository.findByRoomId(DataSeeder.ROOM_103_ID).size();
        assertEquals(0, afterTicketCount, "No phantom maintenance ticket must be persisted on failure");

        // Verify: Room status remains unchanged as RESERVED
        Room afterRoom = roomRepository.findById(DataSeeder.ROOM_103_ID).orElseThrow();
        assertEquals(RoomStatus.RESERVED, afterRoom.getStatus(), "Room status must remain RESERVED");
    }
}
