package com.phonghub.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Đưa phiếu bảo trì của người thuê qua bước chốt nhánh và duyệt (#202, #203) để test các bước sau (giao việc, sửa, thu
 * phí) không phải lặp lại luồng này. Dùng API nên chạy được cho mọi người quản lý có quyền trên nhà trọ.
 */
final class MaintenanceTestFlow {

    private MaintenanceTestFlow() {
    }

    /** Nhánh A: staff chốt hao mòn tự nhiên, chủ trọ duyệt. Sau đó phiếu giao việc và sửa được, chủ trọ chịu phí. */
    static void wearReady(MockMvc mvc, String ticketId, String managerId) throws Exception {
        confirmBranch(mvc, ticketId, managerId, "OWNER");
        mvc.perform(post("/api/maintenance/" + ticketId + "/owner-approve").header("X-User-Id", managerId))
            .andExpect(status().isOk());
    }

    /**
     * Nhánh B: staff chốt lỗi do người thuê, người quản lý gửi báo giá một dòng {@code approvedTotal}, người thuê phê
     * duyệt. Sau đó sửa được với chi phí tối đa bằng {@code approvedTotal}.
     */
    static void tenantFaultReady(MockMvc mvc, String ticketId, String managerId, String tenantId, long approvedTotal)
        throws Exception {
        confirmBranch(mvc, ticketId, managerId, "TENANT");
        submitQuote(mvc, ticketId, managerId, approvedTotal);
        mvc.perform(post("/api/maintenance/" + ticketId + "/quote/approve").header("X-User-Id", tenantId))
            .andExpect(status().isOk());
    }

    static void confirmBranch(MockMvc mvc, String ticketId, String managerId, String branch) throws Exception {
        mvc.perform(post("/api/maintenance/" + ticketId + "/branch").header("X-User-Id", managerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branch\":\"" + branch + "\",\"reason\":\"Chốt nhánh khi tiếp nhận\"}"))
            .andExpect(status().isOk());
    }

    static void submitQuote(MockMvc mvc, String ticketId, String userId, long total) throws Exception {
        mvc.perform(post("/api/maintenance/" + ticketId + "/quote-versions").header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"name\":\"Linh kiện thay thế\",\"quantity\":1,\"unitPrice\":" + total
                    + "}],\"laborAmount\":0,\"note\":\"Báo giá\"}"))
            .andExpect(status().isOk());
    }
}
