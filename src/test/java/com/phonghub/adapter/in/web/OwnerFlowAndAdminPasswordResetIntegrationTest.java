package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnerFlowAndAdminPasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private InMemoryPropertyRepository propertyRepository;

    @Autowired
    private InMemoryStaffPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemoryTenantRepository tenantRepository;

    @Autowired
    private InMemoryContractRepository contractRepository;

    @Autowired
    private InMemoryHandoverRepository handoverRepository;

    @Autowired
    private InMemoryMaintenanceTicketRepository ticketRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        handoverRepository.clear();
        ticketRepository.clear();

        DataSeeder.seedAll(
            userRepository,
            propertyRepository,
            assignmentRepository,
            roomRepository,
            tenantRepository,
            contractRepository,
            ticketRepository
        );
    }

    private UUID createTenantWithProfile(String fullName, String identityCardNumber, String phone, String email) {
        UUID userId = UUID.randomUUID();
        String suffix = identityCardNumber.substring(identityCardNumber.length() - 4);
        User user = userRepository.save(new User(userId, "tenant-" + suffix, email, fullName, phone,
            UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()));
        Tenant tenant = tenantRepository.save(Tenant.create(user.id(), fullName, identityCardNumber, phone, email, null));
        return tenant.id();
    }

    @Test
    @DisplayName("Owner can access new contract form, but Tenant is blocked with redirect")
    void testOwnerCanAccessNewContractFormAndTenantIsBlocked() throws Exception {
        // Owner 1 accesses form
        mockMvc.perform(get("/contracts/new")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tạo hợp đồng thuê")))
            .andExpect(content().string(containsString("Nhà trọ Xanh - Quận 7")));

        // Tenant is rejected
        mockMvc.perform(get("/contracts/new")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Owner can create a contract with an available tenant account without exposing another property's tenant")
    void ownerContractFormListsAvailableTenantAccount() throws Exception {
        User availableTenant = userRepository.save(new User(
            UUID.randomUUID(),
            "available-tenant",
            "available.tenant@example.test",
            "Available Tenant Account",
            "0900000991",
            UserRole.TENANT,
            User.UserStatus.ACTIVE,
            true,
            Instant.now()
        ));
        Tenant availableTenantProfile = tenantRepository.save(Tenant.create(
            availableTenant.id(), availableTenant.fullName(), "079000000991", availableTenant.phone(),
            availableTenant.email(), null));

        User tenantLinkedElsewhere = userRepository.save(new User(
            UUID.randomUUID(),
            "tenant-linked-elsewhere",
            "linked.elsewhere@example.test",
            "Tenant Linked Elsewhere",
            "0900000992",
            UserRole.TENANT,
            User.UserStatus.ACTIVE,
            false,
            Instant.now()
        ));
        Tenant otherPropertyTenant = tenantRepository.save(Tenant.create(
            tenantLinkedElsewhere.id(),
            "Tenant Linked Elsewhere",
            "079000000992",
            "0900000992",
            tenantLinkedElsewhere.email(),
            null
        ));
        tenantRepository.linkToProperty(otherPropertyTenant.id(), DataSeeder.PROP_2_ID,
            LocalDemoAuthenticationAdapter.OWNER_2_ID);

        mockMvc.perform(get("/contracts/new")
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Available Tenant Account")))
            .andExpect(content().string(containsString("****0991")))
            .andExpect(content().string(not(containsString("available.tenant@example.test"))))
            .andExpect(content().string(not(containsString("Tenant Linked Elsewhere"))))
            .andExpect(content().string(not(containsString("linked.elsewhere@example.test"))));

        mockMvc.perform(post("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantId", availableTenantProfile.id().toString())
                .param("depositAmount", "4000000")
                .param("rentAmount", "4000000")
                .param("startDate", LocalDate.now().toString())
                .param("endDate", LocalDate.now().plusMonths(6).toString())
                .param("paymentDay", "5"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        Tenant linkedTenant = tenantRepository.findByUserId(availableTenant.id()).orElseThrow();
        assertEquals("079000000991", linkedTenant.identityCardNumber());
        assertEquals(Set.of(DataSeeder.PROP_1_ID), tenantRepository.findLinkedPropertyIds(linkedTenant.id()));
    }

    @Test
    @DisplayName("Owner UI buttons render properly on contracts list, contract detail, and room detail")
    void testOwnerButtonsRenderInUi() throws Exception {
        // Owner 1 sees "+ Tạo hợp đồng" on contract list
        mockMvc.perform(get("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("+ Tạo hợp đồng")));

        // Owner 1 sees the early-checkout action on their active contract.
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Kết thúc sớm…")))
            .andExpect(content().string(containsString("name=\"reason\"")))
            .andExpect(content().string(containsString("Mở biên bản check-out")));

        // Owner 1 sees status change form and "+ Tạo hợp đồng" button on their room
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thay đổi trạng thái phòng")))
            .andExpect(content().string(containsString("+ Tạo hợp đồng")));

        // Tenant cannot access unassigned room 104 (redirects to /properties)
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/properties"));

        // Tenant accesses their assigned room 101 -> 200 OK, but does NOT see status change form or "+ Tạo hợp đồng"
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Thay đổi trạng thái phòng"))))
            .andExpect(content().string(not(containsString("+ Tạo hợp đồng"))));
    }

    @Test
    @DisplayName("Owner starts check-in from a draft contract and cancelling it removes the draft handover")
    void testOwnerContractLifecycleEndToEnd() throws Exception {
        // 1. Create contract on Room 104 as Owner 1
        mockMvc.perform(post("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantId", createTenantWithProfile("Lê Văn Test", "079201009999", "0909999888",
                    "levantest@example.com").toString())
                .param("depositAmount", "4000000")
                .param("rentAmount", "4000000")
                .param("startDate", LocalDate.now().toString())
                .param("endDate", LocalDate.now().plusMonths(6).toString())
                .param("paymentDay", "5"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        // Retrieve created contract
        List<Contract> contracts = contractRepository.findByPropertyId(DataSeeder.PROP_1_ID);
        Contract createdContract = contracts.stream()
            .filter(c -> c.getRoomId().equals(DataSeeder.ROOM_104_ID))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected contract on room 104"));

        assertEquals(ContractStatus.DRAFT, createdContract.getStatus());

        // 2. View draft contract page as Owner 1, verify the check-in action.
        mockMvc.perform(get("/contracts/" + createdContract.getId())
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lập biên bản check-in")));

        // 3. Start check-in as Owner 1; the contract remains DRAFT.
        mockMvc.perform(post("/contracts/" + createdContract.getId() + "/activate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts/" + createdContract.getId() + "/handover"))
            .andExpect(flash().attributeExists("successMessage"));

        Contract awaitingCheckIn = contractRepository.findById(createdContract.getId()).orElseThrow();
        assertEquals(ContractStatus.DRAFT, awaitingCheckIn.getStatus());
        assertEquals(HandoverType.CHECK_IN, handoverRepository.find(createdContract.getId(), HandoverType.CHECK_IN)
            .orElseThrow().type());
        Room roomWhilePending = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, roomWhilePending.getStatus());

        // 4. Cancelling a draft contract also removes its unsubmitted handover.
        mockMvc.perform(post("/contracts/" + createdContract.getId() + "/cancel")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts/" + createdContract.getId()))
            .andExpect(flash().attributeExists("successMessage"));

        Contract cancelledContract = contractRepository.findById(createdContract.getId()).orElseThrow();
        assertEquals(ContractStatus.CANCELLED, cancelledContract.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(handoverRepository.find(createdContract.getId(), HandoverType.CHECK_IN).isEmpty());
    }

    @Test
    @DisplayName("Owner can change room status on their own room via UI")
    void testOwnerCanChangeRoomStatus() throws Exception {
        // Change Room 104 from AVAILABLE to MAINTENANCE
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_104_ID + "/status")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("status", "MAINTENANCE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/rooms/" + DataSeeder.ROOM_104_ID))
            .andExpect(flash().attributeExists("successMessage"));

        Room room = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(RoomStatus.MAINTENANCE, room.getStatus());

        // Change Room 104 from MAINTENANCE back to AVAILABLE
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_104_ID + "/status")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("status", "AVAILABLE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/rooms/" + DataSeeder.ROOM_104_ID))
            .andExpect(flash().attributeExists("successMessage"));

        room = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    @DisplayName("Admin can reset password for Staff, Technician, and Owner, but is blocked for Admin")
    void testAdminPasswordResetAllowedAndForbiddenRoles() throws Exception {
        // 1. Reset Staff password -> Success
        mockMvc.perform(post("/admin/users/" + LocalDemoAuthenticationAdapter.STAFF_1_ID + "/reset-password")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("resetResult"));

        // 2. Reset Technician password -> Success
        mockMvc.perform(post("/admin/users/" + LocalDemoAuthenticationAdapter.TECH_1_ID + "/reset-password")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("resetResult"));

        // 3. Reset Owner password -> Success
        mockMvc.perform(post("/admin/users/" + LocalDemoAuthenticationAdapter.OWNER_1_ID + "/reset-password")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("resetResult"));

        // 4. Reset Admin password -> Blocked with errorMessage
        mockMvc.perform(post("/admin/users/" + LocalDemoAuthenticationAdapter.ADMIN_ID + "/reset-password")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("errorMessage"));
    }
}
