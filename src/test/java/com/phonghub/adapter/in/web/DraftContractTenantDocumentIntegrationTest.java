package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.DocumentType;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Người thuê của hợp đồng DRAFT phải mở được trang giấy tờ để tải CCCD trước khi xác nhận biên bản check-in (#221). */
class DraftContractTenantDocumentIntegrationTest extends SeededWebTest {

    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String PAGE = "/properties/" + DataSeeder.PROP_1_ID
        + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryTenantDocumentRepository documents;

    private void makeSeededContractDraft() {
        Contract active = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        contracts.save(new Contract(active.getId(), active.getPropertyId(), active.getRoomId(),
            active.getPrimaryTenantId(), active.getDepositAmount(), active.getRentAmount(), active.getStartDate(),
            active.getEndDate(), active.getPaymentDay(), ContractStatus.DRAFT, active.getOccupants(),
            active.getCreatedAt(), active.getUpdatedAt()));
    }

    @Test
    @DisplayName("Primary tenant of a DRAFT contract opens the documents page and uploads the CCCD")
    void tenantOpensDocumentsPageAndUploadsCccdWhileContractIsDraft() throws Exception {
        makeSeededContractDraft();

        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("CCCD")));

        mockMvc.perform(multipart(PAGE + "/CCCD/upload")
                .file(new MockMultipartFile("front", "front.png", "image/png", PNG))
                .header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        assertTrue(documents.findByTenantIdAndType(DataSeeder.TENANT_RECORD_ID, DocumentType.CCCD).isPresent());
        mockMvc.perform(get(PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An owner of another property is still refused the documents page")
    void otherOwnerStillRefused() throws Exception {
        makeSeededContractDraft();

        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_2))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }
}
