package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PropertyStaffUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String BASE = "/properties/" + DataSeeder.PROP_1_ID + "/staff";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Owner sees the roster page and can assign; staff is redirected away and cannot assign")
    void rosterPage() throws Exception {
        String html = mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(view().name("properties/staff"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Phân công nhân sự"));

        mockMvc.perform(post(BASE).header("X-User-Id", OWNER_1).param("usernameOrEmail", "staff2")
            .param("canManageContracts", "true")).andExpect(status().is3xxRedirection());
        assertTrue(mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString().contains("staff2"));

        mockMvc.perform(get(BASE).header("X-User-Id", STAFF_1)).andExpect(status().is3xxRedirection());
        mockMvc.perform(post(BASE).header("X-User-Id", STAFF_1).param("usernameOrEmail", "tech1"))
            .andExpect(status().is3xxRedirection());
    }
}
