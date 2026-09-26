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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhongHubUiIntegrationTest {

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
    @DisplayName("Dashboard page renders successfully")
    void testDashboardRenders() throws Exception {
        mockMvc.perform(get("/dashboard")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Bảng điều khiển quản lý nhà trọ")))
            .andExpect(content().string(containsString("Nhà trọ được truy cập")))
            .andExpect(content().string(containsString("PhongHub")))
            .andExpect(content().string(not(containsString("PhongHub Ops"))))
            .andExpect(content().string(containsString("aria-label=\"Chọn người dùng demo\"")))
            .andExpect(content().string(not(containsString("Người dùng hiện tại"))))
            .andExpect(content().string(not(containsString("style=\"font-weight: 500;\""))));
    }

    @Test
    @DisplayName("Properties list page renders successfully")
    void testPropertiesListRenders() throws Exception {
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhà trọ Xanh - Quận 7")));
    }

    @Test
    @DisplayName("Properties list page for OWNER 1 renders custom title and approval status badges")
    void testPropertiesListForOwner1() throws Exception {
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhà trọ của tôi")))
            .andExpect(content().string(containsString("Nhà trọ Xanh - Quận 7")))
            .andExpect(content().string(containsString("Đã duyệt")))
            .andExpect(content().string(containsString("Chờ duyệt")))
            .andExpect(content().string(containsString("Bị từ chối")))
            .andExpect(content().string(containsString("Giấy phép kinh doanh chưa hợp lệ hoặc thiếu chứng nhận PCCC")));
    }

    @Test
    @DisplayName("Properties list page for OWNER 2 with no properties renders owner empty state")
    void testPropertiesListForOwner2EmptyState() throws Exception {
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhà trọ của tôi")))
            .andExpect(content().string(containsString("Bạn chưa có nhà trọ nào trên hệ thống.")));
    }

    @Test
    @DisplayName("Property detail page renders room list and status badges")
    void testPropertyDetailRenders() throws Exception {
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("P101")))
            .andExpect(content().string(containsString("Đang thuê")))
            .andExpect(content().string(containsString("Bảo trì")));
    }

    @Test
    @DisplayName("Room detail page renders status transition options")
    void testRoomDetailRenders() throws Exception {
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thông tin phòng")))
            .andExpect(content().string(containsString("P101")));
    }

    @Test
    @DisplayName("Contracts list and detail pages render successfully")
    void testContractsUiRenders() throws Exception {
        mockMvc.perform(get("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Hợp đồng")));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nguyễn Văn A")))
            .andExpect(content().string(containsString("Đang hiệu lực")));

        mockMvc.perform(get("/contracts/new")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chọn nhà trọ")))
            .andExpect(content().string(containsString("Chọn phòng")));
    }

    @Test
    @DisplayName("Maintenance list and detail pages render successfully")
    void testMaintenanceUiRenders() throws Exception {
        mockMvc.perform(get("/maintenance")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Yêu cầu bảo trì")));

        mockMvc.perform(get("/maintenance/" + DataSeeder.TICKET_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Sửa vòi nước và kiểm tra máy lạnh")))
            .andExpect(content().string(containsString("Đang xử lý")));

        mockMvc.perform(get("/maintenance/new")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Báo hỏng phòng")))
            .andExpect(content().string(containsString("Mức độ")));
    }

    @Test
    @DisplayName("Actor switch endpoint sets session and redirects")
    void testSwitchUser() throws Exception {
        mockMvc.perform(get("/switch-user?userId=" + LocalDemoAuthenticationAdapter.STAFF_1_ID))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Admin can filter properties list by approval status")
    void testAdminFilterPropertiesByStatus() throws Exception {
        // Filter by PENDING
        mockMvc.perform(get("/properties?status=PENDING")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Khu trọ Tân Bình")))
            .andExpect(content().string(not(containsString("Nhà trọ Xanh - Quận 7"))));

        // Filter by VERIFIED
        mockMvc.perform(get("/properties?status=VERIFIED")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nhà trọ Xanh - Quận 7")))
            .andExpect(content().string(not(containsString("Khu trọ Tân Bình"))));
    }

    @Test
    @DisplayName("Admin can verify pending property via UI")
    void testAdminVerifiesPropertyViaUi() throws Exception {
        mockMvc.perform(post("/properties/" + DataSeeder.PROP_2_ID + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/properties"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("Non-admin verifying property via UI returns error flash message")
    void testNonAdminVerifyingPropertyViaUiFails() throws Exception {
        mockMvc.perform(post("/properties/" + DataSeeder.PROP_2_ID + "/verify")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/properties"))
            .andExpect(flash().attributeExists("errorMessage"));
    }
}
