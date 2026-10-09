package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomAssetUiIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ROOM = "/rooms/" + DataSeeder.ROOM_101_ID;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Owner creates an asset through the form and sees it with management controls")
    void ownerCreatesAssetAndSeesIt() throws Exception {
        String name = "Tivi UI " + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post(ROOM + "/assets").header("X-User-Id", OWNER_1)
                .param("name", name).param("quantity", "2").param("estimatedValue", "3000000")
                .param("note", "Còn mới"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(ROOM))
            .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tài sản trong phòng")))
            .andExpect(content().string(containsString(name)))
            .andExpect(content().string(containsString("Thêm từ mẫu")))
            .andExpect(content().string(containsString("+ Thêm tài sản")));
    }

    @Test
    @DisplayName("Tenant sees assets of own room but no management controls")
    void tenantSeesReadOnlyView() throws Exception {
        mockMvc.perform(get(ROOM).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tài sản trong phòng")))
            .andExpect(content().string(not(containsString("Thêm từ mẫu"))))
            .andExpect(content().string(not(containsString("+ Thêm tài sản"))));
    }

    @Test
    @DisplayName("Tenant cannot create assets: error flash, nothing created")
    void tenantCannotCreateViaForm() throws Exception {
        mockMvc.perform(post(ROOM + "/assets").header("X-User-Id", TENANT_1).param("name", "Lén thêm"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(get(ROOM).header("X-User-Id", TENANT_1))
            .andExpect(content().string(not(containsString("Lén thêm"))));
    }

    @Test
    @DisplayName("Owner of another property gets an error flash when posting asset actions")
    void foreignOwnerGetsErrorFlash() throws Exception {
        mockMvc.perform(post(ROOM + "/assets").header("X-User-Id", OWNER_2).param("name", "Xâm nhập"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(ROOM + "/assets/template").header("X-User-Id", OWNER_2).param("template", "BASIC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Invalid input shows an error flash instead of a server error")
    void invalidInputShowsErrorFlash() throws Exception {
        mockMvc.perform(post(ROOM + "/assets").header("X-User-Id", OWNER_1)
                .param("name", "Sai số lượng").param("quantity", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(ROOM + "/assets/template").header("X-User-Id", OWNER_1).param("template", "KHONGCO"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Event and delete forms update the page")
    void eventAndDeleteFlow() throws Exception {
        String name = "Máy lạnh " + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post(ROOM + "/assets").header("X-User-Id", OWNER_1).param("name", name).param("quantity", "1"))
            .andExpect(flash().attributeExists("successMessage"));
        UUID assetId = latestAssetId(name);

        mockMvc.perform(post(ROOM + "/assets/" + assetId + "/events").header("X-User-Id", OWNER_1)
                .param("type", "DAMAGED").param("description", "Không lạnh").param("cost", "0"))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("Hư hỏng")))
            .andExpect(content().string(containsString("Không lạnh")));

        mockMvc.perform(post(ROOM + "/assets/" + assetId + "/delete").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(content().string(not(containsString(name))));
    }

    private static final byte[] TINY_PNG = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    @DisplayName("Uploaded image is stored privately and served only through a short-lived redirect")
    void imageUploadIsServedThroughSignedRedirect() throws Exception {
        String name = "Tủ ảnh " + UUID.randomUUID().toString().substring(0, 6);
        org.springframework.mock.web.MockMultipartFile png = new org.springframework.mock.web.MockMultipartFile(
            "imageFile", "tu.png", "image/png", TINY_PNG);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(ROOM + "/assets")
                .file(png).header("X-User-Id", OWNER_1).param("name", name).param("quantity", "1"))
            .andExpect(flash().attributeExists("successMessage"));

        UUID assetId = latestAssetId(name);
        MvcResult page = mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1)).andReturn();
        String html = page.getResponse().getContentAsString();
        assertEquals(true, html.contains("/assets/" + assetId + "/image"));
        assertEquals(false, html.contains("properties/" + DataSeeder.PROP_1_ID + "/assets/"));

        mockMvc.perform(get(ROOM + "/assets/" + assetId + "/image").header("X-User-Id", OWNER_1))
            .andExpect(status().isFound())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Location", containsString("memory://")));
        mockMvc.perform(get(ROOM + "/assets/" + assetId + "/image").header("X-User-Id", TENANT_1))
            .andExpect(status().isFound());
        mockMvc.perform(get(ROOM + "/assets/" + assetId + "/image").header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Location", not(containsString("memory://"))));
    }

    @Test
    @DisplayName("A non-image file renamed to png is rejected and no asset is created")
    void fakeImageIsRejected() throws Exception {
        String name = "Giả ảnh " + UUID.randomUUID().toString().substring(0, 6);
        org.springframework.mock.web.MockMultipartFile fake = new org.springframework.mock.web.MockMultipartFile(
            "imageFile", "fake.png", "image/png", "not an image".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(ROOM + "/assets")
                .file(fake).header("X-User-Id", OWNER_1).param("name", name).param("quantity", "1"))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1))
            .andExpect(content().string(not(containsString(name))));
    }

    @Test
    @DisplayName("Web QR endpoint returns a PNG and rejects an asset of a different room")
    void webQr() throws Exception {
        String name = "Bàn QR " + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post(ROOM + "/assets").header("X-User-Id", OWNER_1).param("name", name).param("quantity", "1"));
        UUID assetId = latestAssetId(name);

        MvcResult png = mockMvc.perform(get(ROOM + "/assets/" + assetId + "/qr").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn();
        assertEquals("image/png", png.getResponse().getContentType());
        assertEquals((byte) 0x89, png.getResponse().getContentAsByteArray()[0]);

        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_102_ID + "/assets/" + assetId + "/qr")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isNotFound());
        MvcResult denied = mockMvc.perform(get(ROOM + "/assets/" + assetId + "/qr").header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection()).andReturn();
        assertEquals(0, denied.getResponse().getContentAsByteArray().length);
    }

    private UUID latestAssetId(String name) throws Exception {
        MvcResult page = mockMvc.perform(get(ROOM).header("X-User-Id", OWNER_1)).andReturn();
        String html = page.getResponse().getContentAsString();
        int nameIndex = html.indexOf(name);
        int qrIndex = html.indexOf("/assets/", nameIndex);
        return UUID.fromString(html.substring(qrIndex + "/assets/".length(), qrIndex + "/assets/".length() + 36));
    }
}
