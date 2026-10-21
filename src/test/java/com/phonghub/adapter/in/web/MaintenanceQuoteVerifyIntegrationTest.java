package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * #83: báo giá và người thuê xác nhận kết quả. Phiếu mẫu TICKET_1 ở phòng P102 (không phải phòng của TENANT_1);
 * các phiếu ở phòng P101 của TENANT_1 được tạo trong test.
 */
class MaintenanceQuoteVerifyIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String SEED_TICKET = "/api/maintenance/" + DataSeeder.TICKET_1_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;
    @Autowired private InMemoryInvoiceRepository invoices;

    @BeforeEach
    @AfterEach
    void clearInvoices() {
        invoices.clear();
    }

    private MaintenanceTicket tenantRoomTicket(MaintenanceStatus status, LiableParty liable) {
        return tickets.save(new MaintenanceTicket(UUID.randomUUID(), DataSeeder.ROOM_101_ID, DataSeeder.PROP_1_ID,
            DataSeeder.TENANT_RECORD_ID, LocalDemoAuthenticationAdapter.TECH_1_ID, "Khóa cửa kẹt", "Khó mở",
            MaintenancePriority.MEDIUM, status, BigDecimal.ZERO, null, null, liable, null, null, null, Instant.now(),
            Instant.now()));
    }

    private static String quote(String amount) {
        return "{\"amount\": " + amount + ", \"note\": \"Thay vòi\"}";
    }

    @Test
    @DisplayName("báo giá: kỹ thuật viên được giao, owner, staff, admin lưu được; người ngoài và người thuê bị 403")
    void quotePermissions() throws Exception {
        for (String user : new String[] {TECH_1, OWNER_1, STAFF_1, ADMIN}) {
            mockMvc.perform(post(SEED_TICKET + "/quote").header("X-User-Id", user)
                    .contentType(MediaType.APPLICATION_JSON).content(quote("450000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(450000))
                .andExpect(jsonPath("$.note").value("Thay vòi"));
        }
        for (String user : new String[] {OWNER_2, STAFF_2, TENANT_1}) {
            mockMvc.perform(post(SEED_TICKET + "/quote").header("X-User-Id", user)
                    .contentType(MediaType.APPLICATION_JSON).content(quote("1")))
                .andExpect(status().isForbidden());
        }
        assertEquals(new BigDecimal("450000"), tickets.findById(DataSeeder.TICKET_1_ID).orElseThrow().getQuote().amount(),
            "bị từ chối thì báo giá không đổi");
        assertEquals(MaintenanceStatus.IN_PROGRESS, tickets.findById(DataSeeder.TICKET_1_ID).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("báo giá: số tiền thiếu/âm trả 400, phiếu đã xong trả 409")
    void quoteValidation() throws Exception {
        mockMvc.perform(post(SEED_TICKET + "/quote").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(quote("-5")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(SEED_TICKET + "/quote").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"x\"}"))
            .andExpect(status().isBadRequest());
        MaintenanceTicket done = tenantRoomTicket(MaintenanceStatus.RESOLVED, LiableParty.OWNER);
        mockMvc.perform(post("/api/maintenance/" + done.getId() + "/quote").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(quote("1")))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("người thuê chỉ thấy báo giá khi mình là bên chịu phí; người quản lý luôn thấy")
    void quoteVisibility() throws Exception {
        MaintenanceTicket ownerPays = tenantRoomTicket(MaintenanceStatus.IN_PROGRESS, LiableParty.OWNER);
        MaintenanceTicket tenantPays = tenantRoomTicket(MaintenanceStatus.IN_PROGRESS, LiableParty.TENANT);
        for (MaintenanceTicket t : new MaintenanceTicket[] {ownerPays, tenantPays}) {
            mockMvc.perform(post("/api/maintenance/" + t.getId() + "/quote").header("X-User-Id", TECH_1)
                .contentType(MediaType.APPLICATION_JSON).content(quote("300000"))).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/maintenance/" + ownerPays.getId() + "/quote").header("X-User-Id", TENANT_1))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/maintenance/" + tenantPays.getId() + "/quote").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(300000));
        mockMvc.perform(get("/api/maintenance/" + ownerPays.getId() + "/quote").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/maintenance/" + ownerPays.getId() + "/quote").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(SEED_TICKET + "/quote").header("X-User-Id", TENANT_1))
            .andExpect(status().isForbidden());

        String tenantPage = mockMvc.perform(get("/maintenance/" + ownerPays.getId()).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(tenantPage.contains("id=\"quote-detail\""), "người thuê không chịu phí không thấy báo giá");
        String tenantPaysPage = mockMvc.perform(get("/maintenance/" + tenantPays.getId()).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(tenantPaysPage.contains("id=\"quote-detail\""));
        assertTrue(tenantPaysPage.contains("300.000 đ"));
        assertFalse(tenantPaysPage.contains("id=\"quote-form\""), "người thuê không nhập báo giá");
        String ownerPage = mockMvc.perform(get("/maintenance/" + ownerPays.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(ownerPage.contains("id=\"quote-form\""));
        assertTrue(ownerPage.contains("Cập nhật báo giá"));
    }

    @Test
    @DisplayName("người thuê xác nhận đã sửa xong → VERIFIED; vai trò khác 403; xác nhận lại hoặc chưa xong 409")
    void verify() throws Exception {
        MaintenanceTicket resolved = tenantRoomTicket(MaintenanceStatus.RESOLVED, LiableParty.OWNER);
        String url = "/api/maintenance/" + resolved.getId() + "/verify";
        for (String user : new String[] {OWNER_1, STAFF_1, TECH_1, ADMIN}) {
            mockMvc.perform(post(url).header("X-User-Id", user)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(url).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VERIFIED"));
        mockMvc.perform(post(url).header("X-User-Id", TENANT_1)).andExpect(status().isConflict());

        MaintenanceTicket inProgress = tenantRoomTicket(MaintenanceStatus.IN_PROGRESS, LiableParty.OWNER);
        mockMvc.perform(post("/api/maintenance/" + inProgress.getId() + "/verify").header("X-User-Id", TENANT_1))
            .andExpect(status().isConflict());
        // phiếu phòng khác (không phải người báo, không ở phòng đó)
        mockMvc.perform(post(SEED_TICKET + "/verify").header("X-User-Id", TENANT_1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("phản hồi chưa đạt: bắt buộc lý do, phiếu về ASSIGNED; vai trò khác 403")
    void reopen() throws Exception {
        MaintenanceTicket resolved = tenantRoomTicket(MaintenanceStatus.RESOLVED, LiableParty.OWNER);
        String url = "/api/maintenance/" + resolved.getId() + "/reopen";
        mockMvc.perform(post(url).header("X-User-Id", TENANT_1).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\": \"x\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(url).header("X-User-Id", TENANT_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\": \"Khóa vẫn kẹt\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"));
        mockMvc.perform(get("/api/maintenance/" + resolved.getId() + "/timeline").header("X-User-Id", TENANT_1))
            .andExpect(jsonPath("$[-1:].note").value(org.hamcrest.Matchers.hasItem("Người thuê phản hồi chưa đạt: Khóa vẫn kẹt")))
            .andExpect(jsonPath("$[-1:].actorId").value(org.hamcrest.Matchers.hasItem(TENANT_1)));
    }

    @Test
    @DisplayName("sửa lại sau phản hồi chưa đạt: phí đã trả thì không thu lần hai, phiếu hoàn tất luôn")
    void reworkDoesNotChargeTwice() throws Exception {
        MaintenanceTicket ticket = tenantRoomTicket(MaintenanceStatus.IN_PROGRESS, LiableParty.TENANT);
        String base = "/api/maintenance/" + ticket.getId();
        String resolve = "{\"resolutionNotes\": \"Thay ổ khóa\", \"repairCost\": 200000, \"releaseRoomToAvailable\": false}";

        mockMvc.perform(post(base + "/resolve").header("X-User-Id", TECH_1)
                .contentType(MediaType.APPLICATION_JSON).content(resolve))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        Invoice fee = invoices.findActiveByTicketId(ticket.getId()).orElseThrow();
        fee.applyPayment(new BigDecimal("200000"), Instant.now());
        invoices.save(fee);
        MaintenanceTicket awaiting = tickets.findById(ticket.getId()).orElseThrow();
        awaiting.markFeePaid();
        tickets.save(awaiting);

        mockMvc.perform(post(base + "/reopen").header("X-User-Id", TENANT_1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"feedback\": \"Khóa mới vẫn kẹt\"}"))
            .andExpect(jsonPath("$.status").value("ASSIGNED"));
        mockMvc.perform(post(base + "/resolve").header("X-User-Id", TECH_1)
                .contentType(MediaType.APPLICATION_JSON).content(resolve))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertEquals(1, invoices.findByContractId(DataSeeder.CONTRACT_1_ID).stream()
            .filter(i -> ticket.getId().equals(i.getTicketId())).count(), "không phát hành phí lần hai");
        mockMvc.perform(post(base + "/verify").header("X-User-Id", TENANT_1))
            .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("giao diện: người thuê thấy khối xác nhận khi phiếu đã sửa xong và gửi được xác nhận")
    void tenantConfirmUi() throws Exception {
        MaintenanceTicket resolved = tenantRoomTicket(MaintenanceStatus.RESOLVED, LiableParty.OWNER);
        String page = mockMvc.perform(get("/maintenance/" + resolved.getId()).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("id=\"tenant-confirm\""));
        String ownerPage = mockMvc.perform(get("/maintenance/" + resolved.getId()).header("X-User-Id", OWNER_1))
            .andReturn().getResponse().getContentAsString();
        assertFalse(ownerPage.contains("id=\"tenant-confirm\""));

        mockMvc.perform(post("/maintenance/" + resolved.getId() + "/reopen").header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post("/maintenance/" + resolved.getId() + "/verify").header("X-User-Id", OWNER_1))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post("/maintenance/" + resolved.getId() + "/verify").header("X-User-Id", TENANT_1))
            .andExpect(flash().attributeExists("successMessage"));
        assertEquals(MaintenanceStatus.VERIFIED, tickets.findById(resolved.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/maintenance/" + DataSeeder.TICKET_1_ID + "/quote").header("X-User-Id", OWNER_2)
                .param("amount", "100000"))
            .andExpect(flash().attributeExists("errorMessage"));
        assertNull(tickets.findById(DataSeeder.TICKET_1_ID).orElseThrow().getQuote());
        mockMvc.perform(post("/maintenance/" + DataSeeder.TICKET_1_ID + "/quote").header("X-User-Id", OWNER_1)
                .param("amount", "100000").param("note", "Thay gioăng"))
            .andExpect(flash().attributeExists("successMessage"));
        assertEquals(new BigDecimal("100000"), tickets.findById(DataSeeder.TICKET_1_ID).orElseThrow().getQuote().amount());
    }
}
