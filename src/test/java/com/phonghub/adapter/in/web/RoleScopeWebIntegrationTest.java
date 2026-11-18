package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * #345, #346: phạm vi dữ liệu theo vai trò. Kỹ thuật viên không xem tiền/điện nước, danh bạ chat theo quan hệ
 * trong nhà trọ. Dữ liệu mẫu: nhà trọ 1 có chủ trọ 1, nhân viên 1, kỹ thuật viên 1, người thuê 1; nhà trọ 2 có
 * nhân viên 2; chủ trọ 2 không có nhà nào ở đây.
 */
class RoleScopeWebIntegrationTest extends SeededWebTest {

    private static final String P1 = DataSeeder.PROP_1_ID.toString();
    private static final String ROOM = DataSeeder.ROOM_101_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignments;

    private String body(String url, String user) throws Exception {
        return mockMvc.perform(get(url).header("X-User-Id", user)).andReturn().getResponse().getContentAsString();
    }

    private int statusOf(String url, String user) throws Exception {
        return mockMvc.perform(get(url).header("X-User-Id", user)).andReturn().getResponse().getStatus();
    }

    private void createRate() throws Exception {
        mockMvc.perform(post("/api/properties/" + P1 + "/service-rates").header("X-User-Id", OWNER_1)
            .contentType("application/json")
            .content("{\"serviceName\":\"Điện\",\"chargeType\":\"PER_KWH\",\"pricePerUnit\":3500}"))
            .andExpect(status().isCreated());
    }

    // ---------- #346: kỹ thuật viên không xem đơn giá, chỉ số điện nước ----------

    @Test
    @DisplayName("đơn giá dịch vụ: kỹ thuật viên bị 403; chủ trọ, nhân viên và người thuê của nhà vẫn xem được")
    void serviceRatesDeniedForTechnician() throws Exception {
        createRate();
        String url = "/api/properties/" + P1 + "/service-rates";

        assertEquals(403, statusOf(url, TECH_1));
        for (String user : new String[] {OWNER_1, STAFF_1, TENANT_1}) {
            assertTrue(body(url, user).contains("\"pricePerUnit\":3500"), user);
        }
        assertEquals(403, statusOf(url, OWNER_2), "nhà trọ của người khác");
    }

    @Test
    @DisplayName("chỉ số điện nước theo phòng: kỹ thuật viên bị 403; chủ trọ, nhân viên và người thuê của phòng xem được")
    void meterReadingsDeniedForTechnician() throws Exception {
        String url = "/api/rooms/" + ROOM + "/meter-readings";

        assertEquals(403, statusOf(url, TECH_1));
        for (String user : new String[] {OWNER_1, STAFF_1, TENANT_1}) {
            assertEquals(200, statusOf(url, user), user);
        }
    }

