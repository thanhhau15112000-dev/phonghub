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
import com.phonghub.adapter.in.web.api.AdminPropertyApiController;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
            .andExpect(jsonPath("$", hasSize(3)))
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
    @DisplayName("OWNER 1 only sees owned properties with approval status")
    void testOwner1ListsOwnedProperties() throws Exception {
        mockMvc.perform(get("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].ownerId", is(LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())));
    }

    @Test
    @DisplayName("OWNER 2 with no properties sees empty list")
    void testOwner2ListsEmptyProperties() throws Exception {
        mockMvc.perform(get("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("OWNER 2 accessing Property 1 owned by OWNER 1 returns 403 Forbidden")
    void testOwner2AccessingOtherOwnerPropertyForbidden() throws Exception {
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")))
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("OWNER 1 accessing Property 1 returns 200 OK with approval status")
    void testOwner1AccessingOwnedProperty() throws Exception {
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DataSeeder.PROP_1_ID.toString())))
            .andExpect(jsonPath("$.approvalStatus", is("VERIFIED")));
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

    @Test
    @DisplayName("ADMIN verifies PENDING property successfully (200 OK)")
    void testAdminVerifiesPendingPropertySuccess() throws Exception {
        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_2_ID + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DataSeeder.PROP_2_ID.toString())))
            .andExpect(jsonPath("$.approvalStatus", is("VERIFIED")));
    }

    @Test
    @DisplayName("ADMIN verifies already VERIFIED property returns 409 Conflict")
    void testAdminVerifiesAlreadyVerifiedPropertyConflict() throws Exception {
        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_1_ID + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Property Status")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("ADMIN verifies REJECTED property returns 409 Conflict")
    void testAdminVerifiesRejectedPropertyConflict() throws Exception {
        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_3_ID + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Property Status")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Non-ADMIN (STAFF) verifying property returns 403 Forbidden")
    void testNonAdminVerifyingPropertyForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_2_ID + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("ADMIN verifies non-existent property returns 404 Not Found")
    void testAdminVerifiesNonExistentPropertyNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/properties/" + nonExistentId + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title", is("Resource Not Found")))
            .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("ADMIN rejects PENDING property successfully with reason (200 OK)")
    void testAdminRejectsPendingPropertySuccess() throws Exception {
        AdminPropertyApiController.RejectPropertyRequest req = new AdminPropertyApiController.RejectPropertyRequest(
            "Thiếu chứng nhận thẩm duyệt thiết kế PCCC"
        );

        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_2_ID + "/reject")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DataSeeder.PROP_2_ID.toString())))
            .andExpect(jsonPath("$.approvalStatus", is("REJECTED")))
            .andExpect(jsonPath("$.rejectionReason", is("Thiếu chứng nhận thẩm duyệt thiết kế PCCC")));
    }

    @Test
    @DisplayName("ADMIN rejects property with blank reason returns 400 Bad Request")
    void testAdminRejectsPropertyWithBlankReasonBadRequest() throws Exception {
        AdminPropertyApiController.RejectPropertyRequest req = new AdminPropertyApiController.RejectPropertyRequest(
            "   "
        );

        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_2_ID + "/reject")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("ADMIN rejects already VERIFIED property returns 409 Conflict")
    void testAdminRejectsAlreadyVerifiedPropertyConflict() throws Exception {
        AdminPropertyApiController.RejectPropertyRequest req = new AdminPropertyApiController.RejectPropertyRequest(
            "Phát hiện vi phạm quy định"
        );

        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_1_ID + "/reject")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Property Status")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("ADMIN rejects already REJECTED property returns 409 Conflict")
    void testAdminRejectsAlreadyRejectedPropertyConflict() throws Exception {
        AdminPropertyApiController.RejectPropertyRequest req = new AdminPropertyApiController.RejectPropertyRequest(
            "Từ chối lần nữa"
        );

        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_3_ID + "/reject")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Property Status")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Non-ADMIN (STAFF) rejecting property returns 403 Forbidden")
    void testNonAdminRejectingPropertyForbidden() throws Exception {
        AdminPropertyApiController.RejectPropertyRequest req = new AdminPropertyApiController.RejectPropertyRequest(
            "Staff thử từ chối"
        );

        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_2_ID + "/reject")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("ADMIN rejects non-existent property returns 404 Not Found")
    void testAdminRejectsNonExistentPropertyNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        AdminPropertyApiController.RejectPropertyRequest req = new AdminPropertyApiController.RejectPropertyRequest(
            "Không tồn tại"
        );

        mockMvc.perform(post("/api/admin/properties/" + nonExistentId + "/reject")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title", is("Resource Not Found")))
            .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("OWNER 1 creates room in owned & VERIFIED property successfully (201 Created)")
    void testOwnerCreatesRoomInVerifiedPropertySuccess() throws Exception {
        RoomApiController.CreateRoomRequest req = new RoomApiController.CreateRoomRequest(
            "P105", 2, new BigDecimal("28.5"), new BigDecimal("4200000"), 2
        );

        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roomNumber", is("P105")))
            .andExpect(jsonPath("$.floor", is(2)))
            .andExpect(jsonPath("$.basePrice", is(4200000)))
            .andExpect(jsonPath("$.maxOccupants", is(2)))
            .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    @DisplayName("OWNER 2 creating room in unowned property returns 403 Forbidden")
    void testOwnerCreatingRoomInUnownedPropertyForbidden() throws Exception {
        RoomApiController.CreateRoomRequest req = new RoomApiController.CreateRoomRequest(
            "P105", 2, new BigDecimal("28.5"), new BigDecimal("4200000"), 2
        );

        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")))
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("OWNER 1 creating room in PENDING property returns 409 Conflict")
    void testOwnerCreatingRoomInPendingPropertyConflict() throws Exception {
        RoomApiController.CreateRoomRequest req = new RoomApiController.CreateRoomRequest(
            "P205", 2, new BigDecimal("25.0"), new BigDecimal("3800000"), 2
        );

        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_2_ID + "/rooms")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Property Status")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("OWNER 1 creating room in REJECTED property returns 409 Conflict")
    void testOwnerCreatingRoomInRejectedPropertyConflict() throws Exception {
        RoomApiController.CreateRoomRequest req = new RoomApiController.CreateRoomRequest(
            "P305", 2, new BigDecimal("25.0"), new BigDecimal("3800000"), 2
        );

        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_3_ID + "/rooms")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Property Status")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Creating room with DUPLICATE room number (case-insensitive) returns 409 Conflict")
    void testCreatingDuplicateRoomNumberConflict() throws Exception {
        // Room 101 already exists in PROP_1_ID, try adding "p101"
        RoomApiController.CreateRoomRequest req = new RoomApiController.CreateRoomRequest(
            "p101", 1, new BigDecimal("20.0"), new BigDecimal("3500000"), 2
        );

        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Duplicate Room Number")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Creating room with negative basePrice returns 400 Bad Request")
    void testCreatingRoomWithNegativePriceBadRequest() throws Exception {
        RoomApiController.CreateRoomRequest req = new RoomApiController.CreateRoomRequest(
            "P106", 1, new BigDecimal("20.0"), new BigDecimal("-1000"), 2
        );

        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("OWNER 1 updates room info in owned property successfully (200 OK)")
    void testOwner1UpdatesRoomSuccess() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104-VIP", 2, new BigDecimal("35.0"), new BigDecimal("4500000"), 4
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DataSeeder.ROOM_104_ID.toString())))
            .andExpect(jsonPath("$.roomNumber", is("P104-VIP")))
            .andExpect(jsonPath("$.floor", is(2)))
            .andExpect(jsonPath("$.areaSqm", is(35.0)))
            .andExpect(jsonPath("$.basePrice", is(4500000)))
            .andExpect(jsonPath("$.maxOccupants", is(4)));
    }

    @Test
    @DisplayName("ADMIN updates room info successfully (200 OK)")
    void testAdminUpdatesRoomSuccess() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104-ADM", 1, new BigDecimal("32.0"), new BigDecimal("4200000"), 3
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomNumber", is("P104-ADM")));
    }

    @Test
    @DisplayName("STAFF attempting to update room returns 403 Forbidden")
    void testStaffUpdatingRoomForbidden() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104-HACK", 1, new BigDecimal("30.0"), new BigDecimal("4000000"), 3
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")))
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("OWNER 2 attempting to update room in unowned property returns 403 Forbidden")
    void testOwner2UpdatingRoomInUnownedPropertyForbidden() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104-HACK", 1, new BigDecimal("30.0"), new BigDecimal("4000000"), 3
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")))
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Updating room number to existing room in same property returns 409 Conflict")
    void testUpdatingRoomDuplicateNumberConflict() throws Exception {
        // P101 already exists in PROP_1_ID
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "p101", 1, new BigDecimal("30.0"), new BigDecimal("4000000"), 3
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Duplicate Room Number")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Updating room keeping same room number succeeds (200 OK)")
    void testUpdatingRoomSameNumberSuccess() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104", 1, new BigDecimal("31.0"), new BigDecimal("4100000"), 3
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomNumber", is("P104")))
            .andExpect(jsonPath("$.basePrice", is(4100000)));
    }

    @Test
    @DisplayName("Decreasing room maxOccupants below active contract occupants returns 409 Conflict")
    void testReducingRoomCapacityBelowActiveContractOccupantsConflict() throws Exception {
        // Contract 1 on Room 101 has primary occupant (tenant1). Add a 2nd occupant.
        var contract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.addOccupant(UUID.randomUUID(), false, LocalDate.now());
        contractRepository.save(contract);

        // Active contract now has 2 occupants. Attempt to reduce maxOccupants to 1.
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 1
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Room Capacity")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Updating room with negative basePrice returns 400 Bad Request")
    void testUpdatingRoomWithNegativePriceBadRequest() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104", 1, new BigDecimal("30.0"), new BigDecimal("-500"), 3
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Updating room with maxOccupants = 0 returns 400 Bad Request")
    void testUpdatingRoomWithZeroOccupantsBadRequest() throws Exception {
        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P104", 1, new BigDecimal("30.0"), new BigDecimal("4000000"), 0
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Updating room basePrice does not alter active contract rent amount")
    void testUpdatingRoomPriceDoesNotAlterContractRent() throws Exception {
        var contractBefore = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        BigDecimal originalRent = contractBefore.getRentAmount();

        RoomApiController.UpdateRoomRequest req = new RoomApiController.UpdateRoomRequest(
            "P101", 1, new BigDecimal("25.0"), new BigDecimal("5500000"), 2
        );

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.basePrice", is(5500000)));

        var contractAfter = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(originalRent, contractAfter.getRentAmount());
    }

    @Test
    @DisplayName("OWNER 1 adds occupant to active contract (201 Created)")
    void testOwner1AddsOccupantSuccess() throws Exception {
        ContractApiController.AddOccupantRequest req = new ContractApiController.AddOccupantRequest(
            "Trần Thị Lan",
            "079200008888",
            "0908888999",
            "lan@phonghub.local",
            "Bình Phước",
            LocalDate.now(),
            false
        );

        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/occupants")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.contractId", is(DataSeeder.CONTRACT_1_ID.toString())))
            .andExpect(jsonPath("$.isPrimary", is(false)))
            .andExpect(jsonPath("$.checkInDate", is(LocalDate.now().toString())));
    }

    @Test
    @DisplayName("STAFF adding occupant to contract returns 403 Forbidden")
    void testStaffAddsOccupantForbidden() throws Exception {
        ContractApiController.AddOccupantRequest req = new ContractApiController.AddOccupantRequest(
            "Trần Thị Lan",
            "079200008888",
            "0908888999",
            "lan@phonghub.local",
            "Bình Phước",
            LocalDate.now(),
            false
        );

        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/occupants")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title", is("Unauthorized Property Access")))
            .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Adding occupant exceeding room maxOccupants returns 409 Conflict")
    void testAddOccupantExceedingMaxOccupantsConflict() throws Exception {
        // Contract 1 on Room 101 has 1 occupant. Room 101 maxOccupants = 2.
        // Add 2nd occupant:
        var contract = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.addOccupant(UUID.randomUUID(), false, LocalDate.now());
        contractRepository.save(contract);

        // Attempt to add 3rd occupant:
        ContractApiController.AddOccupantRequest req = new ContractApiController.AddOccupantRequest(
            "Trần Thị Lan",
            "079200008888",
            "0908888999",
            null,
            null,
            LocalDate.now(),
            false
        );

        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/occupants")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Invalid Room Capacity")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Adding occupant who is already in an active contract returns 409 Conflict")
    void testAddOccupantWithDuplicateActiveContractConflict() throws Exception {
        // Tenant 1 is primary tenant in active Contract 1
        var tenant1 = tenantRepository.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow();

        ContractApiController.AddOccupantRequest req = new ContractApiController.AddOccupantRequest(
            tenant1.fullName(),
            tenant1.identityCardNumber(),
            tenant1.phone(),
            tenant1.email(),
            null,
            LocalDate.now(),
            false
        );

        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/occupants")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title", is("Duplicate Active Contract")))
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Adding occupant with blank required fields returns 400 Bad Request")
    void testAddOccupantBlankFieldsBadRequest() throws Exception {
        ContractApiController.AddOccupantRequest req = new ContractApiController.AddOccupantRequest(
            "   ",
            "",
            "",
            null,
            null,
            LocalDate.now(),
            false
        );

        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/occupants")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)));
    }
}
