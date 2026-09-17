package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RoomContractInvariantTest {

    @Autowired
    private RoomUseCase roomUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private ContractRepositoryPort contractRepository;

    @Autowired
    private DemoActorPort demoActorPort;

    @BeforeEach
    void setUp() {
        // Run as Admin
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_ADMIN);
    }

    @Test
    void manualTransitionOfOccupiedRoomWithActiveContractToAvailableIsRejected() {
        // Arrange: ROOM_101 is seeded as OCCUPIED with CONTRACT_1 as ACTIVE
        Room initialRoom = roomRepository.findById(DataSeeder.ROOM_101_ID).orElseThrow();
        assertEquals(RoomStatus.OCCUPIED, initialRoom.getStatus());

        Contract initialContract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        assertEquals(ContractStatus.ACTIVE, initialContract.getStatus());

        // Act & Assert: Attempting to manually change status to AVAILABLE must fail
        InvalidRoomStateException exception = assertThrows(
            InvalidRoomStateException.class,
            () -> roomUseCase.changeRoomStatus(DataSeeder.ROOM_101_ID, RoomStatus.AVAILABLE)
        );
        assertEquals(
            "Cannot manually change status of room 'P101' to AVAILABLE: an ACTIVE contract exists (ID: "
                + DataSeeder.CONTRACT_1_ID + "). Contract must be terminated via contract lifecycle.",
            exception.getMessage()
        );

        // Assert: Room status remains OCCUPIED
        Room afterRoom = roomRepository.findById(DataSeeder.ROOM_101_ID).orElseThrow();
        assertEquals(RoomStatus.OCCUPIED, afterRoom.getStatus(), "Room status must remain OCCUPIED");

        // Assert: Contract remains ACTIVE
        Contract afterContract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        assertEquals(ContractStatus.ACTIVE, afterContract.getStatus(), "Contract status must remain ACTIVE");
    }
}
