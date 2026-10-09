package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyHouseRulesRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverUiIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
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
    }

    @Test
    @DisplayName("Without a handover the manager sees the create form; the tenant only sees a notice")
    void emptyPage() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chưa có biên bản check-in")))
            .andExpect(content().string(containsString("Tạo biên bản")));
        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Tạo biên bản"))));
    }

    @Test
    @DisplayName("The whole check-in can be run from the forms and the buttons follow the role and the status")
    void fullFlowThroughForms() throws Exception {
        mockMvc.perform(post(PAGE).header("X-User-Id", OWNER_1).param("initialElectricMeter", "100.5")
                .param("initialWaterMeter", "20").param("roomCondition", "Phòng sạch").param("keysProvided", "2 chìa"))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("DRAFT")))
            .andExpect(content().string(containsString("Gửi cho khách thuê")))
            .andExpect(content().string(not(containsString("Duyệt</button>"))));

        mockMvc.perform(post(PAGE + "/send").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("errorMessage"));

        for (String kind : new String[]{"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(PAGE + "/photos").file(new MockMultipartFile("file", "p.png", "image/png", PNG))
                    .param("kind", kind).header("X-User-Id", OWNER_1))
                .andExpect(flash().attributeExists("successMessage"));
        }
        mockMvc.perform(post(PAGE + "/house-rules").header("X-User-Id", OWNER_1).param("content", "Giữ yên tĩnh"))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(post(PAGE + "/send").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tôi xác nhận biên bản")))
            .andExpect(content().string(containsString("Giữ yên tĩnh")))
            .andExpect(content().string(not(containsString("Gửi cho khách thuê"))));
        mockMvc.perform(post(PAGE + "/confirm").header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform((MockMultipartHttpServletRequestBuilder) multipart("/properties/" + DataSeeder.PROP_1_ID
                + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents/CCCD/upload")
                .file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(post(PAGE + "/confirm").header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("PENDING_APPROVAL")))
            .andExpect(content().string(containsString("Duyệt</button>")))
            .andExpect(content().string(containsString("Từ chối</button>")));
        mockMvc.perform(post(PAGE + "/approve").header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(PAGE + "/approve").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(post(PAGE + "/approve").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("APPROVED")))
            .andExpect(content().string(containsString("Đã duyệt lúc")));
    }

    @Test
    @DisplayName("Outsiders are redirected with an error and cannot read or change the handover")
    void outsidersAreRefused() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(PAGE).header("X-User-Id", OWNER_2).param("initialElectricMeter", "1")
                .param("initialWaterMeter", "1").param("roomCondition", "x"))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(PAGE).header("X-User-Id", TENANT_1).param("initialElectricMeter", "1")
                .param("initialWaterMeter", "1").param("roomCondition", "x"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("The contract page links to the check-in page for an active contract")
    void contractPageLinks() throws Exception {
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(PAGE)));
    }

    @Test
    @DisplayName("Photos are served through a short-lived redirect and never as a direct storage key")
    void photoRedirect() throws Exception {
        mockMvc.perform(post(PAGE).header("X-User-Id", OWNER_1).param("initialElectricMeter", "1")
            .param("initialWaterMeter", "1").param("roomCondition", "x"));
        mockMvc.perform(multipart(PAGE + "/photos").file(new MockMultipartFile("file", "p.png", "image/png", PNG))
            .param("kind", "ROOM").header("X-User-Id", OWNER_1));
        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString();
        String marker = PAGE + "/photos/";
        String photoPath = html.substring(html.indexOf(marker), html.indexOf(marker) + marker.length() + 36);

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", containsString("memory://")))
            .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", not(containsString("memory://"))));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("properties/" + DataSeeder.PROP_1_ID + "/handover/"));
    }
}
