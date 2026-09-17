package com.phonghub.adapter.in.web;

import tools.jackson.databind.ObjectMapper;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.adapter.in.web.api.ContractApiController;
import com.phonghub.adapter.in.web.api.MaintenanceApiController;
import com.phonghub.adapter.in.web.api.PropertyApiController;
import com.phonghub.adapter.in.web.api.RoomApiController;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PhongHubApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("ADMIN lists all properties")
    void testAdminListsAllProperties() throws Exception {
        mockMvc.perform(get("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name", notNullValue()));
    }

    @Test
    @DisplayName("STAFF 1 only sees assigned Property 1")
    void testStaffListsOnlyAssignedProperties() throws Exception {
        mockMvc.perform(get("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(DataSeeder.PROP_1_ID.toString())));
    }

    @Test
    @DisplayName("STAFF 1 accessing unassigned Property 2 returns 403 Forbidden with Problem Details")
    void testStaffAccessingUnassignedPropertyForbidden() throws Exception {
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_2_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")))
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("ADMIN creates new property successfully (201 Created)")
    void testAdminCreatesProperty() throws Exception {
        PropertyApiController.CreatePropertyRequest req = new PropertyApiController.CreatePropertyRequest(
            "Nha Tro Thu Duc",
            "12 Vo Van Ngan, TP Thu Duc",
            "Gan truong SPKT",
            12
        );

        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Nha Tro Thu Duc")))
            .andExpect(jsonPath("$.totalRooms", is(12)));
    }

    @Test
    @DisplayName("STAFF creating property returns 403 Forbidden")
    void testStaffCreatingPropertyForbidden() throws Exception {
        PropertyApiController.CreatePropertyRequest req = new PropertyApiController.CreatePropertyRequest(
            "Nha Tro Hack",
            "123 Street",
            "",
            5
        );

        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Validation error returns 400 Bad Request Problem Details")
    void testValidationFailure() throws Exception {
        PropertyApiController.CreatePropertyRequest req = new PropertyApiController.CreatePropertyRequest(
            "", // empty name violates @NotBlank
            "", // empty address
            "",
            -1 // negative rooms
        );

        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)))
            .andExpect(jsonPath("$.errors", notNullValue()));
    }

    @Test
    @DisplayName("Create and activate contract on available room changes room to OCCUPIED")
    void testContractCreationAndActivation() throws Exception {
        ContractApiController.CreateContractRequest req = new ContractApiController.CreateContractRequest(
            DataSeeder.PROP_1_ID,
            DataSeeder.ROOM_104_ID, // Available room
            "Pham Van C",
            "079201003333",
            "0909112233",
            "c@local",
            null,
            new BigDecimal("4000000"),
            new BigDecimal("4000000"),
            LocalDate.now(),
            LocalDate.now().plusYears(1),
            5
        );

        String response = mockMvc.perform(post("/api/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("DRAFT")))
            .andReturn().getResponse().getContentAsString();

        String contractId = objectMapper.readTree(response).get("id").asText();

        // Activate contract
        mockMvc.perform(post("/api/contracts/" + contractId + "/activate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Verify room is now OCCUPIED
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("OCCUPIED")));
    }

    @Test
    @DisplayName("Duplicate active contract for same room returns 409 Conflict")
    void testDuplicateActiveContractReturnsConflict() throws Exception {
        // Room 101 already has an active contract in DataSeeder
        ContractApiController.CreateContractRequest req = new ContractApiController.CreateContractRequest(
            DataSeeder.PROP_1_ID,
            DataSeeder.ROOM_101_ID,
            "Le Van D",
            "079201004444",
            "0909444555",
            "d@local",
            null,
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            LocalDate.now(),
            LocalDate.now().plusYears(1),
            5
        );

        mockMvc.perform(post("/api/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Duplicate Active Contract")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Invalid room status transition returns 409 Conflict")
    void testInvalidRoomStatusTransitionReturnsConflict() throws Exception {
        // Room 102 is in MAINTENANCE. Transition to OCCUPIED is invalid.
        RoomApiController.UpdateRoomStatusRequest req = new RoomApiController.UpdateRoomStatusRequest(RoomStatus.OCCUPIED);

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_102_ID + "/status")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Room State Transition")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Technician accept ticket within assigned property succeeds, unassigned property returns 403 Forbidden")
    void testTechnicianMaintenanceFlow() throws Exception {
        // Tech 1 is assigned to Property 1. Ticket 1 is in Property 1.
        mockMvc.perform(post("/api/maintenance/" + DataSeeder.TICKET_1_ID + "/accept")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TECH_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
            .andExpect(jsonPath("$.assignedTechnicianId", is(LocalDemoAuthenticationAdapter.TECH_1_ID.toString())));

        // Now create a ticket in Property 2
        MaintenanceApiController.CreateTicketRequest req2 = new MaintenanceApiController.CreateTicketRequest(
            DataSeeder.ROOM_201_ID,
            "Broken bulb",
            "Bulb blown",
            MaintenancePriority.LOW,
            false
        );

        String res = mockMvc.perform(post("/api/maintenance")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String ticket2Id = objectMapper.readTree(res).get("id").asText();

        // Tech 1 tries to accept ticket in unassigned Property 2 -> 403 Forbidden
        mockMvc.perform(post("/api/maintenance/" + ticket2Id + "/accept")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TECH_1_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")));
    }

    @Test
    @DisplayName("TENANT can access their own active contract but cannot see other rooms")
    void testTenantContractAndRoomAccess() throws Exception {
        // Tenant 1 has active contract in Room 101
        mockMvc.perform(get("/api/contracts/my-active")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomId", is(DataSeeder.ROOM_101_ID.toString())));

        // Tenant 1 can view Room 101
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().isOk());

        // Tenant 1 cannot access Room 102 (different room) -> 403 Forbidden
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_102_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().isForbidden());
    }
}
