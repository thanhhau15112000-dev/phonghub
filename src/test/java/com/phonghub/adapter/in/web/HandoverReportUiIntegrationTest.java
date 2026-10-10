package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class HandoverReportUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String CONTRACT = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover-report";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryHandoverPhotoRepository photos;
    @Autowired private InMemoryHandoverItemRepository items;
    @Autowired private InMemoryRoomAssetRepository roomAssets;

    @BeforeEach
    void reset() {
        handovers.clear();
        photos.clear();
        items.clear();
        roomAssets.clear();
    }

    @Test
    @DisplayName("Comparison page renders once a check-in exists, and bounces to the contract before that")
    void comparisonPage() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1)).andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/api/rooms/" + DataSeeder.ROOM_101_ID + "/assets").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Giường\",\"quantity\":1}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"initialElectricMeter\":10,\"initialWaterMeter\":2,\"roomCondition\":\"Sạch\",\"keysProvided\":\"1\"}"))
            .andExpect(status().isCreated());

        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(view().name("contracts/handover-compare"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Đối chiếu nhận phòng"));
        // Chưa duyệt nên chưa có nút PDF, và tải PDF bị từ chối.
        assertTrue(!html.contains("PDF nhận phòng"));
        mockMvc.perform(get(PAGE + "/check-in/pdf").header("X-User-Id", OWNER_1)).andExpect(status().is3xxRedirection());
    }
}
