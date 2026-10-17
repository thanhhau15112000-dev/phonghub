package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.AuditPort;
import java.util.Base64;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantDocumentApiIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String BASE = "/api/properties/" + DataSeeder.PROP_1_ID
        + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditPort auditPort;

    @BeforeEach
    void clearAudit() {
        ((InMemoryAuditAdapter) auditPort).clear();
    }

    private long count(String action) {
        return ((InMemoryAuditAdapter) auditPort).getRecordedEvents().stream()
            .filter(event -> event.action().equals(action)).count();
    }

    private MockMultipartHttpServletRequestBuilder put(String path) {
        return (MockMultipartHttpServletRequestBuilder) multipart(path).with(request -> {
            request.setMethod("PUT");
            return request;
        });
    }

    private void uploadCccd(String actor) throws Exception {
        mockMvc.perform(put(BASE + "/CCCD").file(new MockMultipartFile("front", "f.png", "image/png", PNG))
                .file(new MockMultipartFile("back", "b.png", "image/png", PNG)).header("X-User-Id", actor))
            .andExpect(status().isOk());
    }

    private void uploadGplx(String actor, String number) throws Exception {
        mockMvc.perform(put(BASE + "/GPLX").param("number", number)
                .file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", actor))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Owner uploads CCCD and GPLX; the list only shows masked numbers and no image data")
    void ownerUploadsAndListsMaskedDocuments() throws Exception {
        uploadCccd(OWNER_1);
        uploadGplx(OWNER_1, "012345678901");

        mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.type=='CCCD')].maskedNumber").value("****1111"))
            .andExpect(jsonPath("$[?(@.type=='GPLX')].maskedNumber").value("****8901"))
            .andExpect(jsonPath("$[?(@.type=='CCCD')].hasBackImage").value(true))
            .andExpect(content().string(not(containsString("012345678901"))))
            .andExpect(content().string(not(containsString("079201001111"))))
            .andExpect(content().string(not(containsString("base64"))));
    }

    @Test
    @DisplayName("Every view writes exactly one audit event")
    void everyViewIsAudited() throws Exception {
        uploadGplx(OWNER_1, "555566667777");

        mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        assertEquals(1, count("TENANT_DOCUMENTS_VIEWED"));

        mockMvc.perform(get(BASE + "/GPLX").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        assertEquals(1, count("TENANT_DOCUMENT_VIEWED"));

        mockMvc.perform(post(BASE + "/GPLX/reveal").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
            .andExpect(status().isOk());
        assertEquals(1, count("TENANT_DOCUMENT_REVEALED"));

        ((InMemoryAuditAdapter) auditPort).getRecordedEvents().forEach(event ->
            assertEquals(false, event.details().toString().contains("555566667777")));
    }

    @Test
    @DisplayName("Reveal needs the viewer's own password; the full number and decrypted images are returned")
    void revealWithPasswordReturnsFullData() throws Exception {
        uploadGplx(OWNER_1, "999988887777");

        MvcResult result = mockMvc.perform(post(BASE + "/GPLX/reveal").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value("999988887777"))
            .andExpect(jsonPath("$.front.contentType").value("image/png"))
            .andExpect(jsonPath("$.back").doesNotExist())
            .andReturn();
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        String base64 = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.front.base64");
        assertEquals((byte) 0x89, Base64.getDecoder().decode(base64)[0]);

        mockMvc.perform(post(BASE + "/CCCD/reveal").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value("079201001111"));
    }

    @Test
    @DisplayName("Wrong or blank password is refused with 403, audited, and repeated failures end in 429")
    void wrongPasswordIsRefusedThenThrottled() throws Exception {
        uploadGplx(STAFF_1, "111122223333");

        mockMvc.perform(post(BASE + "/GPLX/reveal").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"\"}"))
            .andExpect(status().isBadRequest());
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post(BASE + "/GPLX/reveal").header("X-User-Id", STAFF_1)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("111122223333"))));
        }
        assertEquals(5, count("TENANT_DOCUMENT_REVEAL_FAILED"));
        assertEquals(0, count("TENANT_DOCUMENT_REVEALED"));

        mockMvc.perform(post(BASE + "/GPLX/reveal").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
            .andExpect(status().isTooManyRequests());
        assertEquals(0, count("TENANT_DOCUMENT_REVEALED"));
    }

    @Test
    @DisplayName("Users outside the property or without the right role are rejected with 403")
    void outsidersAreRejected() throws Exception {
        uploadCccd(OWNER_1);
        for (String outsider : List.of(OWNER_2, STAFF_2, TECH_1)) {
            mockMvc.perform(get(BASE).header("X-User-Id", outsider)).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/CCCD").header("X-User-Id", outsider)).andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/CCCD/reveal").header("X-User-Id", outsider)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
                .andExpect(status().isForbidden());
            mockMvc.perform(put(BASE + "/CCCD").file(new MockMultipartFile("front", "f.png", "image/png", PNG))
                    .header("X-User-Id", outsider))
                .andExpect(status().isForbidden());
            mockMvc.perform(delete(BASE + "/CCCD").header("X-User-Id", outsider)).andExpect(status().isForbidden());
        }
        assertEquals(0, count("TENANT_DOCUMENT_REVEALED"));
    }

    @Test
    @DisplayName("Tenant can upload and fully view their own documents but cannot delete them")
    void tenantManagesOwnDocuments() throws Exception {
        uploadCccd(TENANT_1);
        mockMvc.perform(get(BASE).header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/CCCD/reveal").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"correct-horse\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value("079201001111"));
        mockMvc.perform(delete(BASE + "/CCCD").header("X-User-Id", TENANT_1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid uploads return 400: non-image, spoofed image, missing front image")
    void invalidUploadsAreRejected() throws Exception {
        mockMvc.perform(put(BASE + "/CCCD").file(new MockMultipartFile("front", "f.pdf", "application/pdf",
                "%PDF-1.7\nbody".getBytes())).header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/CCCD").file(new MockMultipartFile("front", "f.png", "image/png",
                "not an image".getBytes())).header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/CCCD").header("X-User-Id", OWNER_1)).andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/GPLX").file(new MockMultipartFile("front", "f.png", "image/png", PNG))
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Owner deletes a document; it then returns 404")
    void deleteRemovesDocument() throws Exception {
        uploadGplx(OWNER_1, "424242424242");

        mockMvc.perform(delete(BASE + "/GPLX").header("X-User-Id", OWNER_1)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/GPLX").header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());
        assertEquals(1, count("TENANT_DOCUMENT_DELETED"));
    }
}
