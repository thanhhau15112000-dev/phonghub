package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Danh sách thả xuống chọn nhân sự và tài khoản người thuê (#249): quyền, che liên hệ, tìm kiếm, chọn theo userId. */
class UserPickerIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final UUID STAFF_2_ID = LocalDemoAuthenticationAdapter.STAFF_2_ID;
    private static final String STAFF_API = "/api/properties/" + DataSeeder.PROP_1_ID + "/staff";
    private static final String STAFF_PAGE = "/properties/" + DataSeeder.PROP_1_ID + "/staff";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryUserRepository users;
    @Autowired private InMemoryTenantRepository tenants;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignments;

    private ResultActions staffCandidates(String query, String actor) throws Exception {
        return mockMvc.perform(get(STAFF_API + "/candidates").param("q", query).header("X-User-Id", actor));
    }

    private ResultActions assignById(UUID userId, String actor) throws Exception {
        return mockMvc.perform(post(STAFF_API).header("X-User-Id", actor).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + userId + "\",\"canCollectPayment\":true}"));
    }

    private User saveUser(String username, String fullName, String phone, UserRole role, User.UserStatus status) {
        return users.save(new User(UUID.randomUUID(), username, username + "@picker.test", fullName, phone, role, status,
            false, Instant.now()));
    }

    /** Hồ sơ người thuê của nhà trọ 1 chưa có tài khoản app. */
    private Tenant profileWithoutAccount() {
        Tenant tenant = tenants.save(Tenant.create(null, "Lê Thị Hồ Sơ", "079201009999", "0907770001",
            "hoso@picker.test", null));
        tenants.linkToProperty(tenant.id(), DataSeeder.PROP_1_ID, LocalDemoAuthenticationAdapter.OWNER_1_ID);
        return tenant;
    }

    @Test
    @DisplayName("Staff candidates: only the property owner and admin may list them; others 403 (anonymous 401: ProductionAuthSecurityTest)")
    void staffCandidatesAuthorization() throws Exception {
        for (String allowed : new String[]{OWNER_1, ADMIN}) {
            staffCandidates("", allowed).andExpect(status().isOk());
        }
        for (String denied : new String[]{OWNER_2, STAFF_1, TECH_1, TENANT_1}) {
            staffCandidates("", denied).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Staff candidates list every active STAFF/TECHNICIAN account except those already at this property, with masked contact only")
    void staffCandidatesContent() throws Exception {
        User tech2 = saveUser("tech2", "Kỹ thuật viên Hai", "0911222333", UserRole.TECHNICIAN, User.UserStatus.ACTIVE);
        saveUser("staffkhoa", "Nhân viên Bị Khóa", "0911222444", UserRole.STAFF, User.UserStatus.SUSPENDED);

        String body = staffCandidates("", OWNER_1).andExpect(status().isOk())
            // staff2 làm cho chủ trọ 2 vẫn hiện; staff1, tech1 đã ở nhà trọ 1 thì ẩn; nhân viên trước kỹ thuật viên
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].userId").value(STAFF_2_ID.toString()))
            .andExpect(jsonPath("$[0].role").value("STAFF"))
            .andExpect(jsonPath("$[0].fullName").value("Nhân viên Tân Bình"))
            .andExpect(jsonPath("$[0].maskedContact").value("st***@phonghub.local"))
            .andExpect(jsonPath("$[1].userId").value(tech2.id().toString()))
            .andExpect(jsonPath("$[1].role").value("TECHNICIAN"))
            .andReturn().getResponse().getContentAsString();
        // Không lộ tên đăng nhập, email/SĐT đầy đủ hay nhà trọ người đó đang làm
        assertFalse(body.contains("staff2@phonghub.local"));
        assertFalse(body.contains("0900000003"));
        assertFalse(body.contains("username"));
        assertFalse(body.contains(DataSeeder.PROP_2_ID.toString()));
        assertFalse(body.contains("Bị Khóa"));
        assertFalse(body.contains("Chủ trọ"));
        assertFalse(body.contains("Nguyễn Văn A"));
    }

    @Test
    @DisplayName("Staff candidates search by name without accents, username, email and the last 4 phone digits")
    void staffCandidatesSearch() throws Exception {
        for (String query : new String[]{"tan binh", "TÂN BÌNH", "staff2", "staff2@phonghub", "0003"}) {
            staffCandidates(query, OWNER_1).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(STAFF_2_ID.toString()));
        }
        staffCandidates("khong-co-ai", OWNER_1).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // Một chữ số giữa số điện thoại không đủ để dò
        staffCandidates("00000", OWNER_1).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Assigning by userId: picked candidate is assigned; already assigned is 409; ids outside the list are refused")
    void assignByUserId() throws Exception {
        assignById(STAFF_2_ID, OWNER_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(STAFF_2_ID.toString()))
            .andExpect(jsonPath("$.canCollectPayment").value(true));
        assertTrue(assignments.find(STAFF_2_ID, DataSeeder.PROP_1_ID).isPresent());
        staffCandidates("", OWNER_1).andExpect(jsonPath("$.length()").value(0));
        assignById(STAFF_2_ID, OWNER_1).andExpect(status().isConflict());

        User locked = saveUser("staffkhoa", "Nhân viên Bị Khóa", "0911222444", UserRole.STAFF, User.UserStatus.SUSPENDED);
        for (UUID outside : new UUID[]{locked.id(), LocalDemoAuthenticationAdapter.TENANT_1_ID,
            LocalDemoAuthenticationAdapter.OWNER_2_ID, LocalDemoAuthenticationAdapter.ADMIN_ID, UUID.randomUUID()}) {
            assignById(outside, OWNER_1).andExpect(status().isBadRequest());
            assertFalse(assignments.find(outside, DataSeeder.PROP_1_ID).isPresent());
        }
        // Chủ trọ khác không phân công vào nhà trọ không phải của mình, kể cả khi gửi đúng userId
        assignById(STAFF_2_ID, OWNER_2).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can use both ways: pick from the list or type the username")
    void adminUsesBothWays() throws Exception {
        assignById(STAFF_2_ID, ADMIN).andExpect(status().isCreated());
        User tech2 = saveUser("tech2", "Kỹ thuật viên Hai", "0911222333", UserRole.TECHNICIAN, User.UserStatus.ACTIVE);
        mockMvc.perform(post(STAFF_API).header("X-User-Id", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"tech2\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(tech2.id().toString()));
        mockMvc.perform(post(STAFF_API).header("X-User-Id", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Roster page shows the picker; the form accepts a picked userId or the typed username as before")
    void rosterPageForm() throws Exception {
        String html = mockMvc.perform(get(STAFF_PAGE).header("X-User-Id", OWNER_1)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("user-picker"));
        assertTrue(html.contains("/api/properties/" + DataSeeder.PROP_1_ID + "/staff/candidates"));
        assertTrue(html.contains("name=\"usernameOrEmail\""));

        mockMvc.perform(post(STAFF_PAGE).header("X-User-Id", OWNER_1).param("userId", STAFF_2_ID.toString())
                .param("usernameOrEmail", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", "Đã phân công nhân sự vào nhà trọ."));
        assertTrue(assignments.find(STAFF_2_ID, DataSeeder.PROP_1_ID).isPresent());

        saveUser("tech2", "Kỹ thuật viên Hai", "0911222333", UserRole.TECHNICIAN, User.UserStatus.ACTIVE);
        mockMvc.perform(post(STAFF_PAGE).header("X-User-Id", OWNER_1).param("userId", "")
                .param("usernameOrEmail", "tech2"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", "Đã phân công nhân sự vào nhà trọ."));
        mockMvc.perform(post(STAFF_PAGE).header("X-User-Id", OWNER_1).param("usernameOrEmail", "khong-ton-tai"))
            .andExpect(flash().attribute("errorMessage",
                "Không tìm thấy tài khoản nhân viên hoặc kỹ thuật viên đang hoạt động với thông tin này"));
    }

    @Test
    @DisplayName("Tenant account candidates: active TENANT accounts without a profile, masked, scoped to the property")
    void tenantAccountCandidates() throws Exception {
        Tenant profile = profileWithoutAccount();
        User free = saveUser("nguoithuemoi", "Phạm Người Thuê", "0933444555", UserRole.TENANT, User.UserStatus.ACTIVE);
        saveUser("thuebikhoa", "Người Thuê Bị Khóa", "0933444666", UserRole.TENANT, User.UserStatus.SUSPENDED);
        String url = "/api/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + profile.id() + "/account-candidates";

        String body = mockMvc.perform(get(url).header("X-User-Id", OWNER_1)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].userId").value(free.id().toString()))
            .andExpect(jsonPath("$[0].role").value("TENANT"))
            .andExpect(jsonPath("$[0].maskedContact").value("ng***@picker.test"))
            .andReturn().getResponse().getContentAsString();
        // tenant1 đã gắn hồ sơ, tài khoản bị khóa và nhân sự không hiện; không lộ email/SĐT đầy đủ
        assertFalse(body.contains("Nguyễn Văn A"));
        assertFalse(body.contains("Bị Khóa"));
        assertFalse(body.contains("nguoithuemoi@picker.test"));
        assertFalse(body.contains("0933444555"));
        mockMvc.perform(get(url).param("q", "4555").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(url).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(get(url).header("X-User-Id", TENANT_1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Linking a tenant account by picked userId works; ids outside the list are refused; email still works")
    void linkTenantAccountByUserId() throws Exception {
        Tenant profile = profileWithoutAccount();
        User free = saveUser("nguoithuemoi", "Phạm Người Thuê", "0933444555", UserRole.TENANT, User.UserStatus.ACTIVE);
        String link = "/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + profile.id() + "/link-account";

        // Tài khoản ngoài danh sách: đã gắn hồ sơ khác, nhân viên
        mockMvc.perform(post(link).header("X-User-Id", OWNER_1)
                .param("userId", LocalDemoAuthenticationAdapter.TENANT_1_ID.toString()))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(link).header("X-User-Id", OWNER_1).param("userId", STAFF_2_ID.toString()))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(null, tenants.findById(profile.id()).orElseThrow().userId());

        mockMvc.perform(post(link).header("X-User-Id", OWNER_1).param("userId", free.id().toString())
                .param("email", "hoso@picker.test"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", "Đã liên kết tài khoản người thuê đã chọn."));
        assertEquals(free.id(), tenants.findById(profile.id()).orElseThrow().userId());
        String url = "/api/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + profile.id() + "/account-candidates";
        mockMvc.perform(get(url).header("X-User-Id", OWNER_1)).andExpect(jsonPath("$.length()").value(0));

        Tenant second = tenants.save(Tenant.create(null, "Hồ Sơ Thứ Hai", "079201008888", "0907770002",
            "hai@picker.test", null));
        tenants.linkToProperty(second.id(), DataSeeder.PROP_1_ID, LocalDemoAuthenticationAdapter.OWNER_1_ID);
        User byEmail = saveUser("theoemail", "Người Theo Email", "0933444777", UserRole.TENANT, User.UserStatus.ACTIVE);
        mockMvc.perform(post("/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + second.id() + "/link-account")
                .header("X-User-Id", OWNER_1).param("email", "theoemail@picker.test"))
            .andExpect(flash().attribute("successMessage", "Đã liên kết tài khoản người thuê theo email."));
        assertEquals(byEmail.id(), tenants.findById(second.id()).orElseThrow().userId());
    }

    @Test
    @DisplayName("Tenant edit page shows the account picker only while the profile has no account")
    void tenantEditPageShowsPicker() throws Exception {
        Tenant profile = profileWithoutAccount();
        String html = mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + profile.id() + "/edit")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("user-picker"));
        assertTrue(html.contains("/tenants/" + profile.id() + "/account-candidates"));
        assertTrue(html.contains("id=\"linkAccountEmail\""));
    }
}
