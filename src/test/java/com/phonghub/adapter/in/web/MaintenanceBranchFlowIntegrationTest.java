package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.application.service.MaintenanceApprovalService;
import com.phonghub.application.service.MaintenanceApprovalService.ProcessResult;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Hai nhánh báo hỏng: hao mòn tự nhiên (chủ trọ duyệt 3 ngày, #202) và lỗi do người thuê (báo giá 7 ngày, #203). */
class MaintenanceBranchFlowIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;
    @Autowired private InMemoryNotificationRepository notifications;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private MaintenanceApprovalService approvalService;

    private String report(String cause) throws Exception {
        String body = "{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Vòi nước hỏng\","
            + "\"description\":\"Vòi bồn rửa rò nước\",\"priority\":\"MEDIUM\",\"setRoomMaintenance\":false,"
            + "\"cause\":\"" + cause + "\"}";
        String response = mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions branch(String id, String user, String branch, String reason) throws Exception {
        return mockMvc.perform(post("/api/maintenance/" + id + "/branch").header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"branch\":\"" + branch + "\"" + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}"));
    }

    private ResultActions act(String id, String path, String user) throws Exception {
        return mockMvc.perform(post("/api/maintenance/" + id + path).header("X-User-Id", user));
    }

    private ResultActions postJson(String id, String path, String user, String json) throws Exception {
        return mockMvc.perform(post("/api/maintenance/" + id + path).header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions assign(String id, String user) throws Exception {
        return postJson(id, "/assign", user, "{\"technicianId\":\"" + TECH_1 + "\"}");
    }

    private ResultActions submitQuote(String id, String user, String unitPrice, String labor) throws Exception {
        return postJson(id, "/quote-versions", user, "{\"lines\":[{\"name\":\"Vòi nước\",\"quantity\":2,\"unitPrice\":"
            + unitPrice + "}],\"laborAmount\":" + labor + ",\"note\":\"Thay vòi\"}");
    }

    private MaintenanceTicket reload(String id) {
        return tickets.findById(UUID.fromString(id)).orElseThrow();
    }

    /** Số thông báo của người dùng về đúng phiếu này (các test dùng chung kho thông báo nên lọc theo đường dẫn phiếu). */
    private long notificationsFor(String ticketId, String userId, String titleFragment) {
        return notifications.findAll().stream()
            .filter(n -> n.targetUserId().toString().equals(userId) && n.title().contains(titleFragment)
                && n.link() != null && n.link().endsWith(ticketId)).count();
    }

    // ---- Nhánh A: hao mòn tự nhiên

    @Test
    @DisplayName("Wear branch: staff confirm, only the owner approves, then the technician works and nobody is charged")
    void wearBranchHappyPath() throws Exception {
        String id = report("NATURAL_WEAR");

        branch(id, TECH_1, "OWNER", null).andExpect(status().isForbidden());
        branch(id, TENANT_1, "OWNER", null).andExpect(status().isForbidden());
        branch(id, OWNER_2, "OWNER", null).andExpect(status().isForbidden());
        assign(id, OWNER_1).andExpect(status().isConflict());

        branch(id, STAFF_1, "OWNER", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_OWNER_APPROVAL"))
            .andExpect(jsonPath("$.branchConfirmed").value(true))
            .andExpect(jsonPath("$.liableParty").value("OWNER"));
        Instant due = reload(id).getApprovalDueAt();
        assertTrue(Duration.between(Instant.now(), due).toHours() >= 71, "hạn duyệt 3 ngày");
        assertEquals(1, notificationsFor(id, OWNER_1, "chờ bạn duyệt"));
        assertEquals(1, notificationsFor(id, TENANT_1, "tiếp nhận"));
        assign(id, STAFF_1).andExpect(status().isBadRequest());

        act(id, "/owner-approve", STAFF_1).andExpect(status().isForbidden());
        act(id, "/owner-approve", OWNER_2).andExpect(status().isForbidden());
        act(id, "/owner-approve", OWNER_1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REPORTED"));
        act(id, "/owner-approve", OWNER_1).andExpect(status().isConflict());

        assign(id, STAFF_1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        act(id, "/accept", TECH_1).andExpect(status().isOk());
        postJson(id, "/resolve", TECH_1,
            "{\"resolutionNotes\":\"Đã thay vòi\",\"repairCost\":200000,\"releaseRoomToAvailable\":false}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        assertTrue(invoices.findActiveByTicketId(UUID.fromString(id)).isEmpty(), "nhánh A không tạo khoản phí cho người thuê");
        assertEquals(1, notificationsFor(id, TENANT_1, "đã được duyệt"));
    }

    @Test
    @DisplayName("Wear branch: the owner can reject a pending ticket with a reason; staff cannot reject it for the owner")
    void ownerRejectsPendingTicket() throws Exception {
        String id = report("NATURAL_WEAR");
        branch(id, STAFF_1, "OWNER", null).andExpect(status().isOk());

        postJson(id, "/reject", STAFF_1, "{\"reason\":\"Không thuộc phạm vi\"}").andExpect(status().isForbidden());
        postJson(id, "/owner-reject", STAFF_1, "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        postJson(id, "/owner-reject", OWNER_1, "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        postJson(id, "/owner-reject", OWNER_1, "{\"reason\":\"Hư do người thuê, không thuộc phạm vi\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        postJson(id, "/owner-reject", OWNER_1, "{\"reason\":\"Lần hai\"}").andExpect(status().is4xxClientError());
        assertEquals(1, notificationsFor(id, TENANT_1, "bị từ chối"));
    }

    @Test
    @DisplayName("Wear branch: no answer within 3 days escalates once to staff and technician, and the owner is told")
    void overdueOwnerApprovalEscalates() throws Exception {
        String id = report("NATURAL_WEAR");
        branch(id, STAFF_1, "OWNER", null).andExpect(status().isOk());
        UUID ticketId = UUID.fromString(id);
        Instant due = reload(id).getApprovalDueAt();

        assertEquals(ProcessResult.NO_ACTION, approvalService.processTicket(ticketId, due.minus(Duration.ofDays(2))
            .minusSeconds(1)));
        assertEquals(ProcessResult.REMINDED, approvalService.processTicket(ticketId, due.minus(Duration.ofHours(12))));
        assertEquals(ProcessResult.NO_ACTION, approvalService.processTicket(ticketId, due.minus(Duration.ofHours(6))),
            "chỉ nhắc một lần");
        assertEquals(MaintenanceStatus.PENDING_OWNER_APPROVAL, reload(id).getStatus());

        assertEquals(ProcessResult.ESCALATED, approvalService.processTicket(ticketId, due));
        assertEquals(ProcessResult.NO_ACTION, approvalService.processTicket(ticketId, due.plusSeconds(5)),
            "chạy lặp không chuyển hai lần");
        assertEquals(MaintenanceStatus.REPORTED, reload(id).getStatus());
        assertEquals(1, notificationsFor(id, OWNER_1, "quá hạn duyệt"));
        assertEquals(1, notificationsFor(id, TENANT_1, "được chuyển xử lý"));
        mockMvc.perform(get("/api/maintenance/" + id + "/timeline").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("MAINTENANCE_AUTO_ESCALATED")));

        assign(id, STAFF_1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        act(id, "/accept", TECH_1).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Staff can switch the branch with a reason; the tenant is told and the old approval state is reset")
    void staffSwitchesBranch() throws Exception {
        String id = report("NATURAL_WEAR");
        branch(id, STAFF_1, "TENANT", null).andExpect(status().isBadRequest());
        branch(id, STAFF_1, "TENANT", "Kiểm tra thấy vỡ do va đập").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPORTED"))
            .andExpect(jsonPath("$.liableParty").value("TENANT"));
        assertEquals(1, notificationsFor(id, TENANT_1, "phân loại lại"));

        branch(id, STAFF_1, "OWNER", "Đo lại thấy hao mòn").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_OWNER_APPROVAL"));
        branch(id, STAFF_1, "OWNER", "Lặp").andExpect(status().isConflict());
        branch(id, STAFF_1, "UNDETERMINED", "x").andExpect(status().isBadRequest());
    }

    // ---- Nhánh B: lỗi do người thuê

    @Test
    @DisplayName("Tenant-fault branch: quote with parts, negotiation, approval, capped cost, then the repair fee invoice")
    void tenantFaultHappyPath() throws Exception {
        String id = report("TENANT_USAGE");
        branch(id, STAFF_1, "TENANT", null).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPORTED"));
        assign(id, STAFF_1).andExpect(status().isOk());
        act(id, "/accept", TECH_1).andExpect(status().isConflict());

        submitQuote(id, TENANT_1, "150000", "0").andExpect(status().isForbidden());
        submitQuote(id, OWNER_2, "150000", "0").andExpect(status().isForbidden());
        postJson(id, "/quote-versions", TECH_1, "{\"lines\":[],\"laborAmount\":0}").andExpect(status().isBadRequest());
        submitQuote(id, TECH_1, "150000", "100000").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_TENANT_APPROVAL"))
            .andExpect(jsonPath("$.quoteVersion").value(1));
        Instant quoteDue = reload(id).getQuoteDueAt();
        assertTrue(Duration.between(Instant.now(), quoteDue).toHours() >= 167, "hạn phê duyệt 7 ngày");
        assertEquals(1, notificationsFor(id, TENANT_1, "báo giá cần bạn phê duyệt"));
        act(id, "/accept", TECH_1).andExpect(status().isConflict());

        mockMvc.perform(get("/api/maintenance/" + id + "/quote-versions").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].totalAmount").value(400000))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].lines[0].name").value("Vòi nước"));
        mockMvc.perform(get("/api/maintenance/" + id + "/quote-versions").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/maintenance/" + id + "/quote-versions").header("X-User-Id", ADMIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        act(id, "/quote/approve", STAFF_1).andExpect(status().isForbidden());
        postJson(id, "/quote/negotiate", TENANT_1, "{\"note\":\" \"}").andExpect(status().isBadRequest());
        postJson(id, "/quote/negotiate", TENANT_1, "{\"note\":\"Giảm công sửa còn 50.000\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        assertEquals(1, notificationsFor(id, STAFF_1, "thương lượng"));

        submitQuote(id, TECH_1, "150000", "50000").andExpect(status().isOk())
            .andExpect(jsonPath("$.quoteVersion").value(2));
        mockMvc.perform(get("/api/maintenance/" + id + "/quote-versions").header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("NEGOTIATED"))
            .andExpect(jsonPath("$[0].tenantNote").value("Giảm công sửa còn 50.000"))
            .andExpect(jsonPath("$[1].totalAmount").value(350000));

        act(id, "/quote/approve", TENANT_1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        act(id, "/quote/approve", TENANT_1).andExpect(status().isConflict());
        postJson(id, "/quote/negotiate", TENANT_1, "{\"note\":\"muộn\"}").andExpect(status().isConflict());
        act(id, "/accept", TECH_1).andExpect(status().isOk());

        postJson(id, "/resolve", TECH_1,
            "{\"resolutionNotes\":\"Đã thay vòi\",\"repairCost\":380000,\"releaseRoomToAvailable\":false}")
            .andExpect(status().isConflict());
        assertEquals(MaintenanceStatus.IN_PROGRESS, reload(id).getStatus());
        assertTrue(invoices.findActiveByTicketId(UUID.fromString(id)).isEmpty());

        postJson(id, "/resolve", TECH_1,
            "{\"resolutionNotes\":\"Đã thay vòi\",\"repairCost\":350000,\"releaseRoomToAvailable\":false}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        Invoice fee = invoices.findActiveByTicketId(UUID.fromString(id)).orElseThrow();
        assertEquals(0, new java.math.BigDecimal("350000").compareTo(fee.getTotalAmount()), "phí khớp báo giá đã duyệt");
    }

    @Test
    @DisplayName("Tenant-fault branch: only two negotiation rounds; then only approval or expiry")
    void negotiationLimit() throws Exception {
        String id = report("TENANT_USAGE");
        branch(id, STAFF_1, "TENANT", null).andExpect(status().isOk());
        for (int round = 1; round <= 2; round++) {
            submitQuote(id, STAFF_1, "100000", "0").andExpect(status().isOk());
            postJson(id, "/quote/negotiate", TENANT_1, "{\"note\":\"Giảm giá vòng " + round + "\"}")
                .andExpect(status().isOk());
        }
        submitQuote(id, STAFF_1, "90000", "0").andExpect(status().isOk());
        postJson(id, "/quote/negotiate", TENANT_1, "{\"note\":\"Vòng ba\"}").andExpect(status().isConflict());
        act(id, "/quote/approve", TENANT_1).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant-fault branch: no approval within 7 days cancels the ticket once, with no fee, and tells everyone")
    void unapprovedQuoteExpires() throws Exception {
        String id = report("TENANT_USAGE");
        branch(id, STAFF_1, "TENANT", null).andExpect(status().isOk());
        assign(id, STAFF_1).andExpect(status().isOk());
        submitQuote(id, TECH_1, "150000", "0").andExpect(status().isOk());
        UUID ticketId = UUID.fromString(id);
        Instant due = reload(id).getQuoteDueAt();

        assertEquals(ProcessResult.NO_ACTION, approvalService.processTicket(ticketId, due.minus(Duration.ofDays(3))));
        assertEquals(ProcessResult.REMINDED, approvalService.processTicket(ticketId, due.minus(Duration.ofDays(1))));
        assertEquals(1, notificationsFor(id, TENANT_1, "Nhắc phê duyệt"));
        assertEquals(MaintenanceStatus.PENDING_TENANT_APPROVAL, reload(id).getStatus());

        assertEquals(ProcessResult.NO_ACTION, approvalService.processTicket(ticketId, due.minusSeconds(1)));
        assertEquals(ProcessResult.QUOTE_EXPIRED, approvalService.processTicket(ticketId, due));
        assertEquals(ProcessResult.NO_ACTION, approvalService.processTicket(ticketId, due.plusSeconds(60)));
        assertEquals(MaintenanceStatus.CANCELLED, reload(id).getStatus());
        assertTrue(invoices.findActiveByTicketId(ticketId).isEmpty());
        assertEquals(MaintenanceQuoteVersion.Status.EXPIRED, tickets.findQuoteVersions(ticketId).getFirst().status());
        assertEquals(1, notificationsFor(id, TENANT_1, "bị hủy do hết hạn"));
        assertEquals(1, notificationsFor(id, STAFF_1, "bị hủy do hết hạn"));
        assertEquals(1, notificationsFor(id, TECH_1, "bị hủy do hết hạn"));
        mockMvc.perform(get("/api/maintenance/" + id + "/timeline").header("X-User-Id", TENANT_1))
            .andExpect(content().string(containsString("MAINTENANCE_QUOTE_EXPIRED")));
        act(id, "/quote/approve", TENANT_1).andExpect(status().isConflict());
    }

    // ---- Giao diện

    @Test
    @DisplayName("The ticket page shows the branch step, the owner approval and the quote actions to the right people")
    void ticketPageShowsEachStep() throws Exception {
        String wear = report("NATURAL_WEAR");
        mockMvc.perform(get("/maintenance/" + wear).header("X-User-Id", STAFF_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"branch-card\"")))
            .andExpect(content().string(containsString("Cần chốt nhánh xử lý")));
        branch(wear, STAFF_1, "OWNER", null).andExpect(status().isOk());
        mockMvc.perform(get("/maintenance/" + wear).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("id=\"owner-approval\"")))
            .andExpect(content().string(containsString("Duyệt xử lý")));
        mockMvc.perform(get("/maintenance/" + wear).header("X-User-Id", STAFF_1))
            .andExpect(content().string(containsString("Chỉ chủ trọ duyệt hoặc từ chối được ở bước này.")));
        mockMvc.perform(get("/maintenance/" + wear).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chờ chủ trọ duyệt")));

        String fault = report("TENANT_USAGE");
        branch(fault, STAFF_1, "TENANT", null).andExpect(status().isOk());
        mockMvc.perform(get("/maintenance/" + fault).header("X-User-Id", STAFF_1))
            .andExpect(content().string(containsString("id=\"quote-submit-form\"")));
        submitQuote(fault, STAFF_1, "150000", "50000").andExpect(status().isOk());
        mockMvc.perform(get("/maintenance/" + fault).header("X-User-Id", TENANT_1))
            .andExpect(content().string(containsString("id=\"tenant-quote-actions\"")))
            .andExpect(content().string(containsString("Phê duyệt báo giá")))
            .andExpect(content().string(containsString("Vòi nước")));
        mockMvc.perform(get("/maintenance/" + fault).header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"quote-b\""))));
        assertFalse(reload(fault).canStartWork());

        mockMvc.perform(post("/maintenance/" + fault + "/quote/negotiate").header("X-User-Id", TENANT_1)
                .param("note", "Giảm giá giúp em"))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/maintenance/" + fault + "/quote-detail").header("X-User-Id", STAFF_1)
                .param("itemNames", "Vòi nước").param("itemQuantities", "1").param("itemPrices", "120000")
                .param("laborAmount", "30000").param("note", "Giá mới"))
            .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/maintenance/" + fault + "/quote/approve").header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection());
        assertEquals(0, new java.math.BigDecimal("150000").compareTo(reload(fault).getApprovedQuoteTotal()));
        assertTrue(reload(fault).canStartWork());
    }
}
