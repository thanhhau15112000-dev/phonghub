package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.domain.model.Invoice;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** #208: kỳ tạo tay sau ngày thanh toán không bị quá hạn ngay khi tạo. */
class InvoiceDueDateIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String CONTRACT_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryNotificationRepository notifications;

    // Kho hóa đơn dùng chung giữa các test cùng context: dọn cả trước lẫn sau để chỉ còn kỳ do test này tạo.
    // Dọn cả thông báo: chuông thông báo trên header của mọi trang có thể chứa "Quá hạn" do test khác để lại
    // (ví dụ phiếu bảo trì quá hạn xử lý), làm câu kiểm tra "trang không báo quá hạn" sai theo thứ tự chạy (#259).
    @BeforeEach
    @AfterEach
    void clearInvoicesAndNotifications() {
        invoices.clear();
        notifications.clear();
    }

    @Test
    @DisplayName("owner tạo kỳ tháng trước (đã qua ngày thanh toán 5): hạn là hôm nay + 3 ngày, trang hợp đồng không báo quá hạn")
    void periodIssuedAfterPaymentDayIsNotOverdue() throws Exception {
        // Hợp đồng mẫu bắt đầu từ 2 tháng trước, ngày thanh toán 5: kỳ tháng trước đã qua ngày thanh toán.
        YearMonth lastMonth = YearMonth.now(VIETNAM).minusMonths(1);
        LocalDate before = LocalDate.now(VIETNAM);

        mockMvc.perform(post(CONTRACT_PAGE + "/invoices").header("X-User-Id", OWNER_1)
                .param("period", lastMonth.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        LocalDate after = LocalDate.now(VIETNAM);
        List<Invoice> issued = invoices.findByContractId(DataSeeder.CONTRACT_1_ID);
        assertEquals(1, issued.size());
        assertEquals(lastMonth, YearMonth.of(issued.getFirst().getYear(), issued.getFirst().getMonth()));
        LocalDate due = issued.getFirst().getDueDate();
        assertTrue(due.equals(before.plusDays(Invoice.MIN_DAYS_TO_PAY)) || due.equals(after.plusDays(Invoice.MIN_DAYS_TO_PAY)),
            "hạn " + due + " phải là ngày tạo + 3");
        assertFalse(issued.getFirst().isOverdue(after));

        mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Quá hạn"))));
    }
}
