package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyHouseRulesRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.application.port.out.AuditPort;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String BODY = "{\"initialElectricMeter\":1420.5,\"initialWaterMeter\":35,"
        + "\"roomCondition\":\"Phòng sạch sẽ\",\"keysProvided\":\"2 chìa khóa\"}";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditPort auditPort;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryHandoverPhotoRepository photos;
    @Autowired private InMemoryHandoverItemRepository items;
    @Autowired private InMemoryPropertyHouseRulesRepository rules;
    @Autowired private InMemoryTenantDocumentRepository documents;

    @BeforeEach
    void reset() {
        handovers.clear();
        photos.clear();
        items.clear();
        rules.clear();
        documents.clear();
        ((InMemoryAuditAdapter) auditPort).clear();
    }

    private long count(String action) {
        return ((InMemoryAuditAdapter) auditPort).getRecordedEvents().stream()
            .filter(event -> event.action().equals(action)).count();
    }

    private void createDraft(String actor) throws Exception {
        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", actor)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    private void addPhotos(String actor) throws Exception {
        for (String kind : new String[]{"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(CONTRACT + "/handover/photos").file(
                    new MockMultipartFile("file", "p.png", "image/png", PNG)).param("kind", kind)
                    .header("X-User-Id", actor))
                .andExpect(status().isCreated());
        }
    }

    private void tenantUploadsCccd() throws Exception {
        String url = "/api/properties/" + DataSeeder.PROP_1_ID + "/tenants/" + DataSeeder.TENANT_RECORD_ID
            + "/documents/CCCD";
        mockMvc.perform((MockMultipartHttpServletRequestBuilder) multipart(url).with(request -> {
                request.setMethod("PUT");
                return request;
            }).file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
    }

    private void sendAndConfirm() throws Exception {
        createDraft(OWNER_1);
        addPhotos(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_TENANT"));
        tenantUploadsCccd();
        mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.isConfirmedByTenant").value(true));
    }

    @Test
    @DisplayName("Full flow: create, photos, send, tenant confirms, staff approves; contract and room are untouched")
    void fullFlow() throws Exception {
        sendAndConfirm();

        mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get(CONTRACT + "/handover").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos.length()").value(3))
            .andExpect(jsonPath("$.contractTermsHash").isNotEmpty());
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("OCCUPIED"));

        for (String action : new String[]{"HANDOVER_CREATED", "HANDOVER_SENT", "HANDOVER_TENANT_CONFIRMED",
            "HANDOVER_APPROVED"}) {
            assertEquals(1, count(action), action);
        }
        assertEquals(3, count("HANDOVER_PHOTO_ADDED"));
    }

    @Test
    @DisplayName("A contract has exactly one check-in: the second create returns 409")
    void onlyOneCheckInPerContract() throws Exception {
        createDraft(OWNER_1);

        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Approving twice returns 409; approving too early returns 409")
    void approveTwiceAndTooEarly() throws Exception {
        createDraft(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", TENANT_1))
            .andExpect(status().isConflict());

        addPhotos(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        tenantUploadsCccd();
        mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        assertEquals(1, count("HANDOVER_APPROVED"));
    }

    @Test
    @DisplayName("Wrong roles get 403 on every step")
    void wrongRolesAreRejected() throws Exception {
        createDraft(OWNER_1);
        addPhotos(OWNER_1);

        for (String outsider : new String[]{OWNER_2, TECH_1, TENANT_1}) {
            mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", outsider)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
            mockMvc.perform(patch(CONTRACT + "/handover").header("X-User-Id", outsider)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
            mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", outsider))
                .andExpect(status().isForbidden());
            mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", outsider))
                .andExpect(status().isForbidden());
            mockMvc.perform(post(CONTRACT + "/handover/reject").header("X-User-Id", outsider)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        }
        for (String outsider : new String[]{OWNER_2, TECH_1}) {
            mockMvc.perform(get(CONTRACT + "/handover").header("X-User-Id", outsider))
                .andExpect(status().isForbidden());
        }
        for (String nonTenant : new String[]{OWNER_1, STAFF_1, OWNER_2}) {
            mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", nonTenant))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Sending needs a photo of every kind; the tenant needs a CCCD document before confirming")
    void prerequisitesAreEnforced() throws Exception {
        createDraft(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());

        addPhotos(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", TENANT_1))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("CCCD")));
    }

    @Test
    @DisplayName("Content can only be edited while the handover is a draft")
    void editOnlyInDraft() throws Exception {
        createDraft(OWNER_1);
        mockMvc.perform(patch(CONTRACT + "/handover").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialElectricMeter\":1500,\"initialWaterMeter\":40,\"roomCondition\":\"Đã sửa\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.initialElectricMeter").value(1500));
        addPhotos(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());

        mockMvc.perform(patch(CONTRACT + "/handover").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
        mockMvc.perform(multipart(CONTRACT + "/handover/photos").file(
                new MockMultipartFile("file", "p.png", "image/png", PNG)).param("kind", "ROOM")
                .header("X-User-Id", OWNER_1)).andExpect(status().isConflict());
        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialElectricMeter\":-1,\"initialWaterMeter\":1,\"roomCondition\":\"x\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejection needs a reason; after rejection staff can reopen, fix and send again")
    void rejectReopenAndResend() throws Exception {
        sendAndConfirm();

        mockMvc.perform(post(CONTRACT + "/handover/reject").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(CONTRACT + "/handover/reject").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thiếu ảnh đồng hồ nước\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Thiếu ảnh đồng hồ nước"));
        mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());

        mockMvc.perform(post(CONTRACT + "/handover/reopen").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_TENANT"));
        assertEquals(1, count("HANDOVER_REJECTED"));
        assertEquals(1, count("HANDOVER_REOPENED"));
    }

    @Test
    @DisplayName("Send snapshots the room assets and the house rules with their hash")
    void sendSnapshotsAssetsAndRules() throws Exception {
        String assetsUrl = "/api/rooms/" + DataSeeder.ROOM_101_ID + "/assets";
        mockMvc.perform(post(assetsUrl).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Giường check-in\",\"quantity\":1}")).andExpect(status().isCreated());
        mockMvc.perform(put("/api/properties/" + DataSeeder.PROP_1_ID + "/house-rules").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Không hút thuốc trong phòng\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        createDraft(OWNER_1);
        addPhotos(OWNER_1);
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.houseRulesText").value("Không hút thuốc trong phòng"))
            .andExpect(jsonPath("$.houseRulesHash").isNotEmpty())
            .andExpect(jsonPath("$.contractTermsHash").isNotEmpty())
            .andExpect(jsonPath("$.items[?(@.assetName=='Giường check-in')]").exists());

        mockMvc.perform(put("/api/properties/" + DataSeeder.PROP_1_ID + "/house-rules").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Nội quy mới\"}"))
            .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get(CONTRACT + "/handover").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.houseRulesText").value("Không hút thuốc trong phòng"));
    }

    @Test
    @DisplayName("House rules can be read by people of the property but only changed by its managers")
    void houseRulesAccess() throws Exception {
        String url = "/api/properties/" + DataSeeder.PROP_1_ID + "/house-rules";
        mockMvc.perform(get(url).header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());
        mockMvc.perform(put(url).header("X-User-Id", TENANT_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put(url).header("X-User-Id", OWNER_2).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put(url).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\" \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put(url).header("X-User-Id", STAFF_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"Giữ yên tĩnh sau 22h\"}")).andExpect(status().isOk());
        mockMvc.perform(get(url).header("X-User-Id", TENANT_1)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("Giữ yên tĩnh sau 22h"));
        mockMvc.perform(get(url).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Photos can be removed in draft and the audit never carries personal values")
    void removePhotoAndAuditHygiene() throws Exception {
        createDraft(OWNER_1);
        String body = mockMvc.perform(multipart(CONTRACT + "/handover/photos").file(
                new MockMultipartFile("file", "p.png", "image/png", PNG)).param("kind", "ROOM")
                .header("X-User-Id", OWNER_1)).andExpect(status().isCreated()).andReturn()
            .getResponse().getContentAsString();
        String photoId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete(CONTRACT + "/handover/photos/" + UUID.randomUUID()).header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(CONTRACT + "/handover/photos/" + photoId).header("X-User-Id", OWNER_1))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(CONTRACT + "/handover").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.photos.length()").value(0))
            .andExpect(content().string(not(containsString("storage"))));
        ((InMemoryAuditAdapter) auditPort).getRecordedEvents().forEach(event ->
            assertEquals(false, event.details().toString().contains("Phòng sạch")));
    }

    @Test
    @DisplayName("Unknown contract returns 404; a non-image photo returns 400")
    void notFoundAndInvalidPhoto() throws Exception {
        mockMvc.perform(get("/api/contracts/" + UUID.randomUUID() + "/handover").header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound());
        mockMvc.perform(get(CONTRACT + "/handover").header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());
        createDraft(OWNER_1);
        mockMvc.perform(multipart(CONTRACT + "/handover/photos").file(
                new MockMultipartFile("file", "p.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                .param("kind", "ROOM").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }
}
