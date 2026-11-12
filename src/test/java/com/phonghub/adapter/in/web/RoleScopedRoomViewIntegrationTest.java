package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mỗi vai trò chỉ thấy phần thuộc việc của mình trên trang phòng, nhà trọ và hợp đồng: giá thuê cơ bản chỉ hiện với
 * người có quyền tài chính (#326), Quản trị viên không sửa người thuê trên trang hợp đồng (#327), Kỹ thuật viên không
 * thấy biên bản hiện trạng (#328), không còn dải Chế độ demo che menu (#329).
 */
class RoleScopedRoomViewIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String PRICE_LABEL = "Giá thuê cơ bản:";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignments;
    @Autowired private InMemoryRoomRepository rooms;
    @Autowired private InMemoryTenantRepository tenants;

    @BeforeEach
    void staffTwoManagesRoomsWithoutFinance() {
        // Nhân viên 2 được giao nhà 1, có quyền quản lý phòng nhưng không có quyền thu tiền hay quản lý hợp đồng
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), LocalDemoAuthenticationAdapter.STAFF_2_ID,
            DataSeeder.PROP_1_ID, false, false, false, true, Instant.now()));
    }

    private String html(String path, String userId) throws Exception {
        return mockMvc.perform(get(path).header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String json(String path, String userId) throws Exception {
        return mockMvc.perform(get(path).header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("#326 Trang phòng: chỉ chủ trọ và nhân viên có quyền tài chính thấy giá thuê cơ bản")
    void roomPageShowsBasePriceOnlyToFinanceRoles() throws Exception {
        String room = "/rooms/" + DataSeeder.ROOM_101_ID;
        assertTrue(html(room, OWNER_1).contains(PRICE_LABEL));
        assertTrue(html(room, STAFF_1).contains(PRICE_LABEL));
        for (String user : new String[] {ADMIN, TECH_1, STAFF_2}) {
            String page = html(room, user);
            assertFalse(page.contains(PRICE_LABEL), user);
            assertFalse(page.contains("3.500.000"), user);
        }
    }

    @Test
    @DisplayName("#326 Trang nhà trọ: cột giá và bộ lọc giá ẩn với người không có quyền tài chính")
    void propertyPageHidesPriceColumnAndFilter() throws Exception {
        String property = "/properties/" + DataSeeder.PROP_1_ID;
        String owner = html(property, OWNER_1);
        assertTrue(owner.contains("Giá cơ bản</th>"));
        assertTrue(owner.contains("id=\"filterMinPrice\""));
        for (String user : new String[] {ADMIN, STAFF_2}) {
            String page = html(property, user);
            assertFalse(page.contains("Giá cơ bản</th>"), user);
            assertFalse(page.contains("id=\"filterMinPrice\""), user);
            assertFalse(page.contains("3.500.000"), user);
        }
        // Lọc theo giá qua đường dẫn bị bỏ qua: vẫn trả trang, không đoán được giá
        assertFalse(html(property + "?minPrice=3600000", ADMIN).contains("3.800.000"));
    }

    @Test
    @DisplayName("#326 API phòng: không trả giá cho người không có quyền tài chính, lọc theo giá bị từ chối")
    void roomApiHidesBasePrice() throws Exception {
        String room = "/api/rooms/" + DataSeeder.ROOM_101_ID;
        assertTrue(json(room, OWNER_1).contains("\"basePrice\":3500000"));
        for (String user : new String[] {ADMIN, TECH_1, STAFF_2}) {
            assertFalse(json(room, user).contains("3500000"), user);
            assertFalse(json("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms", user).contains("3500000"), user);
        }
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms").param("minPrice", "3600000")
                .header("X-User-Id", ADMIN))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/rooms").param("minPrice", "3600000")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("#326 Nhân viên quản lý phòng không có quyền tài chính: form sửa không lộ giá, để trống thì giữ giá cũ")
    void staffWithoutFinanceEditsRoomWithoutSeeingOrChangingPrice() throws Exception {
        String page = html("/rooms/" + DataSeeder.ROOM_104_ID, STAFF_2);
        assertTrue(page.contains("Để trống để giữ giá hiện tại"));
        assertFalse(page.contains("value=\"4000000"));

        Room before = rooms.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        mockMvc.perform(post("/rooms/" + DataSeeder.ROOM_104_ID + "/edit")
                .header("X-User-Id", STAFF_2)
                .param("roomNumber", "P104")
                .param("floor", "1")
                .param("areaSqm", "31")
                .param("maxOccupants", "3")
                .param("version", Long.toString(before.getVersion())))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        Room after = rooms.findById(DataSeeder.ROOM_104_ID).orElseThrow();
        assertEquals(0, new BigDecimal("4000000").compareTo(after.getBasePrice()));
        assertEquals(0, new BigDecimal("31").compareTo(after.getAreaSqm()));
    }

    @Test
    @DisplayName("#327 Quản trị viên không thấy nút và không sửa được người thuê trên trang hợp đồng")
    void adminCannotEditTenantFromContractPage() throws Exception {
        String contract = "/contracts/" + DataSeeder.CONTRACT_1_ID;
        assertFalse(html(contract, ADMIN).contains("id=\"editPrimaryTenantBtn\""));
        assertFalse(html(contract, ADMIN).contains("id=\"editTenantModal\""));
        assertTrue(html(contract, OWNER_1).contains("id=\"editPrimaryTenantBtn\""));

        String nameBefore = tenants.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow().fullName();
        mockMvc.perform(post(contract + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/edit")
                .header("X-User-Id", ADMIN)
                .param("fullName", "Tên do quản trị viên sửa")
                .param("identityCardNumber", "079201001111")
                .param("phone", "0901234567"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(contract))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(nameBefore, tenants.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow().fullName());
    }

    @Test
    @DisplayName("#328 Kỹ thuật viên: mô tả không nhắc hợp đồng, không thấy biên bản hiện trạng")
    void technicianRoomPageShowsOnlyRepairRelevantParts() throws Exception {
        String room = "/rooms/" + DataSeeder.ROOM_101_ID;
        String tech = html(room, TECH_1);
        assertTrue(tech.contains("Thông tin phòng, tài sản và yêu cầu bảo trì."));
        assertFalse(tech.contains("hợp đồng đang thuê"));
        assertFalse(tech.contains("id=\"initial-condition\""));
        assertTrue(tech.contains("Các yêu cầu bảo trì của phòng này"));

        String owner = html(room, OWNER_1);
        assertTrue(owner.contains("id=\"initial-condition\""));
        assertTrue(owner.contains("hợp đồng đang thuê"));
    }

    @Test
    @DisplayName("#329 Không còn dải Chế độ demo trong header")
    void demoBannerIsGone() throws Exception {
        assertFalse(html("/rooms/" + DataSeeder.ROOM_101_ID, OWNER_1).contains("Chế độ demo: không kiểm tra mật khẩu"));
    }
}