    @Test
    @DisplayName("trang Xuất dữ liệu: kỹ thuật viên và người thuê được chuyển về trang chủ, chủ trọ và nhân viên vào được")
    void exportsPageOnlyForManagers() throws Exception {
        for (String user : new String[] {TECH_1, TENANT_1}) {
            mockMvc.perform(get("/exports").header("X-User-Id", user))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attributeExists("errorMessage"));
        }
        for (String user : new String[] {OWNER_1, STAFF_1}) {
            assertEquals(200, statusOf("/exports", user), user);
        }
    }

    @Test
    @DisplayName("giá trị ước tính và chi phí sửa tài sản bị ẩn với kỹ thuật viên và nhân viên không có quyền tiền/phòng")
    void assetMoneyHiddenFromRolesWithoutMoneyAccess() throws Exception {
        String created = mockMvc.perform(post("/api/rooms/" + ROOM + "/assets").header("X-User-Id", OWNER_1)
                .contentType("application/json")
                .content("{\"assetCode\":\"AC-01\",\"name\":\"Máy lạnh\",\"quantity\":1,\"estimatedValue\":1500000}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String assetId = json.readTree(created).path("id").asString();
        mockMvc.perform(post("/api/assets/" + assetId + "/events").header("X-User-Id", OWNER_1)
                .contentType("application/json")
                .content("{\"type\":\"REPAIRED\",\"description\":\"Nạp gas\",\"cost\":200000}"))
            .andExpect(status().isCreated());
        // Nhân viên 2 được giao nhà trọ 1 nhưng không có quyền tiền, hợp đồng hay phòng.
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), UUID.fromString(STAFF_2),
            DataSeeder.PROP_1_ID, false, false, false, false, Instant.now()));

        String listUrl = "/api/rooms/" + ROOM + "/assets";
        String eventsUrl = "/api/assets/" + assetId + "/events";
        for (String user : new String[] {OWNER_1, STAFF_1, TENANT_1}) {
            assertEquals(1500000, json.readTree(body(listUrl, user)).get(0).path("estimatedValue").asInt(), user);
            assertEquals(200000, json.readTree(body(eventsUrl, user)).get(0).path("cost").asInt(), user);
        }
        for (String user : new String[] {TECH_1, STAFF_2}) {
            assertEquals(0, json.readTree(body(listUrl, user)).get(0).path("estimatedValue").asInt(), user);
            assertEquals(0, json.readTree(body(eventsUrl, user)).get(0).path("cost").asInt(), user);
            assertFalse(body("/rooms/" + ROOM, user).contains("1.500.000"), "trang phòng: " + user);
        }
        assertTrue(body("/rooms/" + ROOM, OWNER_1).contains("1.500.000"), "chủ trọ vẫn thấy giá trị");
    }

    // ---------- #345: danh bạ chat theo quan hệ trong nhà trọ ----------

    private Set<String> contactIds(String user) throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonNode c : json.readTree(body("/api/chat/contacts", user))) {
            ids.add(c.path("userId").asString());
        }
        return ids;
    }

    private int direct(String user, String target) throws Exception {
        return mockMvc.perform(post("/api/chat/rooms/direct").header("X-User-Id", user)
                .contentType("application/json").content("{\"targetUserId\":\"" + target + "\"}"))
            .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("người thuê chỉ thấy chủ trọ, nhân viên, kỹ thuật viên của nhà mình và quản trị viên")
    void tenantContactsAreScopedToOwnProperty() throws Exception {
        Set<String> ids = contactIds(TENANT_1);

        assertEquals(Set.of(OWNER_1, STAFF_1, TECH_1, ADMIN), ids);
        assertEquals(200, direct(TENANT_1, OWNER_1));
        assertEquals(403, direct(TENANT_1, OWNER_2), "chủ trọ nhà khác");
        assertEquals(403, direct(TENANT_1, STAFF_2), "nhân viên nhà khác");
    }

    @Test
    @DisplayName("kỹ thuật viên thấy chủ trọ, nhân viên, người thuê của nhà được giao, không thấy nhà khác")
    void technicianContactsAreScoped() throws Exception {
        Set<String> ids = contactIds(TECH_1);

        assertEquals(Set.of(OWNER_1, STAFF_1, TENANT_1, ADMIN), ids);
        assertEquals(200, direct(TECH_1, TENANT_1));
        assertEquals(403, direct(TECH_1, OWNER_2));
        assertEquals(403, direct(TECH_1, STAFF_2));
    }

    @Test
    @DisplayName("nhân viên nhà này không thấy người thuê, nhân viên hay chủ trọ của nhà khác")
    void staffContactsAreScoped() throws Exception {
        Set<String> ids = contactIds(STAFF_1);

        assertTrue(ids.containsAll(Set.of(OWNER_1, TENANT_1, TECH_1, ADMIN)), ids.toString());
        assertFalse(ids.contains(OWNER_2));
        assertFalse(ids.contains(STAFF_2), "nhân viên nhà 2 chưa chung nhà trọ");
        assertEquals(403, direct(STAFF_1, OWNER_2));
    }

    @Test
    @DisplayName("chủ trọ không thấy chủ trọ khác; chủ trọ 2 chưa có nhà nên không thấy người thuê của nhà 1")
    void ownerContactsAreScoped() throws Exception {
        Set<String> owner1 = contactIds(OWNER_1);
        assertTrue(owner1.containsAll(Set.of(TENANT_1, STAFF_1, TECH_1, ADMIN)), owner1.toString());
        assertFalse(owner1.contains(OWNER_2));

        Set<String> owner2 = contactIds(OWNER_2);
        assertFalse(owner2.contains(TENANT_1));
        assertFalse(owner2.contains(STAFF_1));
        assertEquals(403, direct(OWNER_2, TENANT_1));
    }

    @Test
    @DisplayName("quản trị viên liên hệ được mọi người")
    void adminSeesEveryone() throws Exception {
        Set<String> ids = contactIds(ADMIN);

        assertTrue(ids.containsAll(Set.of(OWNER_1, OWNER_2, STAFF_1, STAFF_2, TECH_1, TENANT_1)), ids.toString());
        assertEquals(200, direct(ADMIN, OWNER_2));
    }

    // ---------- Kênh chung theo nhà trọ ----------

    private static final String GLOBAL = com.phonghub.domain.model.ChatRoom.GLOBAL_ROOM_ID.toString();

    private void postGlobal(String user, String text) throws Exception {
        mockMvc.perform(post("/api/chat/rooms/" + GLOBAL + "/messages").header("X-User-Id", user)
                .contentType("application/json").content("{\"content\":\"" + text + "\"}"))
            .andExpect(status().isOk());
    }

    private String globalMessages(String user) throws Exception {
        return body("/api/chat/rooms/" + GLOBAL + "/messages", user);
    }

    private String globalSummary(String user) throws Exception {
        for (JsonNode room : json.readTree(body("/api/chat/rooms", user))) {
            if ("GLOBAL".equals(room.path("type").asString())) {
                return room.toString();
            }
        }
        throw new AssertionError("không có kênh chung");
    }

    @Test
    @DisplayName("kênh chung: tin của nhân viên nhà 1 chỉ tới người cùng nhà trọ, không tới nhân viên và chủ trọ nhà khác")
    void globalChannelOnlyReachesSameProperty() throws Exception {
        postGlobal(STAFF_1, "Thông báo cắt nước nhà 1");
        postGlobal(STAFF_2, "Thông báo nhà 2");

        for (String user : new String[] {STAFF_1, TENANT_1, OWNER_1, TECH_1, ADMIN}) {
            assertTrue(globalMessages(user).contains("Thông báo cắt nước nhà 1"), user);
        }
        for (String user : new String[] {STAFF_2, OWNER_2}) {
            assertFalse(globalMessages(user).contains("Thông báo cắt nước nhà 1"), user);
        }
        assertFalse(globalMessages(TENANT_1).contains("Thông báo nhà 2"), "người thuê nhà 1 không thấy tin nhà 2");
        assertTrue(globalMessages(STAFF_2).contains("Thông báo nhà 2"), "người gửi luôn thấy tin của mình");
        assertTrue(globalMessages(ADMIN).contains("Thông báo nhà 2"), "quản trị viên thấy mọi tin");
    }

    @Test
    @DisplayName("kênh chung: xem trước và số chưa đọc không lộ tin của người ngoài nhà trọ")
    void globalSummaryDoesNotLeakOtherProperties() throws Exception {
        postGlobal(STAFF_1, "Bí mật nhà 1");

        String ownerTwo = globalSummary(OWNER_2);
        assertFalse(ownerTwo.contains("Bí mật nhà 1"), ownerTwo);
        assertEquals(0, json.readTree(ownerTwo).path("unreadCount").asInt());

        String tenant = globalSummary(TENANT_1);
        assertTrue(tenant.contains("Bí mật nhà 1"), tenant);
        assertEquals(1, json.readTree(tenant).path("unreadCount").asInt());
    }
}
