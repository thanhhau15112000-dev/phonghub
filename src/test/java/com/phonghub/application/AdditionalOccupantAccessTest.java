package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AdditionalOccupantAccessTest {

    @Autowired
    private ContractUseCase contractUseCase;

    @Autowired
    private RoomUseCase roomUseCase;

    @Autowired
    private ContractRepositoryPort contractRepository;

    @Autowired
    private TenantRepositoryPort tenantRepository;

    @Autowired
    private CurrentUserPort currentUserPort;

    @Test
    void additionalOccupantCanAccessContractAndAssignedRoom() {
        // Arrange: Create a second tenant as an additional occupant in Contract 1
        UUID occupantUserId = UUID.randomUUID();
        UUID occupantTenantId = UUID.randomUUID();
        Tenant occupantTenant = new Tenant(
            occupantTenantId,
            occupantUserId,
            "Nguyen Thi B (Co-tenant)",
            "079201002222",
            "0909888777",
            "cotenant@phonghub.local",
            "Long An",
            Instant.now()
        );
        tenantRepository.save(occupantTenant);

        // Fetch existing contract 1 and add the additional occupant
        Contract contract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.addOccupant(occupantTenantId, false, LocalDate.now());
        contractRepository.save(contract);

        // Act as the co-tenant (additional occupant)
        CurrentUser occupantUser = new CurrentUser(
            occupantUserId,
            "cotenant@phonghub.local",
            "Nguyen Thi B",
            UserRole.TENANT
        );
        currentUserPort.setCurrentUser(occupantUser);

        // 1. Co-tenant must see Contract 1 in accessible contracts list
        List<Contract> accessibleContracts = contractUseCase.listContractsForTenant(occupantUserId);
        assertEquals(1, accessibleContracts.size(), "Additional occupant must see the contract they reside in");
        assertEquals(contract.getId(), accessibleContracts.getFirst().getId());

        // 2. Co-tenant must retrieve Contract 1 as active contract
        Optional<Contract> activeContractOpt = contractUseCase.getActiveContractForTenant(occupantUserId);
        assertTrue(activeContractOpt.isPresent(), "Additional occupant must have an active contract");
        assertEquals(contract.getId(), activeContractOpt.get().getId());

        // 3. Co-tenant listing rooms for property must see Room 101
        List<Room> rooms = roomUseCase.listRoomsForProperty(contract.getPropertyId());
        assertEquals(1, rooms.size(), "Additional occupant should only see their occupied room");
        assertEquals(DataSeeder.ROOM_101_ID, rooms.getFirst().getId());
    }
}
