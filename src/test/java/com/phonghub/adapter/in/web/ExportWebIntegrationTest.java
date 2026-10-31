package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** #85: tải tệp xuất dữ liệu qua API và giao diện trên dữ liệu mẫu (hợp đồng mẫu ở PROP_1, CCCD 079201001111). */
class ExportWebIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryInvoiceRepository invoices;

    /** Dữ liệu mẫu chưa có hóa đơn; xuất rỗng bị chặn (#227) nên cần một hóa đơn còn nợ của tháng hiện tại. */
    @BeforeEach
    void seedInvoice() {
        LocalDate today = LocalDate.now();
        invoices.save(new Invoice(UUID.randomUUID(), DataSeeder.CONTRACT_1_ID, DataSeeder.ROOM_101_ID,
            DataSeeder.TENANT_RECORD_ID, today.getMonthValue(), today.getYear(), new BigDecimal("3000000"),
            new BigDecimal("3000000"), BigDecimal.ZERO, today.plusDays(5), InvoiceStatus.ISSUED, null, InvoiceType.RENT,
            null, null, Instant.now(), Instant.now()));
    }

    @AfterEach
    void clearInvoices() {
        invoices.clear();
    }

    @Test
    @DisplayName("API: owner tải danh sách hợp đồng Excel, CCCD bị che, tệp đính kèm có tên")
    void ownerDownloadsContracts() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/exports/contracts").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", XLSX))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.startsWith("attachment; filename=\"hop-dong_")))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(4);
            assertEquals("Nhà trọ Xanh - Quận 7", row.getCell(0).getStringCellValue());
            assertEquals("P101", row.getCell(1).getStringCellValue());
            assertEquals("****1111", row.getCell(3).getStringCellValue());
            StringBuilder all = new StringBuilder();
            sheet.forEach(r -> r.forEach(c -> all.append(c.toString()).append('|')));
            assertFalse(all.toString().contains("079201001111"), "không có CCCD đầy đủ");
        }
    }

    @Test
    @DisplayName("API: staff có quyền tải PDF công nợ; kỹ thuật viên, người thuê, owner nhà khác bị 403")
    void permissions() throws Exception {
        byte[] pdf = mockMvc.perform(get("/api/exports/debts").param("format", "pdf").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));

        for (String user : new String[] {TECH_1, TENANT_1}) {
            for (String dataset : new String[] {"invoices", "debts", "contracts"}) {
                mockMvc.perform(get("/api/exports/" + dataset).header("X-User-Id", user))
                    .andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(get("/api/exports/invoices").param("propertyId", DataSeeder.PROP_1_ID.toString())
                .header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API: loại dữ liệu, định dạng, khoảng tháng sai trả 400")
    void invalidParameters() throws Exception {
        mockMvc.perform(get("/api/exports/salaries").header("X-User-Id", OWNER_1)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/exports/invoices").param("format", "csv").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/exports/invoices").param("from", "2026-09").param("to", "2026-01")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/exports/invoices").param("from", "2026-13").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("giao diện: trang xuất dữ liệu, tải tệp, lỗi quyền quay về trang xuất; nút trên trang hợp đồng")
    void ui() throws Exception {
        String page = mockMvc.perform(get("/exports").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("id=\"exportForm\""));

        mockMvc.perform(get("/exports/download").param("dataset", "invoices").param("format", "xlsx")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", XLSX));

        mockMvc.perform(get("/exports/download").param("dataset", "contracts").header("X-User-Id", TENANT_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/exports"))
            .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(get("/exports/download").param("dataset", "nope").header("X-User-Id", OWNER_1))
            .andExpect(redirectedUrl("/exports"))
            .andExpect(flash().attributeExists("errorMessage"));

        String owner = mockMvc.perform(get("/contracts").header("X-User-Id", OWNER_1))
            .andReturn().getResponse().getContentAsString();
        assertTrue(owner.contains("id=\"export-link\""));
        String tenant = mockMvc.perform(get("/contracts").header("X-User-Id", TENANT_1))
            .andReturn().getResponse().getContentAsString();
        assertFalse(tenant.contains("id=\"export-link\""));
    }

    @Test
    @DisplayName("#227: khoảng kỳ không có hóa đơn thì API trả 422, giao diện quay về trang xuất kèm thông báo, không có tệp")
    void emptyResultIsRejected() throws Exception {
        invoices.clear();
        mockMvc.perform(get("/api/exports/invoices").param("from", "2000-01").param("to", "2000-01")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/exports/download").param("dataset", "invoices").param("from", "2000-01")
                .param("to", "2000-01").header("X-User-Id", OWNER_1))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/exports"))
            .andExpect(flash().attribute("errorMessage", "Không có dữ liệu phù hợp để xuất."));
    }
}
