package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

@SpringBootTest(properties = "phonghub.handover.skip-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverSkipUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryHandoverPhotoRepository photos;
    @Autowired private InMemoryHandoverItemRepository items;

    @BeforeEach
    void reset() {
        handovers.clear();
        photos.clear();
        items.clear();
    }

    @Test
    @DisplayName("The skip button is shown to the manager only and approves the check-in without photos or tenant")
    void skipCheckIn() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("Bỏ qua bước này")));
        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(content().string(not(containsString("Bỏ qua bước này"))));

        mockMvc.perform(post(PAGE + "/skip").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("successMessage"));

        assertEquals(HandoverStatus.APPROVED,
            handovers.find(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_IN).orElseThrow().status());
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(content().string(not(containsString("Bỏ qua bước này"))));
    }

    @Test
    @DisplayName("A tenant cannot skip a handover")
    void tenantCannotSkip() throws Exception {
        mockMvc.perform(post(PAGE + "/skip").header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(true, handovers.find(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_IN).isEmpty());
    }
}
