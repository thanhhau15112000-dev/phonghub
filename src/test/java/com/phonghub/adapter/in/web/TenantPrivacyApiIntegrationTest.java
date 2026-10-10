package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantPrivacyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.TenantPrivacyUseCase;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Tenant;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class TenantPrivacyApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String TENANT_PATH = "/api/properties/" + DataSeeder.PROP_1_ID
        + "/tenants/" + DataSeeder.TENANT_RECORD_ID;
    private static final String DOCS = TENANT_PATH + "/documents";
    private static final String SEEDED_CCCD = "079201001111";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantPrivacyUseCase privacy;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryTenantRepository tenants;
    @Autowired private InMemoryTenantDocumentRepository documents;
    @Autowired private InMemoryTenantPrivacyRepository privacyRepository;

    @BeforeEach
    void resetPrivacy() {
        privacyRepository.clear();
    }

    private void uploadCccd(String actor) throws Exception {
        MockMultipartHttpServletRequestBuilder request = (MockMultipartHttpServletRequestBuilder)
            multipart(DOCS + "/CCCD").with(r -> {
                r.setMethod("PUT");
                return r;
            });
        mockMvc.perform(request.file(new MockMultipartFile("front", "f.png", "image/png", PNG))
            .header("X-User-Id", actor)).andExpect(status().isOk());
    }

    /** Check-out: hợp đồng đang hiệu lực của tenant mẫu kết thúc. */
    private void checkOut() {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.terminate();
        contracts.save(contract);
    }

    private static Instant daysFromNow(long days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    @Test
    @DisplayName("After check-out the profile is read-only (409 on edit/upload/delete) but still readable")
    void profileLocksAfterCheckOut() throws Exception {
        uploadCccd(OWNER_1);
        String update = "{\"fullName\":\"Nguyễn Văn A\",\"phone\":\"0901234567\"}";
        mockMvc.perform(put("/api/tenants/" + DataSeeder.TENANT_RECORD_ID).header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
        mockMvc.perform(get(TENANT_PATH + "/privacy").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));

        checkOut();

        mockMvc.perform(put("/api/tenants/" + DataSeeder.TENANT_RECORD_ID).header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        MockMultipartHttpServletRequestBuilder upload = (MockMultipartHttpServletRequestBuilder)
            multipart(DOCS + "/CCCD").with(r -> {
                r.setMethod("PUT");
                return r;
            });
        mockMvc.perform(upload.file(new MockMultipartFile("front", "f.png", "image/png", PNG))
            .header("X-User-Id", OWNER_1)).andExpect(status().isConflict());
        mockMvc.perform(delete(DOCS + "/CCCD").header("X-User-Id", OWNER_1)).andExpect(status().isConflict());
        mockMvc.perform(get(DOCS).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(get(TENANT_PATH + "/privacy").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true))
            .andExpect(jsonPath("$.purgeDueAt").exists());
    }

    @Test
    @DisplayName("Documents and the CCCD number are purged only after the retention period")
    void purgeAfterRetention() throws Exception {
        uploadCccd(OWNER_1);
        checkOut();

        assertEquals(0, privacy.purgeExpired(daysFromNow(89)));
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());

        assertEquals(1, privacy.purgeExpired(daysFromNow(91)));
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
        Tenant tenant = tenants.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow();
        assertTrue(tenant.identityPurged());
        assertFalse(tenant.identityCardNumber().contains(SEEDED_CCCD));
        assertTrue(tenants.findByIdentityCardNumber(SEEDED_CCCD).isEmpty());
        // Chạy lại không xóa thêm.
        assertEquals(0, privacy.purgeExpired(daysFromNow(200)));
        mockMvc.perform(get(TENANT_PATH + "/privacy").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.purged").value(true));
    }

    @Test
    @DisplayName("A tenant who still lives in the property is never purged")
    void activeTenantIsNotPurged() throws Exception {
        uploadCccd(OWNER_1);
        assertEquals(0, privacy.purgeExpired(daysFromNow(1000)));
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());
        assertFalse(tenants.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow().identityPurged());
    }

    @Test
    @DisplayName("Legal hold blocks purging; only the owner/admin can set or clear it")
    void legalHold() throws Exception {
        uploadCccd(OWNER_1);
        checkOut();
        String hold = TENANT_PATH + "/legal-hold";

        for (String denied : new String[]{STAFF_1, OWNER_2, TECH_1, TENANT_1}) {
            mockMvc.perform(put(hold).header("X-User-Id", denied).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Tranh chấp\"}")).andExpect(status().isForbidden());
            mockMvc.perform(delete(hold).header("X-User-Id", denied)).andExpect(status().isForbidden());
        }
        mockMvc.perform(put(hold).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"  \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(hold).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Tranh chấp tiền cọc\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.legalHold").value(true));

        assertEquals(0, privacy.purgeExpired(daysFromNow(365)));
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());

        mockMvc.perform(delete(hold).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.legalHold").value(false));
        assertEquals(1, privacy.purgeExpired(daysFromNow(365)));
    }

    @Test
    @DisplayName("Early erasure: only the tenant, not while a contract is active; legal hold defers it")
    void erasureRequest() throws Exception {
        uploadCccd(OWNER_1);
        String erase = "/api/tenants/me/erasure-request";

        mockMvc.perform(post(erase).header("X-User-Id", OWNER_1)).andExpect(status().isForbidden());
        mockMvc.perform(post(erase).header("X-User-Id", STAFF_1)).andExpect(status().isForbidden());
        mockMvc.perform(post(erase).header("X-User-Id", TENANT_1)).andExpect(status().isConflict());
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());

        checkOut();
        mockMvc.perform(put(TENANT_PATH + "/legal-hold").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Nghĩa vụ lưu trữ\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post(erase).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PENDING_LEGAL_HOLD"));
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());

        // Gỡ giữ pháp lý: yêu cầu xóa đã ghi nhận nên job xóa ngay, không cần đợi 90 ngày.
        mockMvc.perform(delete(TENANT_PATH + "/legal-hold").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        assertEquals(1, privacy.purgeExpired(Instant.now()));
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
    }

    @Test
    @DisplayName("Erasure after check-out without a hold purges immediately and is idempotent")
    void erasureImmediate() throws Exception {
        uploadCccd(OWNER_1);
        checkOut();
        mockMvc.perform(post("/api/tenants/me/erasure-request").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PURGED"));
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
        assertTrue(tenants.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow().identityPurged());
        mockMvc.perform(post("/api/tenants/me/erasure-request").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PURGED"));
    }

    @Test
    @DisplayName("Privacy status is limited to managers of the tenant's property")
    void statusAuthorization() throws Exception {
        for (String denied : new String[]{OWNER_2, TECH_1, TENANT_1}) {
            mockMvc.perform(get(TENANT_PATH + "/privacy").header("X-User-Id", denied)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_2_ID + "/tenants/" + DataSeeder.TENANT_RECORD_ID
            + "/privacy").header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());
    }
}
