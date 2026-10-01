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
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAndPropertyAuthZIntegrationTest {

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
    @DisplayName("GET /api/tenants/{id} enforces object-level authorization across all roles")
    void testTenantGetEndpointObjectLevelAuthorization() throws Exception {
        // 1. ADMIN can access
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));

        // 2. Self (Tenant 1 himself) can access
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));

        // 3. Owner 1 (owns Property 1 where Tenant 1 resides) can access
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));

        // 4. Owner 2 (does not own Property 1) is blocked with 403 Forbidden
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isForbidden());

        // 5. Staff 1 (assigned to Property 1) can access
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));

        // 6. Staff 2 (assigned to Property 2, not Property 1) is blocked with 403 Forbidden
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_2_ID.toString()))
            .andExpect(status().isForbidden());

        // 7. Technician (not authorized to view tenant profiles) is blocked with 403 Forbidden
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TECH_1_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/properties authorization: OWNER creates PENDING property, ADMIN creates VERIFIED, others blocked")
    void testPropertyCreationAuthorization() throws Exception {
        // 1. OWNER creates property -> 201 Created, status = PENDING, ownerId assigned
        String ownerRequestBody = """
            {
                "name": "Nhà trọ Mới của Owner 1",
                "address": "999 Nguyễn Văn Linh, Quận 7",
                "description": "Nhà trọ mới xây",
                "totalRooms": 15
            }
            """;

        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerRequestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
            .andExpect(jsonPath("$.name").value("Nhà trọ Mới của Owner 1"));

        List<Property> ownerProps = propertyRepository.findByOwnerId(LocalDemoAuthenticationAdapter.OWNER_1_ID);
        Property createdByOwner = ownerProps.stream()
            .filter(p -> p.name().equals("Nhà trọ Mới của Owner 1"))
            .findFirst()
            .orElse(null);
        assertNotNull(createdByOwner);
        assertEquals(PropertyApprovalStatus.PENDING, createdByOwner.approvalStatus());
        assertEquals(LocalDemoAuthenticationAdapter.OWNER_1_ID, createdByOwner.ownerId());

        // 2. ADMIN creates property -> 201 Created, status = VERIFIED
        String adminRequestBody = """
            {
                "name": "Khu ký túc xá Trung tâm",
                "address": "1 Đại Cồ Việt, Hai Bà Trưng",
                "description": "Khu công cộng",
                "totalRooms": 50
            }
            """;

        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminRequestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.approvalStatus").value("VERIFIED"));

        // 3. TENANT is blocked with 403 Forbidden
        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerRequestBody))
            .andExpect(status().isForbidden());

        // 4. STAFF is blocked with 403 Forbidden
        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerRequestBody))
            .andExpect(status().isForbidden());

        // 5. TECH is blocked with 403 Forbidden
        mockMvc.perform(post("/api/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.TECH_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownerRequestBody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Owner can create property via UI and see form on properties page")
    void testOwnerCanCreatePropertyViaUi() throws Exception {
        // Owner 1 sees create property form on /properties
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thêm nhà trọ")))
            .andExpect(content().string(containsString("PENDING")));

        // Owner 1 submits property creation form
        mockMvc.perform(post("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .param("name", "Nhà trọ UI Test")
                .param("address", "12 Hoàng Diệu, Quận 4")
                .param("description", "Nhà trọ gần đại học")
                .param("totalRooms", "8"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/properties"))
            .andExpect(flash().attributeExists("successMessage"));
    }
}
