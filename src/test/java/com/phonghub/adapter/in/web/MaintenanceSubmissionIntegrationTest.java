package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Tạo phiếu kèm ảnh là một giao dịch: tệp bị từ chối thì phiếu cũng không được tạo. */
class MaintenanceSubmissionIntegrationTest extends SeededWebTest {

    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ROOM_101 = DataSeeder.ROOM_101_ID.toString();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;

    private int ticketsInRoom101() {
        return tickets.findByRoomId(DataSeeder.ROOM_101_ID).size();
    }

    private MockMultipartHttpServletRequestBuilder createWithImages(String path, int imageCount) {
        MockMultipartHttpServletRequestBuilder request = multipart(path)
            .param("roomId", ROOM_101).param("title", "Rò nước").param("description", "Vòi rò");
        for (int i = 0; i < imageCount; i++) {
            request.file(new MockMultipartFile("files", "photo" + i + ".png", "image/png", PNG));
        }
        return request;
    }

    @Test
    @DisplayName("API: six photos are refused with 409 and no ticket is left behind")
    void apiRejectsTooManyPhotosWithoutCreatingTicket() throws Exception {
        int before = ticketsInRoom101();

        mockMvc.perform(createWithImages("/api/maintenance", 6).header("X-User-Id", TENANT_1))
            .andExpect(status().isConflict());

        assertEquals(before, ticketsInRoom101());
    }

    @Test
    @DisplayName("API: a ticket with two photos is created together with both attachments")
    void apiCreatesTicketWithAttachments() throws Exception {
        int before = ticketsInRoom101();

        String json = mockMvc.perform(createWithImages("/api/maintenance", 2).header("X-User-Id", TENANT_1))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        assertEquals(before + 1, ticketsInRoom101());
        mockMvc.perform(get("/api/maintenance/" + id + "/attachments").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("UI: six photos send the tenant back to the form with an error and no ticket")
    void uiRejectsTooManyPhotosWithoutCreatingTicket() throws Exception {
        int before = ticketsInRoom101();

        mockMvc.perform(createWithImages("/maintenance", 6).param("cause", "NATURAL_WEAR").header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/maintenance/new?roomId=" + ROOM_101))
            .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(before, ticketsInRoom101());
    }
}
