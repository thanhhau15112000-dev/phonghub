package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantPrivacyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.TenantPrivacyUseCase;
import com.phonghub.domain.model.Contract;
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

/** Chế độ mặc định APPROVAL: đến hạn chỉ báo owner, việc xóa cần owner/admin xác nhận bằng mật khẩu. */
class TenantPurgeApprovalIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PROPERTY = "/api/properties/" + DataSeeder.PROP_1_ID;
    private static final String TENANT_PATH = PROPERTY + "/tenants/" + DataSeeder.TENANT_RECORD_ID;
    private static final String GOOD_PASSWORD = "{\"password\":\"correct-horse\"}";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantPrivacyUseCase privacy;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryTenantRepository tenants;
    @Autowired private InMemoryTenantDocumentRepository documents;
    @Autowired private InMemoryTenantPrivacyRepository privacyRepository;
    @Autowired private InMemoryNotificationRepository notifications;

    @BeforeEach
    void reset() {
        privacyRepository.clear();
        documents.clear();
    }

    private void uploadCccd() throws Exception {
        MockMultipartHttpServletRequestBuilder request = (MockMultipartHttpServletRequestBuilder)
            multipart(TENANT_PATH + "/documents/CCCD").with(r -> {
                r.setMethod("PUT");
                return r;
            });
        mockMvc.perform(request.file(new MockMultipartFile("front", "f.png", "image/png", PNG))
            .header("X-User-Id", OWNER_1)).andExpect(status().isOk());
    }

    private void checkOut() {
        Contract contract = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contract.terminate();
        contracts.save(contract);
    }

    private static Instant daysFromNow(long days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    private long dueNotices() {
        return notifications.findAll().stream()
            .filter(n -> "Cần xác nhận xóa giấy tờ người thuê".equals(n.title())).count();
    }

    @Test
    @DisplayName("When due the job only notifies the owner once; nothing is deleted until a manager confirms")
    void jobNotifiesButDoesNotDelete() throws Exception {
        uploadCccd();
        checkOut();
        long before = dueNotices();

        assertEquals(0, privacy.purgeExpired(daysFromNow(91)));
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());
        long afterFirst = dueNotices();
        assertTrue(afterFirst > before, "owner should be told the tenant is due");
        assertTrue(notifications.findAll().stream().anyMatch(n -> java.util.Objects.equals(
            n.targetUserId() == null ? null : n.targetUserId().toString(), OWNER_1)
            && "Cần xác nhận xóa giấy tờ người thuê".equals(n.title())));

        // Chạy lại không báo lặp.
        privacy.purgeExpired(daysFromNow(92));
        assertEquals(afterFirst, dueNotices());
    }

    @Test
    @DisplayName("Pending list shows only due tenants; confirm needs the manager's password and a due, unheld tenant")
    void confirmFlow() throws Exception {
        uploadCccd();
        mockMvc.perform(get(PROPERTY + "/privacy/pending-purge").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // Còn hợp đồng hiệu lực: không xóa được dù xác nhận đúng mật khẩu.
        mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(GOOD_PASSWORD)).andExpect(status().isConflict());

        checkOut();
        // Mới check-out, chưa đến 90 ngày: chưa có trong danh sách chờ và chưa xóa được.
        mockMvc.perform(get(PROPERTY + "/privacy/pending-purge").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(GOOD_PASSWORD)).andExpect(status().isConflict());

        // Tenant yêu cầu xóa sớm: chờ owner xác nhận.
        mockMvc.perform(post("/api/tenants/me/erasure-request").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PENDING_APPROVAL"));
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());
        mockMvc.perform(get(PROPERTY + "/privacy/pending-purge").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].erasureRequested").value(true));

        mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"sai-mat-khau\"}"))
            .andExpect(status().isForbidden());
        assertEquals(1, documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).size());

        mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(GOOD_PASSWORD))
            .andExpect(status().isOk()).andExpect(jsonPath("$.purged").value(true));
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
        assertTrue(tenants.findById(DataSeeder.TENANT_RECORD_ID).orElseThrow().identityPurged());
        mockMvc.perform(get(PROPERTY + "/privacy/pending-purge").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Only the property owner or admin can list/confirm; legal hold blocks confirmation")
    void authorizationAndLegalHold() throws Exception {
        uploadCccd();
        checkOut();
        mockMvc.perform(post("/api/tenants/me/erasure-request").header("X-User-Id", TENANT_1)).andExpect(status().isOk());

        for (String denied : new String[]{STAFF_1, OWNER_2, TENANT_1}) {
            mockMvc.perform(get(PROPERTY + "/privacy/pending-purge").header("X-User-Id", denied))
                .andExpect(status().isForbidden());
            mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", denied)
                .contentType(MediaType.APPLICATION_JSON).content(GOOD_PASSWORD)).andExpect(status().isForbidden());
        }
        assertFalse(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());

        mockMvc.perform(put(TENANT_PATH + "/legal-hold").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tranh chấp\"}")).andExpect(status().isOk());
        mockMvc.perform(get(PROPERTY + "/privacy/pending-purge").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(GOOD_PASSWORD)).andExpect(status().isConflict());

        // Admin cũng xác nhận được sau khi gỡ giữ pháp lý.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete(TENANT_PATH + "/legal-hold").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(post(TENANT_PATH + "/privacy/purge").header("X-User-Id", ADMIN)
            .contentType(MediaType.APPLICATION_JSON).content(GOOD_PASSWORD)).andExpect(status().isOk());
        assertTrue(documents.findByTenantId(DataSeeder.TENANT_RECORD_ID).isEmpty());
    }
}
