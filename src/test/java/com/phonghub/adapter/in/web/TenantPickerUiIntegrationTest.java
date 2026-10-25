package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Tenant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Bộ chọn người thuê và màn hình nhà trọ sau các sửa UI/UX của #198. */
class TenantPickerUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String SEEDED_CCCD = "079201001111";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryTenantRepository tenants;
    @Autowired private InMemoryContractRepository contracts;

    private String page(String path) throws Exception {
        return mockMvc.perform(get(path).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Tenant pickers never put the full CCCD in the page and hide people who already live in a room")
    void pickersHideFullIdentityNumber() throws Exception {
        accountTenant("Phạm Thị D", "079200008888", "0933444666");
        String newContract = page("/contracts/new?propertyId=" + DataSeeder.PROP_1_ID);
        assertTrue(newContract.contains("Phạm Thị D"), "người thuê có tài khoản và chưa ở đâu được liệt kê");
        assertFalse(newContract.contains("079200008888"));
        assertTrue(newContract.contains("****8888"));
        assertFalse(newContract.contains(SEEDED_CCCD), "người đang ở theo hợp đồng không hiện trong ô chọn");

        assertFalse(page("/properties/" + DataSeeder.PROP_1_ID + "/tenants/new").contains(SEEDED_CCCD));
        Contract seeded = contracts.findAll().stream().filter(Contract::isActive).findFirst().orElseThrow();
        assertFalse(page("/contracts/" + seeded.getId()).contains(SEEDED_CCCD));
    }

    @Test
    @DisplayName("Choosing an existing profile creates the contract without typing the CCCD again")
    void contractFromProfileWithoutIdentityNumber() throws Exception {
        Tenant newcomer = accountTenant("Phạm Thị D", "079200008888", "0933444666");
        int before = contracts.findAll().size();

        mockMvc.perform(post("/contracts").header("X-User-Id", OWNER_1)
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantId", newcomer.id().toString())
                .param("depositAmount", "4000000").param("rentAmount", "4000000")
                .param("startDate", "2026-11-01").param("endDate", "2027-10-31").param("paymentDay", "5"))
            .andExpect(status().is3xxRedirection());

        assertEquals(before + 1, contracts.findAll().size());
        assertTrue(contracts.findAll().stream().anyMatch(c -> c.getPrimaryTenantId().equals(newcomer.id())));
    }

    @Test
    @DisplayName("The occupant dialog keeps both ways to add many people: tick existing profiles, or type rows")
    void occupantDialogKeepsBothWaysToAddPeople() throws Exception {
        Tenant roomOwner = accountTenant("Chủ Phòng G", "079200005555", "0977888000");
        mockMvc.perform(post("/contracts").header("X-User-Id", OWNER_1)
                .param("propertyId", DataSeeder.PROP_1_ID.toString())
                .param("roomId", DataSeeder.ROOM_104_ID.toString())
                .param("primaryTenantId", roomOwner.id().toString())
                .param("depositAmount", "4000000").param("rentAmount", "4000000")
                .param("startDate", "2026-11-01").param("endDate", "2027-10-31").param("paymentDay", "5"))
            .andExpect(status().is3xxRedirection());
        Contract contract = contracts.findAll().stream()
            .filter(c -> c.getRoomId().equals(DataSeeder.ROOM_104_ID)).findFirst().orElseThrow();
        activateWithApprovedCheckIn(contract.getId(), OWNER_1);
        Tenant waiting = accountTenant("Phạm Thị D", "079200008888", "0933444666");

        String html = page("/contracts/" + contract.getId());

        // cách của Hậu: tích chọn nhiều hồ sơ có sẵn trong nhà trọ, gửi một lần
        assertTrue(html.contains("id=\"bulkOccupantForm\""));
        assertTrue(html.contains("/occupants/bulk"));
        assertTrue(html.contains("id=\"bulkOccupantCheckInDate\""), "đường tích chọn cũng cho chọn ngày nhận phòng");
        assertTrue(html.contains(waiting.id().toString()), "hồ sơ khả dụng nằm trong danh sách tích chọn");
        // cách của mình: thêm 1 người qua bộ chọn, hoặc nhập nhiều dòng (có thể có người mới)
        assertTrue(html.contains("data-occupant-panel=\"single\""));
        assertTrue(html.contains("/occupants/batch"));
        assertTrue(html.contains("value=\"" + LocalDate.now() + "\""), "ngày nhận phòng ở định dạng yyyy-MM-dd của ô date");
        assertFalse(html.contains("079200008888"), "CCCD đầy đủ không đưa ra trình duyệt");
    }

    @Test
    @DisplayName("Property page labels rooms that cannot take people and lists tenants without a room")
    void propertyPageShowsRoomContext() throws Exception {
        Tenant waiting = tenants.save(Tenant.create(null, "Đỗ Văn E", "079200009999", "0944555777", null, null));
        tenants.linkToProperty(waiting.id(), DataSeeder.PROP_1_ID, LocalDemoAuthenticationAdapter.OWNER_1_ID);

        String html = page("/properties/" + DataSeeder.PROP_1_ID);

        assertTrue(html.contains("Đang bảo trì, tạm không nhận người"));
        assertTrue(html.contains("Đã giữ chỗ, chờ ký hợp đồng"));
        assertTrue(html.contains("Người thuê chưa có phòng (1)"));
        assertTrue(html.contains("Đỗ Văn E"));
        assertTrue(html.contains("tenantProfileId=" + waiting.id()));
    }
}
