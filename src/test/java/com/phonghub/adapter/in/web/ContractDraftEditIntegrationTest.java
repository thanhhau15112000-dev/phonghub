package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** #89: sửa điều khoản và hủy hợp đồng nháp qua giao diện và API — 403, 404, 409 theo trạng thái và version. */
class ContractDraftEditIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ACTIVE = DataSeeder.CONTRACT_1_ID.toString();
    private static final LocalDate START = LocalDate.of(2026, 12, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;

    private String draftId;

    @BeforeEach
    void createDraft() {
        Contract draft = Contract.create(DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, DataSeeder.TENANT_RECORD_ID,
            new BigDecimal("1000000"), new BigDecimal("4000000"), START, START.plusYears(1), 5);
        draftId = contracts.save(draft).getId().toString();
    }

    private static String terms(String rent, String version) {
        return "{\"depositAmount\":2000000,\"rentAmount\":" + rent + ",\"startDate\":\"2026-12-15\","
            + "\"endDate\":\"2027-06-15\",\"paymentDay\":10" + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    @Test
    @DisplayName("Giao diện: hợp đồng nháp có nút sửa/hủy; lưu điều khoản mới rồi hủy, trang hiện 'Đã hủy'")
    void uiEditThenCancel() throws Exception {
        String page = mockMvc.perform(get("/contracts/" + draftId).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("editDraftModal"));
        assertTrue(page.contains("Hủy hợp đồng nháp"));

        mockMvc.perform(post("/contracts/" + draftId + "/edit").header("X-User-Id", OWNER_1)
                .param("depositAmount", "1500000").param("rentAmount", "4200000")
                .param("startDate", "2026-12-10").param("endDate", "2027-12-10").param("paymentDay", "7")
                .param("version", "0"))
            .andExpect(redirectedUrl("/contracts/" + draftId))
            .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật điều khoản")));
        Contract saved = contracts.findById(UUID.fromString(draftId)).orElseThrow();
        assertEquals(0, new BigDecimal("4200000").compareTo(saved.getRentAmount()));
        assertEquals(LocalDate.of(2026, 12, 10), saved.getStartDate());
        assertEquals(7, saved.getPaymentDay());

        mockMvc.perform(post("/contracts/" + draftId + "/edit").header("X-User-Id", OWNER_1)
                .param("depositAmount", "1500000").param("rentAmount", "4200000")
                .param("startDate", "2026-12-10").param("endDate", "2026-12-01").param("paymentDay", "7"))
            .andExpect(flash().attribute("errorMessage", containsString("Ngày kết thúc phải sau ngày bắt đầu")));

        mockMvc.perform(post("/contracts/" + draftId + "/cancel").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/contracts/" + draftId))
            .andExpect(flash().attribute("successMessage", containsString("Đã hủy hợp đồng nháp")));
        assertEquals(ContractStatus.CANCELLED, contracts.findById(UUID.fromString(draftId)).orElseThrow().getStatus());
        String cancelled = mockMvc.perform(get("/contracts/" + draftId).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(cancelled.contains("Đã hủy"));
        assertFalse(cancelled.contains("editDraftModal"), "hợp đồng đã hủy không sửa được");
        assertFalse(cancelled.contains("/activate"), "hợp đồng đã hủy không kích hoạt được");
    }

    @Test
    @DisplayName("API: 200 / 409 (không còn nháp, version cũ) / 403 / 404 / 400")
    void apiStatusCodes() throws Exception {
        mockMvc.perform(patch("/api/contracts/" + draftId).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(terms("4500000", null)))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/contracts/" + draftId).header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(terms("4500000", null)))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/contracts/" + UUID.randomUUID()).header("X-User-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(terms("4500000", null)))
            .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/contracts/" + draftId).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(terms("0", null)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/contracts/" + draftId).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(terms("4500000", "0")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rentAmount").value(4500000))
            .andExpect(jsonPath("$.startDate").value("2026-12-15"))
            .andExpect(jsonPath("$.occupants[0].checkInDate").value("2026-12-15"));
        mockMvc.perform(patch("/api/contracts/" + draftId).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(terms("4600000", "999")))
            .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/contracts/" + ACTIVE).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(terms("4500000", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("trạng thái nháp")));
        mockMvc.perform(post("/api/contracts/" + ACTIVE + "/cancel").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/contracts/" + draftId + "/cancel").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contracts/" + draftId + "/cancel").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/contracts/" + draftId + "/cancel").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/contracts/" + draftId + "/activate").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
    }
}
