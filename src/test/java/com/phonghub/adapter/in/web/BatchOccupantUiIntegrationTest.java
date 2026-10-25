package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Thêm nhiều người vào hợp đồng đang hoạt động qua giao diện (#198). */
class BatchOccupantUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryTenantRepository tenants;
    @Autowired private InMemoryContractRepository contracts;

    private Contract seededContract() {
        return contracts.findAll().stream().filter(Contract::isActive).findFirst().orElseThrow();
    }

    private Tenant waitingTenant(String name, String cccd, String phone) {
        return accountTenant(name, cccd, phone);
    }

    /** P104 chứa 3 người: hợp đồng mới cho người thuê có tài khoản, kích hoạt xong còn 2 chỗ trống. */
    private Contract activeContractInRoom104() throws Exception {
        Tenant owner = accountTenant("Chủ Phòng G", "079200005555", "0977888000");
        mockMvc.perform(post("/contracts").header("X-User-Id", OWNER_1)
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantId", owner.id().toString())
                .param("depositAmount", "4000000").param("rentAmount", "4000000")
                .param("startDate", "2026-11-01").param("endDate", "2027-10-31").param("paymentDay", "5"))
            .andExpect(status().is3xxRedirection());
        Contract contract = contracts.findAll().stream()
            .filter(c -> c.getRoomId().equals(DataSeeder.ROOM_104_ID)).findFirst().orElseThrow();
        activateWithApprovedCheckIn(contract.getId(), OWNER_1);
        return contract;
    }

    @Test
    @DisplayName("A single row that picks an existing profile (CCCD left empty) is accepted")
    void singleRowWithExistingProfile() throws Exception {
        Contract contract = seededContract();
        Tenant guest = waitingTenant("Phạm Thị D", "079200008888", "0933444666");

        mockMvc.perform(post("/contracts/" + contract.getId() + "/occupants/batch").header("X-User-Id", OWNER_1)
                .param("tenantProfileIds", guest.id().toString())
                .param("tenantUserIds", "")
                .param("fullNames", "Phạm Thị D")
                .param("identityCardNumbers", "")
                .param("phones", "0933444666")
                .param("emails", "")
                .param("permanentAddresses", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        Contract reloaded = contracts.findById(contract.getId()).orElseThrow();
        assertTrue(reloaded.getOccupants().stream().anyMatch(o -> o.tenantId().equals(guest.id())));
    }

    @Test
    @DisplayName("More rows than free slots are refused as a whole and nothing is saved")
    void moreRowsThanFreeSlotsIsRefused() throws Exception {
        Contract contract = seededContract();
        Tenant guest = waitingTenant("Đỗ Văn E", "079200009999", "0944555777");
        int before = contract.getOccupants().size();

        // P101 chứa tối đa 2 người và đã có 1 người: thêm 2 người nữa vượt sức chứa
        mockMvc.perform(post("/contracts/" + contract.getId() + "/occupants/batch").header("X-User-Id", OWNER_1)
                .param("tenantProfileIds", guest.id().toString()).param("tenantProfileIds", "")
                .param("tenantUserIds", "").param("tenantUserIds", "")
                .param("fullNames", "Đỗ Văn E").param("fullNames", "Võ Thị F")
                .param("identityCardNumbers", "").param("identityCardNumbers", "079200006666")
                .param("phones", "0944555777").param("phones", "0955666888")
                .param("emails", "").param("emails", "")
                .param("permanentAddresses", "").param("permanentAddresses", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(before, contracts.findById(contract.getId()).orElseThrow().getOccupants().size());
        assertTrue(tenants.findAll().stream().noneMatch(t -> "Võ Thị F".equals(t.fullName())));
    }

    @Test
    @DisplayName("Two rows that both have accounts are added together when the room has space")
    void twoRowsWithAccounts() throws Exception {
        Contract contract = activeContractInRoom104();
        Tenant first = waitingTenant("Đỗ Văn E", "079200009999", "0944555777");
        Tenant second = waitingTenant("Võ Thị F", "079200006666", "0955666888");
        int before = contracts.findById(contract.getId()).orElseThrow().getOccupants().size();

        mockMvc.perform(post("/contracts/" + contract.getId() + "/occupants/batch").header("X-User-Id", OWNER_1)
                .param("tenantProfileIds", first.id().toString()).param("tenantProfileIds", second.id().toString())
                .param("tenantUserIds", "").param("tenantUserIds", "")
                .param("fullNames", "Đỗ Văn E").param("fullNames", "Võ Thị F")
                .param("identityCardNumbers", "").param("identityCardNumbers", "")
                .param("phones", "0944555777").param("phones", "0955666888")
                .param("emails", "").param("emails", "")
                .param("permanentAddresses", "").param("permanentAddresses", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        assertEquals(before + 2, contracts.findById(contract.getId()).orElseThrow().getOccupants().size());
    }

    @Test
    @DisplayName("A batch cannot create new accounts for several people at once and saves nothing")
    void batchWithNewPersonNeedingAccountIsRefused() throws Exception {
        Contract contract = activeContractInRoom104();
        Tenant guest = waitingTenant("Đỗ Văn E", "079200009999", "0944555777");
        int before = contracts.findById(contract.getId()).orElseThrow().getOccupants().size();

        mockMvc.perform(post("/contracts/" + contract.getId() + "/occupants/batch").header("X-User-Id", OWNER_1)
                .param("tenantProfileIds", guest.id().toString()).param("tenantProfileIds", "")
                .param("tenantUserIds", "").param("tenantUserIds", "")
                .param("fullNames", "Đỗ Văn E").param("fullNames", "Võ Thị F")
                .param("identityCardNumbers", "").param("identityCardNumbers", "079200006666")
                .param("phones", "0944555777").param("phones", "0955666888")
                .param("emails", "").param("emails", "vo.f@test.local")
                .param("permanentAddresses", "").param("permanentAddresses", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(before, contracts.findById(contract.getId()).orElseThrow().getOccupants().size());
        assertTrue(tenants.findAll().stream().noneMatch(t -> "Võ Thị F".equals(t.fullName())));
    }

    @Test
    @DisplayName("A row with neither a profile nor a CCCD is refused and nothing is saved")
    void missingIdentityIsRefused() throws Exception {
        Contract contract = seededContract();
        int before = contract.getOccupants().size();

        mockMvc.perform(post("/contracts/" + contract.getId() + "/occupants/batch").header("X-User-Id", OWNER_1)
                .param("tenantProfileIds", "").param("tenantUserIds", "")
                .param("fullNames", "Người Lạ").param("identityCardNumbers", "").param("phones", "0966777999"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(before, contracts.findById(contract.getId()).orElseThrow().getOccupants().size());
        assertTrue(tenants.findAll().stream().noneMatch(t -> "Người Lạ".equals(t.fullName())));
    }
}
