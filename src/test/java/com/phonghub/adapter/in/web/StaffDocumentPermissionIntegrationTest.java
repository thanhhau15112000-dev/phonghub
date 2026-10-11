package com.phonghub.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class StaffDocumentPermissionIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String STAFF_PATH = "/api/properties/" + DataSeeder.PROP_1_ID + "/staff";
    private static final String DOCS = "/api/properties/" + DataSeeder.PROP_1_ID + "/tenants/"
        + DataSeeder.TENANT_RECORD_ID + "/documents";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Viewing identity documents is a separate staff permission, off by default for new staff")
    void documentPermissionIsSeparate() throws Exception {
        // Staff mẫu giữ quyền cũ (xem được).
        mockMvc.perform(get(DOCS).header("X-User-Id", STAFF_1)).andExpect(status().isOk());

        // Staff mới: được cấp quyền hợp đồng nhưng chưa cấp quyền xem giấy tờ.
        mockMvc.perform(post(STAFF_PATH).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"staff2\",\"canManageContracts\":true}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.canViewDocuments").value(false));
        mockMvc.perform(get(DOCS).header("X-User-Id", STAFF_2)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/tenants").header("X-User-Id", STAFF_2))
            .andExpect(status().isOk());

        mockMvc.perform(put(STAFF_PATH + "/" + STAFF_2).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"canManageContracts\":true,\"canViewDocuments\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canViewDocuments").value(true));
        mockMvc.perform(get(DOCS).header("X-User-Id", STAFF_2)).andExpect(status().isOk());

        // Owner thu hồi: có hiệu lực ngay, kể cả với quyền hợp đồng vẫn còn.
        mockMvc.perform(put(STAFF_PATH + "/" + STAFF_2).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"canManageContracts\":true}"))
            .andExpect(status().isOk());
        mockMvc.perform(get(DOCS).header("X-User-Id", STAFF_2)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Technicians never get the documents permission")
    void technicianCannotGetDocumentPermission() throws Exception {
        mockMvc.perform(put(STAFF_PATH + "/" + LocalDemoAuthenticationAdapter.TECH_1_ID).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"canViewDocuments\":true}"))
            .andExpect(status().isBadRequest());
    }
}
