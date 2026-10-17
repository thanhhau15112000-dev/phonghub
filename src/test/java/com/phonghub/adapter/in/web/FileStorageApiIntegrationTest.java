package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileStorageApiIntegrationTest {

    private static final String FILES = "/api/properties/" + DataSeeder.PROP_1_ID + "/files";
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();

    @Autowired
    private MockMvc mockMvc;

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "giay-to.pdf", "application/pdf",
            "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Owner uploads a PDF to own property and receives a property-scoped random key")
    void ownerUploadsFile() throws Exception {
        MvcResult result = mockMvc.perform(multipart(FILES).file(pdf()).param("category", "documents")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isCreated())
            .andReturn();
        assertTrue(result.getResponse().getContentAsString()
            .matches(".*\"objectKey\":\"properties/" + DataSeeder.PROP_1_ID + "/documents/[0-9a-f-]{36}[.]pdf\".*"));
    }

    @Test
    @DisplayName("Spoofed content type returns 400")
    void spoofedContentTypeReturns400() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
            "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(multipart(FILES).file(fake).param("category", "documents").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Unsupported file type returns 400")
    void unsupportedTypeReturns400() throws Exception {
        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart(FILES).file(text).param("category", "documents").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Oversized file returns 400")
    void oversizedFileReturns400() throws Exception {
        byte[] big = new byte[11 * 1024 * 1024];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, big, 0, header.length);
        MockMultipartFile large = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        mockMvc.perform(multipart(FILES).file(large).param("category", "documents").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Owner of another property gets 403 on upload, signed URL and delete")
    void foreignOwnerGets403() throws Exception {
        mockMvc.perform(multipart(FILES).file(pdf()).param("category", "documents").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        String key = "properties/" + DataSeeder.PROP_1_ID + "/documents/x.pdf";
        mockMvc.perform(get(FILES + "/signed-url").param("objectKey", key).header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(FILES).param("objectKey", key).header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Signed URL is issued only for keys of the same property")
    void signedUrlRejectsKeyOfAnotherProperty() throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart(FILES).file(pdf()).param("category", "documents")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isCreated())
            .andReturn();
        String body = uploaded.getResponse().getContentAsString();
        String key = body.replaceAll(".*\"objectKey\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(FILES + "/signed-url").param("objectKey", key).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").isNotEmpty());

        String foreignKey = "properties/" + DataSeeder.PROP_2_ID + "/documents/x.pdf";
        mockMvc.perform(get(FILES + "/signed-url").param("objectKey", foreignKey).header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }
}
