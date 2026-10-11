package com.phonghub.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

class PropertyStaffApiIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String BASE = "/api/properties/" + DataSeeder.PROP_1_ID + "/staff";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private ResultActions assign(String usernameOrEmail, boolean payment, boolean contracts, String user) throws Exception {
        return mockMvc.perform(post(BASE).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"" + usernameOrEmail + "\",\"canCollectPayment\":" + payment
                + ",\"canManageContracts\":" + contracts + "}"));
    }

    private ResultActions permissions(String userId, boolean payment, boolean contracts, String user) throws Exception {
        return mockMvc.perform(put(BASE + "/" + userId).header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"canCollectPayment\":" + payment + ",\"canManageContracts\":" + contracts + "}"));
    }

    private int createContractAs(String user) throws Exception {
        ContractApiController.CreateContractRequest req = new ContractApiController.CreateContractRequest(
            DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, "Pham Van C", "079201003333", "0909112233", "c@local",
            null, new BigDecimal("4000000"), new BigDecimal("4000000"), LocalDate.now(),
            LocalDate.now().plusYears(1), 5);
        return mockMvc.perform(post("/api/contracts").header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
            .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Role x action matrix: only the property owner and admin manage the staff roster")
    void rosterAuthorizationMatrix() throws Exception {
        for (String allowed : new String[]{OWNER_1, ADMIN}) {
            mockMvc.perform(get(BASE).header("X-User-Id", allowed)).andExpect(status().isOk());
        }
        for (String denied : new String[]{OWNER_2, STAFF_1, STAFF_2, TECH_1, TENANT_1}) {
            mockMvc.perform(get(BASE).header("X-User-Id", denied)).andExpect(status().isForbidden());
            assign("staff2", true, true, denied).andExpect(status().isForbidden());
            permissions(STAFF_1, false, false, denied).andExpect(status().isForbidden());
            mockMvc.perform(delete(BASE + "/" + STAFF_1).header("X-User-Id", denied)).andExpect(status().isForbidden());
        }
        // Staff không tự nâng quyền của chính mình.
        permissions(STAFF_1, true, true, STAFF_1).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Assigning validates the target account and never reveals which accounts exist")
    void assignValidation() throws Exception {
        String generic = "Không tìm thấy tài khoản nhân viên hoặc kỹ thuật viên đang hoạt động với thông tin này";
        for (String bad : new String[]{"khong-ton-tai", "tenant1", "owner2", "admin"}) {
            assign(bad, true, true, OWNER_1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(generic));
        }
        assign("  ", true, true, OWNER_1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Owner assigns a staff member with limited permissions; re-assigning updates them without duplicates")
    void assignAndUpdate() throws Exception {
        assign("staff2", false, false, OWNER_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("STAFF"))
            .andExpect(jsonPath("$.canManageContracts").value(false))
            .andExpect(jsonPath("$.canCollectPayment").value(false));
        assign("staff2@phonghub.local", true, false, OWNER_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.canCollectPayment").value(true));
        permissions(STAFF_2, true, true, OWNER_1).andExpect(status().isOk())
            .andExpect(jsonPath("$.canManageContracts").value(true));
        // staff1, tech1 đã có sẵn + staff2 mới = 3 dòng, không nhân đôi.
        mockMvc.perform(get(BASE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        permissions(TENANT_1, true, true, OWNER_1).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Technicians never receive staff permissions")
    void technicianHasNoStaffPermissions() throws Exception {
        assign("tech1", true, true, OWNER_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("TECHNICIAN"))
            .andExpect(jsonPath("$.canManageContracts").value(false))
            .andExpect(jsonPath("$.canCollectPayment").value(false));
        permissions(TECH_1, false, true, OWNER_1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Staff permission flags are enforced: contracts need can_manage_contracts, scope follows the assignment")
    void contractPermissionIsEnforced() throws Exception {
        // staff2 chưa được phân công nhà trọ 1.
        org.junit.jupiter.api.Assertions.assertEquals(403, createContractAs(STAFF_2));

        assign("staff2", true, false, OWNER_1).andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(403, createContractAs(STAFF_2),
            "assigned staff without can_manage_contracts must be refused");
        // Vẫn xem được nhà trọ và các việc không cần quyền hợp đồng.
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/tenants").header("X-User-Id", STAFF_2))
            .andExpect(status().isOk());

        permissions(STAFF_2, true, true, OWNER_1).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(201, createContractAs(STAFF_2));
    }

    @Test
    @DisplayName("Unassigning removes access at once; a technician with open tickets cannot be removed (409)")
    void unassign() throws Exception {
        assign("staff2", true, true, OWNER_1).andExpect(status().isCreated());
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/tenants").header("X-User-Id", STAFF_2))
            .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/" + STAFF_2).header("X-User-Id", OWNER_1)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/properties/" + DataSeeder.PROP_1_ID + "/tenants").header("X-User-Id", STAFF_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + STAFF_2).header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());

        // Seed: TICKET_1 đang IN_PROGRESS với tech1 tại nhà trọ 1.
        mockMvc.perform(delete(BASE + "/" + TECH_1).header("X-User-Id", OWNER_1)).andExpect(status().isConflict());
    }
}
