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

/** #252: người thuê tải GPLX ở Hồ sơ cá nhân trước khi có hợp đồng; hợp đồng lập sau và biên bản check-in dùng luôn. */
class ProfileDriverLicenseIntegrationTest extends SeededWebTest {

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

    private String signIn(Tenant tenant) {
        demoActors.setCurrentUser(new CurrentUser(tenant.userId(), tenant.email(), tenant.fullName(), UserRole.TENANT));
        return tenant.userId().toString();
    }

    private void uploadGplxFromProfile(String tenantUser, String licenseNumber) throws Exception {
        var builder = multipart("/account/profile/driver-license")
            .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
            .file(new MockMultipartFile("back", "back.png", "image/png", PNG))
            .header("X-User-Id", tenantUser);
        if (licenseNumber != null) {
            builder.param("licenseNumber", licenseNumber);
        }
        mockMvc.perform(builder)
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/profile#gplx"))
            .andExpect(flash().attributeExists("driverLicenseSuccessMessage"));
    }

    private void uploadCccdFromProfile(String tenantUser) throws Exception {
        mockMvc.perform(multipart("/account/profile/identity-card")
                .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
                .file(new MockMultipartFile("back", "back.png", "image/png", PNG))
                .header("X-User-Id", tenantUser))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/profile#cccd"))
            .andExpect(flash().attributeExists("identityCardSuccessMessage"));
    }

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
    @DisplayName("GPLX uploaded on the profile before any contract is used by the later contract and check-in")
    void profileUploadIsUsedByTheLaterContract() throws Exception {
        Tenant newcomer = accountTenant("Nguyễn Văn G", "079300005555", "0912345672");
        String tenantUser = signIn(newcomer);

        mockMvc.perform(get("/account/profile").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"gplx\"")))
            .andExpect(content().string(containsString("Chưa có giấy phép lái xe")));

        uploadGplxFromProfile(tenantUser, "520123456789");

        assertNull(documents.findByTenantIdAndType(newcomer.id(), DocumentType.GPLX).orElseThrow().propertyId(),
            "chưa có hợp đồng nên giấy tờ không gắn nhà trọ nào");
        mockMvc.perform(get("/account/profile").header("X-User-Id", tenantUser))
            .andExpect(content().string(containsString("Đã có GPLX")))
            .andExpect(content().string(containsString("****6789")))
            .andExpect(content().string(not(containsString("520123456789"))));

        // Tải CCCD để đủ điều kiện check-in
        uploadCccdFromProfile(tenantUser);

        UUID contractId = contractWithSentCheckIn(newcomer);

        // Chủ trọ thấy GPLX ngay ở trang giấy tờ của nhà trọ
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + newcomer.id() + "/documents")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("****6789")));

        // Trang check-in của người thuê hiện cả 2 huy hiệu CCCD và GPLX
        mockMvc.perform(get("/contracts/" + contractId + "/handover").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đã có ảnh CCCD trong Hồ sơ cá nhân")))
            .andExpect(content().string(containsString("Đã có GPLX trong Hồ sơ cá nhân")));

        // Người thuê chỉ việc bấm xác nhận
        mockMvc.perform(post("/api/contracts/" + contractId + "/handover/confirm").header("X-User-Id", tenantUser))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Only tenants have the GPLX section; other roles are refused when posting the form")
    void onlyTenantsUploadGplxFromTheProfile() throws Exception {
        mockMvc.perform(get("/account/profile").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"gplx\""))));

        mockMvc.perform(multipart("/account/profile/driver-license")
                .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
                .param("licenseNumber", "123456789")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("driverLicenseErrorMessage"));
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
    }

    @Test
    @DisplayName("Missing license number on initial upload is rejected with clear Vietnamese error")
    void missingLicenseNumberOnInitialUploadIsRejected() throws Exception {
        Tenant newcomer = accountTenant("Trần Văn H", "079300006666", "0912345673");
        String tenantUser = signIn(newcomer);

        mockMvc.perform(multipart("/account/profile/driver-license")
                .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
                .header("X-User-Id", tenantUser))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("driverLicenseErrorMessage", containsString("Cần nhập số giấy phép lái xe")));
    }
}
