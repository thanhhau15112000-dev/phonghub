package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Hiển thị: tiền/ngày kiểu Việt Nam, cột phòng ở danh sách bảo trì, trang người thuê, thông báo lỗi dễ hiểu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UiPolishIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;

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
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
    }

    @Test
    @DisplayName("Trang hợp đồng: tiền 3.500.000 đ, ngày dd/MM/yyyy, tiêu đề theo số phòng")
    void contractPageUsesVietnameseFormats() throws Exception {
        String endDate = contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow().getEndDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("3.500.000 đ")))
            .andExpect(content().string(containsString(endDate)))
            .andExpect(content().string(containsString("Hợp đồng phòng <span>P101</span>")));
    }

    @Test
    @DisplayName("Danh sách bảo trì có cột Phòng")
    void maintenanceListShowsRoom() throws Exception {
        mockMvc.perform(get("/maintenance").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<th>Phòng</th>")))
            .andExpect(content().string(containsString("P102")));
    }

    @Test
    @DisplayName("Dashboard người thuê không dùng chữ của người quản lý")
    void tenantDashboardWording() throws Exception {
        mockMvc.perform(get("/dashboard").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Phòng thuê của tôi")))
            .andExpect(content().string(not(containsString("Nhà trọ được truy cập"))));
    }

    @Test
    @DisplayName("Giá trị lựa chọn không hợp lệ: thông báo tiếng Việt, không lộ lỗi Java")
    void typeMismatchIsFriendly() throws Exception {
        mockMvc.perform(post("/maintenance").header("X-User-Id", OWNER_1)
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("title", "Thử").param("description", "Thử")
                .param("cause", "WEAR_AND_TEAR"))
            .andExpect(flash().attribute("errorMessage", containsString("Giá trị nhập vào không hợp lệ")))
            .andExpect(flash().attribute("errorMessage", not(containsString("No enum constant"))));
    }
}
