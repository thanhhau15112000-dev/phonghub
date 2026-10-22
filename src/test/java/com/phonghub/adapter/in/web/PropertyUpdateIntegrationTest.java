package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertyUpdateIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String URL = "/api/properties/" + DataSeeder.PROP_1_ID;
    private static final String ROOMS = URL + "/rooms";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditPort auditPort;

    @BeforeEach
    void clearAudit() {
        ((InMemoryAuditAdapter) auditPort).clear();
    }

    private String body(String name, String address, String description) {
        return "{\"name\":\"" + name + "\",\"address\":\"" + address + "\",\"description\":\"" + description + "\"}";
    }

    private String currentName() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(mockMvc.perform(get(URL).header("X-User-Id", ADMIN))
            .andReturn().getResponse().getContentAsString(), "$.name");
    }

    private String currentAddress() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(mockMvc.perform(get(URL).header("X-User-Id", ADMIN))
            .andReturn().getResponse().getContentAsString(), "$.address");
    }

    @Test
    @DisplayName("Owner changes the description: saved, status stays VERIFIED, rooms can still be created")
    void minorChangeKeepsApproval() throws Exception {
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(body(currentName(), currentAddress(), "Mô tả mới")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Mô tả mới"))
            .andExpect(jsonPath("$.approvalStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("Owner renames a verified property: stays VERIFIED; changing the address needs approval again and blocks new rooms until the admin approves")
    void addressChangeNeedsReapproval() throws Exception {
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(body("Nhà trọ đổi tên", currentAddress(), "x")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Nhà trọ đổi tên"))
            .andExpect(jsonPath("$.approvalStatus").value("VERIFIED"));
        mockMvc.perform(post(ROOMS).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"P899\",\"floor\":1,\"basePrice\":1000000,\"maxOccupants\":2}"))
            .andExpect(status().isCreated());

        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(body("Nhà trọ đổi tên", "99 Đường mới", "x")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        mockMvc.perform(post(ROOMS).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"P900\",\"floor\":1,\"basePrice\":1000000,\"maxOccupants\":2}"))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_1_ID + "/verify").header("X-User-Id", ADMIN))
            .andExpect(status().isOk());
        mockMvc.perform(post(ROOMS).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomNumber\":\"P901\",\"floor\":1,\"basePrice\":1000000,\"maxOccupants\":2}"))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Changing the address also triggers re-approval; admin edits never change the status")
    void addressChangeAndAdminEdit() throws Exception {
        mockMvc.perform(patch(URL).header("X-User-Id", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(body("Tên do admin sửa", currentAddress(), "")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("VERIFIED"));
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(body(currentName(), "99 Đường mới", "")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("PENDING"));
    }

    @Test
    @DisplayName("A rejected property is resubmitted when its owner edits it and the rejection reason is cleared")
    void rejectedPropertyIsResubmitted() throws Exception {
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content(body("Cần duyệt", "Địa chỉ cần xác minh", "")));
        mockMvc.perform(post("/api/admin/properties/" + DataSeeder.PROP_1_ID + "/reject").header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sai địa chỉ\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("REJECTED"));

        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(body("Cần duyệt", "Địa chỉ đã sửa", "")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
            .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    @DisplayName("Only the owner of the property and admins may edit; invalid input returns 400; unknown property 404")
    void accessAndValidation() throws Exception {
        for (String outsider : new String[]{OWNER_2, STAFF_1, TECH_1, TENANT_1}) {
            mockMvc.perform(patch(URL).header("X-User-Id", outsider).contentType(MediaType.APPLICATION_JSON)
                    .content(body("Chiếm quyền", "x", "")))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content(body(" ", "x", ""))).andExpect(status().isBadRequest());
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"a\",\"address\":\"b\",\"totalRooms\":-1}")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/properties/" + java.util.UUID.randomUUID()).header("X-User-Id", ADMIN)
            .contentType(MediaType.APPLICATION_JSON).content(body("a", "b", ""))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The audit lists changed field names and status transition without personal or free-text values")
    void auditHasNoValues() throws Exception {
        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content(body("Tên riêng tư XYZ", "Địa chỉ riêng ABC", "")));

        var event = ((InMemoryAuditAdapter) auditPort).getRecordedEvents().stream()
            .filter(e -> e.action().equals("PROPERTY_UPDATED")).findFirst().orElseThrow();
        assertEquals(java.util.List.of("name", "address", "description"), event.details().get("changedFields"));
        assertEquals("VERIFIED", event.details().get("oldStatus"));
        assertEquals("PENDING", event.details().get("newStatus"));
        assertEquals(false, event.details().toString().contains("XYZ"));
        assertEquals(false, event.details().toString().contains("ABC"));
    }

    @Test
    @DisplayName("No change means no audit event and no status change")
    void noChangeIsANoop() throws Exception {
        mockMvc.perform(get(URL).header("X-User-Id", ADMIN));
        String name = currentName();
        String address = currentAddress();
        String description = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(get(URL).header("X-User-Id", ADMIN))
            .andReturn().getResponse().getContentAsString(), "$.description");

        mockMvc.perform(patch(URL).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(body(name, address, description == null ? "" : description)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("VERIFIED"));
        assertEquals(0, ((InMemoryAuditAdapter) auditPort).getRecordedEvents().stream()
            .filter(e -> e.action().equals("PROPERTY_UPDATED")).count());
    }

    @Test
    @DisplayName("The property page offers the edit form to its owner and admins only, and the form works")
    void uiForm() throws Exception {
        String page = "/properties/" + DataSeeder.PROP_1_ID;
        mockMvc.perform(get(page).header("X-User-Id", OWNER_1)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Sửa thông tin")));
        mockMvc.perform(get(page).header("X-User-Id", STAFF_1)).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("editPropertyBtn"))));

        mockMvc.perform(post(page + "/edit").header("X-User-Id", OWNER_1).param("name", "Tên mới từ form")
                .param("address", currentAddress()).param("description", "d"))
            .andExpect(flash().attribute("successMessage", "Đã cập nhật thông tin nhà trọ."));
        mockMvc.perform(post(page + "/edit").header("X-User-Id", OWNER_1).param("name", "Tên mới từ form")
                .param("address", "Địa chỉ mới từ form").param("description", "d"))
            .andExpect(flash().attribute("successMessage",
                "Đã cập nhật. Đổi địa chỉ cần được quản trị viên duyệt lại."));
        mockMvc.perform(post(page + "/edit").header("X-User-Id", STAFF_1).param("name", "x").param("address", "y"))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(page + "/edit").header("X-User-Id", OWNER_1).param("name", " ").param("address", "y"))
            .andExpect(flash().attributeExists("errorMessage"));
    }
}
