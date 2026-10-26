package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.in.web.api.ContractApiController;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Các quy tắc nghiệp vụ nằm giữa nhiều tính năng (#69, #70, #71) mà test từng tính năng không chạm tới. */
class CrossFeatureBusinessRulesTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANTS = "/api/properties/" + DataSeeder.PROP_1_ID + "/tenants";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String addAndHide(String cccd) throws Exception {
        String body = "{\"fullName\":\"Khách Quay Lại\",\"identityCardNumber\":\"" + cccd
            + "\",\"phone\":\"0933000111\",\"email\":\"tenant" + cccd.substring(cccd.length() - 4)
            + "@example.test\"}";
        String response = mockMvc.perform(post(TENANTS).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        mockMvc.perform(post(TENANTS + "/" + id + "/hide").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        return id;
    }

    private void createDraftContractFor(String tenantId) throws Exception {
        ContractApiController.CreateContractRequest req = contractRequest(tenantId);
        mockMvc.perform(post("/api/contracts").header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }

    private ContractApiController.CreateContractRequest contractRequest(String tenantId) {
        return new ContractApiController.CreateContractRequest(
            DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, java.util.UUID.fromString(tenantId),
            new BigDecimal("4000000"), new BigDecimal("4000000"), LocalDate.now(), LocalDate.now().plusYears(1), 5);
    }

    @Test
    @DisplayName("A hidden tenant who gets a contract reappears in the list, and cannot be hidden while it is open")
    void hiddenTenantWithNewContractIsVisible() throws Exception {
        String id = addAndHide("079300000111");
        assertTrue(!mockMvc.perform(get(TENANTS).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString().contains("Khách Quay Lại"));

        mockMvc.perform(post("/api/contracts").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(contractRequest(id))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(TENANTS + "/" + id + "/restore").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        createDraftContractFor(id);

        mockMvc.perform(get(TENANTS + "/" + id).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hidden").value(false))
            .andExpect(jsonPath("$.hasActiveContract").value(true));
        assertTrue(mockMvc.perform(get(TENANTS).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString().contains("Khách Quay Lại"));
        mockMvc.perform(post(TENANTS + "/" + id + "/hide").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Staff without the contract permission cannot read handover reports but still manages tenants")
    void staffWithoutContractPermission() throws Exception {
        mockMvc.perform(post("/api/properties/" + DataSeeder.PROP_1_ID + "/staff/" + STAFF_1 + "/noop")
            .header("X-User-Id", OWNER_1)).andExpect(status().is4xxClientError());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/properties/" + DataSeeder.PROP_1_ID + "/staff/" + STAFF_1)
                .header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"canCollectPayment\":true,\"canManageContracts\":false}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover-comparison")
            .header("X-User-Id", STAFF_1)).andExpect(status().isForbidden());
        mockMvc.perform(get(TENANTS).header("X-User-Id", STAFF_1)).andExpect(status().isOk());
    }
}
