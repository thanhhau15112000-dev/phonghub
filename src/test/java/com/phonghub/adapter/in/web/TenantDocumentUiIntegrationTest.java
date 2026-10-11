package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class TenantDocumentUiIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PAGE = "/properties/" + DataSeeder.PROP_1_ID
        + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    private void uploadGplx(String actor, String number) throws Exception {
        mockMvc.perform(multipart(PAGE + "/GPLX/upload")
                .file(new MockMultipartFile("front", "f.png", "image/png", PNG))
                .param("number", number).header("X-User-Id", actor))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("The page lists masked numbers only and is marked as not cacheable")
    void pageShowsMaskedNumbers() throws Exception {
        uploadGplx(OWNER_1, "246813579024");

        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(content().string(containsString("****9024")))
            .andExpect(content().string(not(containsString("246813579024"))))
            .andExpect(content().string(not(containsString("data:image"))));
    }

    @Test
    @DisplayName("Correct password shows the full number and decrypted image on a non-cacheable page")
    void revealShowsFullDataWithPassword() throws Exception {
        uploadGplx(OWNER_1, "135792468013");

        mockMvc.perform(post(PAGE + "/GPLX/reveal").param("password", "correct-horse").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(content().string(containsString("135792468013")))
            .andExpect(content().string(containsString("data:image/png;base64,")));
    }

    @Test
    @DisplayName("Wrong password shows an error and never the number or the image")
    void wrongPasswordShowsErrorOnly() throws Exception {
        uploadGplx(OWNER_1, "864209753186");

        mockMvc.perform(post(PAGE + "/GPLX/reveal").param("password", "wrong-password").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Mật khẩu xác nhận không đúng")))
            .andExpect(content().string(not(containsString("864209753186"))))
            .andExpect(content().string(not(containsString("data:image"))));
    }

    @Test
    @DisplayName("Other owners are redirected with an error and see nothing; tenant has no delete button")
    void accessRulesInTheUi() throws Exception {
        uploadGplx(OWNER_1, "192837465019");

        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post(PAGE + "/GPLX/reveal").param("password", "correct-horse").header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(content().string(not(containsString("192837465019"))));

        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Xóa</button>"))));
    }

    @Test
    @DisplayName("Contract page links to the documents page")
    void contractPageLinksToDocuments() throws Exception {
        mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(PAGE)));
    }
}
