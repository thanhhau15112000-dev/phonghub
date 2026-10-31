package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm thử vị trí các nút hành động (lưu, gửi, duyệt, xác nhận, nghiệm thu)
 * phải nằm ở cuối trang sau toàn bộ nội dung cần xem (#253).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BottomActionButtonsUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String HANDOVER_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;

    @Test
    @DisplayName("Biên bản check-in: các nút hành động nằm trong thẻ #handover-actions ở cuối trang sau ảnh và tài sản")
    void handoverButtonsAtBottom() throws Exception {
        UUID actor = LocalDemoAuthenticationAdapter.OWNER_1_ID;
        // 1. Ở trạng thái DRAFT: nút Gửi cho khách thuê nằm sau ảnh bàn giao và tài sản
        Handover draft = Handover.draft(DataSeeder.CONTRACT_1_ID, HandoverType.CHECK_IN,
            BigDecimal.valueOf(100), BigDecimal.valueOf(20), "Phòng sạch", "2 chìa", actor);
        handovers.save(draft);

        String htmlDraft = mockMvc.perform(get(HANDOVER_PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Xem và xác nhận")))
            .andExpect(content().string(containsString("id=\"handover-actions\"")))
            .andExpect(content().string(containsString("Gửi cho khách thuê")))
            .andReturn().getResponse().getContentAsString();

        int photosIdx = htmlDraft.indexOf("Ảnh bàn giao");
        int assetsIdx = htmlDraft.indexOf("Tài sản bàn giao");
        int actionsIdx = htmlDraft.indexOf("id=\"handover-actions\"");

        assertTrue(photosIdx > 0, "Trang có mục Ảnh bàn giao");
        assertTrue(assetsIdx > 0, "Trang có mục Tài sản bàn giao");
        assertTrue(actionsIdx > photosIdx, "Thẻ xác nhận/gửi phải nằm sau Ảnh bàn giao");
        assertTrue(actionsIdx > assetsIdx, "Thẻ xác nhận/gửi phải nằm sau Tài sản bàn giao");

        // 2. Ở trạng thái PENDING_TENANT: nút Tôi xác nhận biên bản nằm trong #handover-actions ở cuối
        Handover pendingTenant = draft.send("hash-terms", "Nội quy phòng", "hash-rules", actor);
        handovers.save(pendingTenant);

        String htmlTenant = mockMvc.perform(get(HANDOVER_PAGE).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tôi xác nhận biên bản")))
            .andReturn().getResponse().getContentAsString();

        int tenantActionsIdx = htmlTenant.indexOf("id=\"handover-actions\"");
        int tenantPhotosIdx = htmlTenant.indexOf("Ảnh bàn giao");
        int tenantAssetsIdx = htmlTenant.indexOf("Tài sản bàn giao");

        assertTrue(tenantActionsIdx > tenantPhotosIdx, "Nút xác nhận của khách nằm sau ảnh bàn giao");
        assertTrue(tenantActionsIdx > tenantAssetsIdx, "Nút xác nhận của khách nằm sau tài sản bàn giao");

        // 3. Ở trạng thái PENDING_APPROVAL: nút Duyệt và Từ chối nằm trong #handover-actions ở cuối
        Handover pendingApproval = pendingTenant.tenantConfirm(LocalDemoAuthenticationAdapter.TENANT_1_ID);
        handovers.save(pendingApproval);

        String htmlApproval = mockMvc.perform(get(HANDOVER_PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Duyệt</button>")))
            .andExpect(content().string(containsString("Từ chối</button>")))
            .andReturn().getResponse().getContentAsString();

        int approvalActionsIdx = htmlApproval.indexOf("id=\"handover-actions\"");
        int approvalPhotosIdx = htmlApproval.indexOf("Ảnh bàn giao");
        int approvalAssetsIdx = htmlApproval.indexOf("Tài sản bàn giao");

        assertTrue(approvalActionsIdx > approvalPhotosIdx, "Nút duyệt của chủ trọ nằm sau ảnh");
        assertTrue(approvalActionsIdx > approvalAssetsIdx, "Nút duyệt của chủ trọ nằm sau tài sản");
    }

    @Test
    @DisplayName("Phiếu báo hỏng Nhánh A: thẻ duyệt của chủ trọ #owner-approval nằm sau Lịch sử và Ảnh/video")
    void maintenanceOwnerApprovalAtBottom() throws Exception {
        String body = "{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Vòi rò rỉ\","
            + "\"description\":\"Hao mòn tự nhiên\",\"priority\":\"MEDIUM\",\"setRoomMaintenance\":false,"
            + "\"cause\":\"NATURAL_WEAR\"}";
        String res = mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(res, "$.id");

        // Chốt nhánh A (chủ trọ chịu chi phí)
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/branch").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"branch\":\"OWNER\"}"))
            .andExpect(status().isOk());

        String html = mockMvc.perform(get("/maintenance/" + ticketId).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"owner-approval\"")))
            .andExpect(content().string(containsString("Duyệt xử lý")))
            .andReturn().getResponse().getContentAsString();

        int timelineIdx = html.indexOf("Lịch sử xử lý");
        int mediaIdx = html.indexOf("Ảnh và video đính kèm");
        int ownerApprovalIdx = html.indexOf("id=\"owner-approval\"");

        assertTrue(timelineIdx > 0, "Trang có mục Lịch sử xử lý");
        assertTrue(mediaIdx > 0, "Trang có mục Ảnh và video đính kèm");
        assertTrue(ownerApprovalIdx > timelineIdx, "Thẻ Duyệt xử lý phải nằm sau Lịch sử xử lý");
        assertTrue(ownerApprovalIdx > mediaIdx, "Thẻ Duyệt xử lý phải nằm sau Ảnh và video đính kèm");
    }

    @Test
    @DisplayName("Phiếu báo hỏng Nhánh B: thẻ phê duyệt báo giá #tenant-quote-actions nằm sau Lịch sử và Ảnh/video")
    void maintenanceTenantQuoteAtBottom() throws Exception {
        String body = "{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Vỡ gương\","
            + "\"description\":\"Do sơ suất\",\"priority\":\"HIGH\",\"setRoomMaintenance\":false,"
            + "\"cause\":\"TENANT_USAGE\"}";
        String res = mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(res, "$.id");

        // Chốt nhánh B
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/branch").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"branch\":\"TENANT\"}"))
            .andExpect(status().isOk());

        // Lập báo giá
        mockMvc.perform(post("/api/maintenance/" + ticketId + "/quote-versions").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"name\":\"Gương mới\",\"quantity\":1,\"unitPrice\":300000}],\"laborAmount\":50000,\"note\":\"Thay gương\"}"))
            .andExpect(status().isOk());

        String html = mockMvc.perform(get("/maintenance/" + ticketId).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"tenant-quote-actions\"")))
            .andExpect(content().string(containsString("Phê duyệt báo giá")))
            .andReturn().getResponse().getContentAsString();

        int timelineIdx = html.indexOf("Lịch sử xử lý");
        int mediaIdx = html.indexOf("Ảnh và video đính kèm");
        int quoteActionsIdx = html.indexOf("id=\"tenant-quote-actions\"");

        assertTrue(timelineIdx > 0, "Trang có mục Lịch sử xử lý");
        assertTrue(mediaIdx > 0, "Trang có mục Ảnh và video đính kèm");
        assertTrue(quoteActionsIdx > timelineIdx, "Thẻ Phê duyệt báo giá phải nằm sau Lịch sử xử lý");
        assertTrue(quoteActionsIdx > mediaIdx, "Thẻ Phê duyệt báo giá phải nằm sau Ảnh và video đính kèm");
    }

    @Test
    @DisplayName("Phiếu báo hỏng đã sửa: thẻ nghiệm thu #tenant-confirm nằm sau Lịch sử và Ảnh/video")
    void maintenanceTenantConfirmAtBottom() throws Exception {
        MaintenanceTicket resolved = new MaintenanceTicket(UUID.randomUUID(), DataSeeder.ROOM_101_ID, DataSeeder.PROP_1_ID,
            DataSeeder.TENANT_RECORD_ID, LocalDemoAuthenticationAdapter.TECH_1_ID, "Đã sửa quạt", "Quạt kêu",
            MaintenancePriority.LOW, MaintenanceStatus.RESOLVED, BigDecimal.valueOf(50000), "Đã tra dầu mỡ",
            null, LiableParty.OWNER, null, null, null, Instant.now(), Instant.now());
        tickets.save(resolved);

        String html = mockMvc.perform(get("/maintenance/" + resolved.getId()).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"tenant-confirm\"")))
            .andExpect(content().string(containsString("Xác nhận đã sửa xong")))
            .andReturn().getResponse().getContentAsString();

        int timelineIdx = html.indexOf("Lịch sử xử lý");
        int mediaIdx = html.indexOf("Ảnh và video đính kèm");
        int confirmIdx = html.indexOf("id=\"tenant-confirm\"");

        assertTrue(timelineIdx > 0, "Trang có mục Lịch sử xử lý");
        assertTrue(mediaIdx > 0, "Trang có mục Ảnh và video đính kèm");
        assertTrue(confirmIdx > timelineIdx, "Thẻ Xác nhận kết quả phải nằm sau Lịch sử xử lý");
        assertTrue(confirmIdx > mediaIdx, "Thẻ Xác nhận kết quả phải nằm sau Ảnh và video đính kèm");
    }
}
