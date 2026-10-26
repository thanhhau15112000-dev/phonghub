package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PropertyTenantApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String BASE = "/api/properties/" + DataSeeder.PROP_1_ID + "/tenants";
    private static final String SEEDED_CCCD = "079201001111";

    @Autowired private MockMvc mockMvc;

    private static String body(String name, String cccd, String phone) {
        return "{\"fullName\":\"" + name + "\",\"identityCardNumber\":\"" + cccd + "\",\"phone\":\"" + phone
            + "\",\"email\":\"khach+" + phone + "@example.test\",\"permanentAddress\":\"Hà Nội\"}";
    }

    private ResultActions add(String path, String body, String user) throws Exception {
        return mockMvc.perform(post(path).header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String addOk(String name, String cccd, String phone) throws Exception {
        String response = add(BASE, body(name, cccd, phone), OWNER_1).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String list(String query, String user) throws Exception {
        return mockMvc.perform(get(BASE + query).header("X-User-Id", user))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Only managers of the property can list/add tenants; everyone else gets 403")
    void authorization() throws Exception {
        for (String ok : new String[]{OWNER_1, STAFF_1}) {
            mockMvc.perform(get(BASE).header("X-User-Id", ok)).andExpect(status().isOk());
        }
        for (String denied : new String[]{OWNER_2, STAFF_2, TECH_1, TENANT_1}) {
            mockMvc.perform(get(BASE).header("X-User-Id", denied)).andExpect(status().isForbidden());
            add(BASE, body("Người Lạ", "079300000001", "0911111111"), denied).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(BASE + "/" + DataSeeder.TENANT_RECORD_ID).header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        // Staff phụ trách được thêm tenant.
        add(BASE, body("Nhân Viên Thêm", "079300000002", "0922222222"), STAFF_1).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("List shows masked CCCD only, never the full identity number, address or email")
    void listHidesSensitiveFields() throws Exception {
        String json = list("", OWNER_1);
        assertTrue(json.contains("Nguyễn Văn A"));
        assertTrue(json.contains("****1111"));
        assertFalse(json.contains(SEEDED_CCCD), "full CCCD must not appear in the list");
        assertFalse(json.contains("tenant1@phonghub.local"));
        assertFalse(json.contains("Bến Tre"));

        String detail = mockMvc.perform(get(BASE + "/" + DataSeeder.TENANT_RECORD_ID).header("X-User-Id", STAFF_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContract").value(true))
            .andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains(SEEDED_CCCD), "full CCCD must not appear in the detail");
    }

    @Test
    @DisplayName("Adding validates input and rejects duplicate CCCD without leaking other properties' tenants")
    void addValidationAndDuplicates() throws Exception {
        add(BASE, body("", "079300000003", "0933333333"), OWNER_1).andExpect(status().isBadRequest());
        add(BASE, body("Khách", "12345", "0933333333"), OWNER_1).andExpect(status().isBadRequest());
        add(BASE, body("Khách", "079300000003", "933333333"), OWNER_1).andExpect(status().isBadRequest());

        addOk("Khách Mới", "079300000003", "0933333333");
        add(BASE, body("Khách Trùng", "079300000003", "0944444444"), OWNER_1).andExpect(status().isConflict());
        // CCCD của tenant đang ở nhà trọ khác không bị lộ, chỉ báo trùng.
        String other = "/api/properties/" + DataSeeder.PROP_2_ID + "/tenants";
        add(other, body("Khách Khác", SEEDED_CCCD, "0955555555"), STAFF_2).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A tenant added to one property is invisible to another property's owner")
    void propertyScope() throws Exception {
        String id = addOk("Khách Riêng", "079300000004", "0966666666");

        String other = "/api/properties/" + DataSeeder.PROP_2_ID + "/tenants";
        String json = mockMvc.perform(get(other).header("X-User-Id", STAFF_2))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("Khách Riêng"));
        mockMvc.perform(get("/api/tenants/" + id).header("X-User-Id", STAFF_2)).andExpect(status().isForbidden());
        mockMvc.perform(get(other + "/" + id).header("X-User-Id", STAFF_2)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tenants/" + id).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Search by name (no diacritics), phone and exact CCCD; results are paginated")
    void searchAndPagination() throws Exception {
        addOk("Trần Thị Bích", "079300000005", "0977777777");
        addOk("Lê Đức Anh", "079300000006", "0988888888");

        assertTrue(list("?q=bich", OWNER_1).contains("Trần Thị Bích"));
        assertTrue(list("?q=duc anh", OWNER_1).contains("Lê Đức Anh"));
        assertFalse(list("?q=bich", OWNER_1).contains("Lê Đức Anh"));
        assertTrue(list("?q=0988888", OWNER_1).contains("Lê Đức Anh"));
        assertTrue(list("?q=079300000005", OWNER_1).contains("Trần Thị Bích"));
        assertFalse(list("?q=079300000005", OWNER_1).contains("Lê Đức Anh"));
        mockMvc.perform(get(BASE + "?q=079399999999").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get(BASE + "?size=1&page=1").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.totalPages").value(3));
        // Kích thước trang bị chặn trên.
        mockMvc.perform(get(BASE + "?size=100000").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("Hide is a soft delete: blocked with 409 while a contract is ACTIVE, restorable otherwise")
    void hideAndRestore() throws Exception {
        mockMvc.perform(post(BASE + "/" + DataSeeder.TENANT_RECORD_ID + "/hide").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());

        String id = addOk("Khách Tạm", "079300000007", "0912345670");
        mockMvc.perform(post(BASE + "/" + id + "/hide").header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + id + "/hide").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true));

        assertFalse(list("", OWNER_1).contains("Khách Tạm"));
        assertTrue(list("?includeHidden=true", OWNER_1).contains("Khách Tạm"));
        // CCCD đã tồn tại (đang ẩn) nên không thêm trùng được.
        add(BASE, body("Khách Tạm 2", "079300000007", "0912345671"), OWNER_1).andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/" + id + "/restore").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(false));
        assertTrue(list("", OWNER_1).contains("Khách Tạm"));
    }

    @Test
    @DisplayName("Assigned staff may edit tenant info; staff of another property may not")
    void staffEdit() throws Exception {
        String id = addOk("Khách Sửa", "079300000008", "0912345680");
        String update = "{\"fullName\":\"Khách Đã Sửa\",\"phone\":\"0912345681\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tenants/" + id)
                .header("X-User-Id", STAFF_2).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tenants/" + id)
                .header("X-User-Id", STAFF_1).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Khách Đã Sửa"));
    }
}
