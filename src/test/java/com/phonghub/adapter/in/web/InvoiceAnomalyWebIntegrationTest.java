package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.MissingReading;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.UtilityReading;
import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** #156: trang "Phân tích hóa đơn" chỉ dành cho chủ trọ; hồ sơ in-memory không có chỉ số nên báo cáo rỗng. */
class InvoiceAnomalyWebIntegrationTest extends SeededWebTest {

    private static final String PAGE = "/analysis/invoices";
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InvoiceAnomalyQueryPort anomalyQuery;

    @Test
    @DisplayName("chủ trọ xem được trang, có bộ lọc nhà trọ/tháng và thông báo khi không có bất thường")
    void ownerSeesPage() throws Exception {
        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1).param("month", "2026-10"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("Phân tích hóa đơn"));
        assertTrue(html.contains("id=\"anomalyFilter\""));
        assertTrue(html.contains("value=\"2026-10\""), "giữ tháng đã chọn trong ô lọc");
        assertTrue(html.contains("id=\"anomalyEmpty\""));
        assertTrue(html.contains("0 điểm bất thường trong tháng 2026-10"));
        assertTrue(html.contains("href=\"/analysis/invoices\""), "menu có liên kết cho chủ trọ");
    }

    @Test
    @DisplayName("có bất thường thì hiện bảng với mức độ, số liệu đối chiếu và việc nên làm")
    void ownerSeesAnomalyTable() throws Exception {
        UUID room = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 10);
        when(anomalyQuery.utilityReadings(anyCollection(), any(), any())).thenReturn(List.of(
            new UtilityReading(DataSeeder.PROP_1_ID, room, "101", MeterType.ELECTRIC, period.minusMonths(1),
                BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("100"), false),
            new UtilityReading(DataSeeder.PROP_1_ID, room, "101", MeterType.ELECTRIC, period,
                new BigDecimal("100"), new BigDecimal("300"), new BigDecimal("200"), false)));
        when(anomalyQuery.missingReadings(anyCollection(), any())).thenReturn(List.of(
            new MissingReading(DataSeeder.PROP_1_ID, UUID.randomUUID(), "102", MeterType.WATER)));

        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1).param("month", period.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("id=\"anomalyTable\""));
        assertFalse(html.contains("id=\"anomalyEmpty\""));
        assertTrue(html.contains("2 điểm bất thường trong tháng 2026-10"), "tổng hợp theo mức độ");
        assertTrue(html.contains("Phòng 101: điện tăng 100%"));
        assertTrue(html.contains("200 kWh"), "số liệu nguồn kỳ này");
        assertTrue(html.contains("Phòng 102: chưa có chỉ số nước"));
        assertTrue(html.contains("Đối chiếu chỉ số công tơ"), "việc nên làm");
    }

    @Test
    @DisplayName("chọn đích danh nhà trọ của mình thì xem được")
    void ownerCanFilterOwnProperty() throws Exception {
        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1)
                .param("propertyId", DataSeeder.PROP_1_ID.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("selected"), "nhà trọ được chọn giữ trong ô lọc");
    }

    @Test
    @DisplayName("chủ trọ xem nhà của người khác bị chặn và quay về trang chủ")
    void ownerCannotFilterForeignProperty() throws Exception {
        mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_2)
                .param("propertyId", DataSeeder.PROP_1_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("quản trị viên, nhân viên, kỹ thuật viên và người thuê không xem được")
    void otherRolesAreRejected() throws Exception {
        for (String userId : new String[] {ADMIN, STAFF_1, TECH_1, TENANT_1}) {
            mockMvc.perform(get(PAGE).header("X-User-Id", userId))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        }
    }

    @Test
    @DisplayName("tháng hoặc nhà trọ sai định dạng không gây lỗi, trang báo và dùng giá trị mặc định")
    void invalidParametersFallBack() throws Exception {
        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1)
                .param("month", "abc").param("propertyId", "không-phải-uuid"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("id=\"anomalyEmpty\""));
        assertFalse(html.contains("Exception"));
    }

    @Test
    @DisplayName("menu Phân tích hóa đơn chỉ hiện cho chủ trọ")
    void navLinkOnlyForOwner() throws Exception {
        String tenantPage = mockMvc.perform(get("/dashboard").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(tenantPage.contains("/analysis/invoices"));

        mockMvc.perform(get(PAGE).header("X-User-Id", ADMIN)).andExpect(redirectedUrl("/"));
    }
}
