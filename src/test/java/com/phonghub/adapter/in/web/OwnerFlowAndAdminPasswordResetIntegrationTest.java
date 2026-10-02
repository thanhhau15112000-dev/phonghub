package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.time.LocalDate;
import java.util.List;
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
    private InMemoryMaintenanceTicketRepository ticketRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
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
    @DisplayName("Owner UI buttons render properly on contracts list, contract detail, and room detail")
    void testOwnerButtonsRenderInUi() throws Exception {
        // Owner 1 sees "+ Tạo hợp đồng" on contract list
        mockMvc.perform(get("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("+ Tạo hợp đồng")));

        // Owner 1 sees "Chấm dứt hợp đồng" on their active contract
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chấm dứt hợp đồng")));

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
    @DisplayName("Owner can execute full contract lifecycle: Create (draft) -> Activate -> Terminate")
    void testOwnerContractLifecycleEndToEnd() throws Exception {
        // 1. Create contract on Room 104 as Owner 1
        mockMvc.perform(post("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantFullName", "Lê Văn Test")
                .param("primaryTenantIdCard", "079201009999")
                .param("primaryTenantPhone", "0909999888")
                .param("primaryTenantEmail", "levantest@example.com")
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

        // 2. View draft contract page as Owner 1, verify "Kích hoạt hợp đồng" button
        mockMvc.perform(get("/contracts/" + createdContract.getId())
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Kích hoạt hợp đồng")));

        // 3. Activate contract as Owner 1
        mockMvc.perform(post("/contracts/" + createdContract.getId() + "/activate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts/" + createdContract.getId()))
            .andExpect(flash().attributeExists("successMessage"));

        Contract activatedContract = contractRepository.findById(createdContract.getId()).orElseThrow();
        assertEquals(ContractStatus.ACTIVE, activatedContract.getStatus());
        Room roomAfterActivation = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(RoomStatus.OCCUPIED, roomAfterActivation.getStatus());

        // 4. Terminate contract as Owner 1
        mockMvc.perform(post("/contracts/" + createdContract.getId() + "/terminate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("requiresMaintenance", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/contracts/" + createdContract.getId()))
            .andExpect(flash().attributeExists("successMessage"));

        Contract terminatedContract = contractRepository.findById(createdContract.getId()).orElseThrow();
        assertEquals(ContractStatus.TERMINATED, terminatedContract.getStatus());
        Room roomAfterTermination = roomRepository.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, roomAfterTermination.getStatus());
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
