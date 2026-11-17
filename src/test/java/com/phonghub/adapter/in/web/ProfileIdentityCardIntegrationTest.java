package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** #244: người thuê tải ảnh CCCD ở Hồ sơ cá nhân trước khi có hợp đồng; hợp đồng lập sau dùng luôn ảnh đó. */
class ProfileIdentityCardIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryTenantDocumentRepository documents;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired @Qualifier("demoActorPort") private DemoActorPort demoActors;

    @BeforeEach
    @AfterEach
    void clearDocuments() {
        documents.clear();
    }

    /** Chế độ demo chỉ nhận ra tài khoản mẫu qua X-User-Id; đăng ký tài khoản người thuê vừa tạo để đóng vai họ. */
    private String signIn(Tenant tenant) {
        demoActors.setCurrentUser(new CurrentUser(tenant.userId(), tenant.email(), tenant.fullName(), UserRole.TENANT));
        return tenant.userId().toString();
    }

    private void uploadFromProfile(String tenantUser) throws Exception {
        mockMvc.perform(multipart("/account/profile/identity-card")
                .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
                .file(new MockMultipartFile("back", "back.png", "image/png", PNG))
                .header("X-User-Id", tenantUser))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/profile#cccd"))
            .andExpect(flash().attributeExists("identityCardSuccessMessage"));
    }

    /** Chủ trọ lập hợp đồng nháp ở phòng trống P104 rồi gửi biên bản check-in đủ ảnh cho người thuê. */
    private UUID contractWithSentCheckIn(Tenant tenant) throws Exception {
        mockMvc.perform(post("/contracts").header("X-User-Id", OWNER_1)
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantId", tenant.id().toString())
                .param("depositAmount", "4000000").param("rentAmount", "4000000")
                .param("startDate", "2026-11-01").param("endDate", "2027-10-31").param("paymentDay", "5"))
            .andExpect(status().is3xxRedirection());
        UUID contractId = contracts.findAll().stream()
            .filter(contract -> contract.getPrimaryTenantId().equals(tenant.id()))
            .findFirst().orElseThrow().getId();

        String checkIn = "/api/contracts/" + contractId + "/handover";
        mockMvc.perform(post("/api/contracts/" + contractId + "/check-in").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialElectricMeter\":100,\"initialWaterMeter\":10,"
                    + "\"roomCondition\":\"Phòng sạch, đủ tài sản\",\"keysProvided\":\"2 chìa khóa\"}"))
            .andExpect(status().isCreated());
        for (String kind : new String[] {"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(checkIn + "/photos")
                    .file(new MockMultipartFile("file", "p.png", "image/png", PNG)).param("kind", kind)
                    .header("X-User-Id", OWNER_1))
                .andExpect(status().isCreated());
        }
        mockMvc.perform(post(checkIn + "/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        return contractId;
    }

    @Test
    @DisplayName("CCCD uploaded on the profile before any contract is used by the later contract and check-in")
    void profileUploadIsUsedByTheLaterContract() throws Exception {
        Tenant newcomer = accountTenant("Lê Văn C", "079300004567", "0912345670");
        String tenantUser = signIn(newcomer);

        mockMvc.perform(get("/account/profile").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"cccd\"")))
            .andExpect(content().string(containsString("Chưa có ảnh CCCD")));

        uploadFromProfile(tenantUser);

        assertNull(documents.findByTenantIdAndType(newcomer.id(), DocumentType.CCCD).orElseThrow().propertyId(),
            "chưa có hợp đồng nên giấy tờ không gắn nhà trọ nào");
        mockMvc.perform(get("/account/profile").header("X-User-Id", tenantUser))
            .andExpect(content().string(containsString("Đã có ảnh CCCD")))
            .andExpect(content().string(containsString("****4567")))
            .andExpect(content().string(not(containsString("079300004567"))));

        UUID contractId = contractWithSentCheckIn(newcomer);

        // Chủ trọ thấy CCCD ngay ở trang giấy tờ của nhà trọ, không ai phải tải lại
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + newcomer.id() + "/documents")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("****4567")));

        mockMvc.perform(get("/contracts/" + contractId + "/handover").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đã có ảnh CCCD trong Hồ sơ cá nhân")));
        mockMvc.perform(post("/api/contracts/" + contractId + "/handover/confirm").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Without a CCCD the check-in page points the tenant to the profile and confirming is refused")
    void checkInWithoutCccdPointsToTheProfile() throws Exception {
        Tenant newcomer = accountTenant("Lê Văn E", "079300007890", "0912345671");
        String tenantUser = signIn(newcomer);
        UUID contractId = contractWithSentCheckIn(newcomer);

        mockMvc.perform(get("/contracts/" + contractId + "/handover").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tải ảnh CCCD ở Hồ sơ cá nhân")))
            .andExpect(content().string(containsString("/account/profile#cccd")));
        mockMvc.perform(post("/contracts/" + contractId + "/handover/confirm").header("X-User-Id", tenantUser))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", containsString("Hồ sơ cá nhân")));

        uploadFromProfile(tenantUser);
        mockMvc.perform(post("/contracts/" + contractId + "/handover/confirm").header("X-User-Id", tenantUser))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("Only tenants have the CCCD section; other roles are refused when posting the form")
    void onlyTenantsUploadFromTheProfile() throws Exception {
        mockMvc.perform(get("/account/profile").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"cccd\""))));

        mockMvc.perform(multipart("/account/profile/identity-card")
                .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
                .header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("identityCardErrorMessage"));
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
    }
}
