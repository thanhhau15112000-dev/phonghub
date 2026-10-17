package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class PropertyTenantUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String BASE = "/properties/" + DataSeeder.PROP_1_ID + "/tenants";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("The tenant management page renders masked CCCD and never the full number")
    void listRendersMaskedIdentity() throws Exception {
        String html = mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(view().name("tenants/list"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Nguyễn Văn A"));
        assertTrue(html.contains("****1111"));
        assertFalse(html.contains("079201001111"));
    }

    @Test
    @DisplayName("Add form renders and a post creates the tenant; staff of another property is bounced")
    void addFlow() throws Exception {
        mockMvc.perform(get(BASE + "/new").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(view().name("tenants/form"));
        mockMvc.perform(post(BASE).header("X-User-Id", OWNER_1)
                .param("fullName", "Khách UI").param("identityCardNumber", "079300000009").param("phone", "0912345690"))
            .andExpect(status().is3xxRedirection());
        assertTrue(mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString().contains("Khách UI"));

        // Staff nhà trọ khác và tenant không vào được trang quản lý.
        mockMvc.perform(get(BASE).header("X-User-Id", STAFF_2)).andExpect(status().is3xxRedirection());
        mockMvc.perform(post(BASE).header("X-User-Id", STAFF_2)
                .param("fullName", "Kẻ Lạ").param("identityCardNumber", "079300000010").param("phone", "0912345691"))
            .andExpect(status().is3xxRedirection());
        assertFalse(mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString().contains("Kẻ Lạ"));
        mockMvc.perform(get(BASE).header("X-User-Id", TENANT_1)).andExpect(status().is3xxRedirection());
    }
}
