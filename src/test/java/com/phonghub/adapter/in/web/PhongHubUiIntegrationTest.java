package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhongHubUiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Dashboard page renders successfully")
    void testDashboardRenders() throws Exception {
        mockMvc.perform(get("/dashboard")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Property Operations Dashboard")))
            .andExpect(content().string(containsString("Accessible Properties")));
    }

    @Test
    @DisplayName("Properties list page renders successfully")
    void testPropertiesListRenders() throws Exception {
        mockMvc.perform(get("/properties")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nha Tro Xanh - Quan 7")));
    }

    @Test
    @DisplayName("Property detail page renders room list and status badges")
    void testPropertyDetailRenders() throws Exception {
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("P101")))
            .andExpect(content().string(containsString("OCCUPIED")))
            .andExpect(content().string(containsString("MAINTENANCE")));
    }

    @Test
    @DisplayName("Room detail page renders status transition options")
    void testRoomDetailRenders() throws Exception {
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Room Information")))
            .andExpect(content().string(containsString("P101")));
    }

    @Test
    @DisplayName("Contracts list and detail pages render successfully")
    void testContractsUiRenders() throws Exception {
        mockMvc.perform(get("/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Contracts")));

        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nguyen Van A")))
            .andExpect(content().string(containsString("ACTIVE")));
    }

    @Test
    @DisplayName("Maintenance list and detail pages render successfully")
    void testMaintenanceUiRenders() throws Exception {
        mockMvc.perform(get("/maintenance")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Maintenance Tickets")));

        mockMvc.perform(get("/maintenance/" + DataSeeder.TICKET_1_ID)
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Sua voi nuoc va kiem tra may lanh")))
            .andExpect(content().string(containsString("IN_PROGRESS")));
    }

    @Test
    @DisplayName("Actor switch endpoint sets session and redirects")
    void testSwitchUser() throws Exception {
        mockMvc.perform(get("/switch-user?userId=" + LocalDemoAuthenticationAdapter.STAFF_1_ID))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }
}
