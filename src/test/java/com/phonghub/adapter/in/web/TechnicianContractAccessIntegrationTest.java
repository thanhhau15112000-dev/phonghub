package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kỹ thuật viên chỉ xử lý phiếu bảo trì nên không xem hợp đồng thuê và người thuê (#312). Nút mở biên bản trên trang
 * hợp đồng chỉ hiện với người mở được biên bản.
 */
class TechnicianContractAccessIntegrationTest extends SeededWebTest {

    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String CONTRACT_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryHandoverRepository handovers;

    @Test
    @DisplayName("API: kỹ thuật viên nhận 403 khi xem chi tiết hoặc danh sách hợp đồng")
    void technicianIsDeniedContractApi() throws Exception {
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", TECH_1))
            .andExpect(status().isForbidden())
            .andExpect(content().string(containsString(AuthorizationService.TECHNICIAN_CONTRACT_DENIED)));
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/contracts").header("X-User-Id", TECH_1))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Giao diện: kỹ thuật viên không có menu hợp đồng và bị chuyển về trang bảo trì")
    void technicianIsRedirectedAwayFromContractPages() throws Exception {
        mockMvc.perform(get("/contracts").header("X-User-Id", TECH_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/maintenance"))
            .andExpect(flash().attribute("errorMessage", AuthorizationService.TECHNICIAN_CONTRACT_DENIED));
        mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", TECH_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/maintenance"));
        mockMvc.perform(get("/maintenance").header("X-User-Id", TECH_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("href=\"/contracts\""))));
    }

    @Test
    @DisplayName("Trang phòng, nhà trọ: kỹ thuật viên không thấy hợp đồng và tên người thuê; chủ trọ vẫn thấy")
    void roomAndPropertyPagesHideContractAndTenantsFromTechnician() throws Exception {
        String room = "/rooms/" + DataSeeder.ROOM_101_ID;
        String property = "/properties/" + DataSeeder.PROP_1_ID;
        mockMvc.perform(get(room).header("X-User-Id", TECH_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"room-contract\""))))
            .andExpect(content().string(not(containsString(DataSeeder.CONTRACT_1_ID.toString()))));
        mockMvc.perform(get(property).header("X-User-Id", TECH_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Nguyễn Văn A"))));

        mockMvc.perform(get(room).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"room-contract\"")))
            .andExpect(content().string(containsString(DataSeeder.CONTRACT_1_ID.toString())));
        mockMvc.perform(get(property).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nguyễn Văn A")));
    }

    @Test
    @DisplayName("Nút mở biên bản chỉ hiện với người mở được biên bản")
    void handoverLinkOnlyForUsersWhoCanOpenIt() throws Exception {
        // Hợp đồng 1 đang ở nên trang hiện tiến độ trả phòng và nút mở biên bản check-out
        if (handovers.find(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_OUT).isEmpty()) {
            handovers.save(Handover.draft(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_OUT, BigDecimal.ZERO,
                BigDecimal.ZERO, "Tình trạng khi trả phòng", null, LocalDemoAuthenticationAdapter.OWNER_1_ID));
        }
        String link = "href=\"" + CONTRACT_PAGE + "/check-out\"";

        mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(link)));
        mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(link))));
    }
}
