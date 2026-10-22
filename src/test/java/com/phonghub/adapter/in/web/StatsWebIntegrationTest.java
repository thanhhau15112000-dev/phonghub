package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * #93/#75: API thống kê và dashboard vận hành trên dữ liệu mẫu. Ba nhà trọ mẫu đều của OWNER_1; OWNER_2 chưa có nhà
 * nào. Hóa đơn được thêm theo tháng hiện tại để rơi vào khoảng mặc định 12 tháng.
 */
class StatsWebIntegrationTest extends SeededWebTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PROP_1 = DataSeeder.PROP_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryInvoiceRepository invoices;

    @BeforeEach
    void addInvoices() {
        YearMonth current = YearMonth.now(StatisticsQueryPort.ZONE);
        YearMonth previous = current.minusMonths(1);
        invoices.save(invoice(current, "3500000", "3500000", InvoiceStatus.PAID));
        invoices.save(invoice(previous, "3500000", "500000", InvoiceStatus.PARTIALLY_PAID));
        invoices.save(invoice(current.minusMonths(2), "9999999", "0", InvoiceStatus.VOIDED));
    }

    @AfterEach
    void clearInvoices() {
        invoices.clear();
    }

    private static Invoice invoice(YearMonth period, String total, String paid, InvoiceStatus status) {
        return new Invoice(UUID.randomUUID(), DataSeeder.CONTRACT_1_ID, DataSeeder.ROOM_101_ID, DataSeeder.TENANT_RECORD_ID,
            period.getMonthValue(), period.getYear(), new BigDecimal(total), new BigDecimal(total), new BigDecimal(paid),
            period.atDay(5), status, null, InvoiceType.RENT, null, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("owner xem tổng hợp nhà của mình: doanh thu, công nợ, quá hạn, lấp đầy, bảo trì")
    void ownerSummary() throws Exception {
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.propertyCount").value(3))
            .andExpect(jsonPath("$.revenue.invoiceCount").value(2))
            .andExpect(jsonPath("$.revenue.billed").value(7000000))
            .andExpect(jsonPath("$.revenue.collected").value(4000000))
            .andExpect(jsonPath("$.revenue.outstanding").value(3000000))
            .andExpect(jsonPath("$.revenue.overdueInvoices").value(1))
            .andExpect(jsonPath("$.revenue.collectionRate").value(57.1))
            .andExpect(jsonPath("$.estimatedProfit").value(4000000))
            .andExpect(jsonPath("$.occupancy.totalRooms").value(5))
            .andExpect(jsonPath("$.occupancy.occupied").value(1))
            .andExpect(jsonPath("$.occupancy.occupancyRate").value(20.0))
            .andExpect(jsonPath("$.maintenance.openTickets").value(1))
            .andExpect(jsonPath("$.maintenance.roomsUnderMaintenance").value(1));
    }

    @Test
    @DisplayName("owner lọc một nhà, xem chuỗi theo quý và từng nhà trọ")
    void ownerRevenueAndProperties() throws Exception {
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_1).param("propertyId", PROP_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.propertyCount").value(1))
            .andExpect(jsonPath("$.occupancy.totalRooms").value(4));

        YearMonth current = YearMonth.now(StatisticsQueryPort.ZONE);
        mockMvc.perform(get("/api/stats/owner/revenue").header("X-User-Id", OWNER_1)
                .param("from", current.minusMonths(5).toString()).param("to", current.toString())
                .param("groupBy", "MONTH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(6)))
            .andExpect(jsonPath("$[5].period").value(current.toString()))
            .andExpect(jsonPath("$[5].collected").value(3500000))
            .andExpect(jsonPath("$[4].outstanding").value(3000000));

        mockMvc.perform(get("/api/stats/owner/revenue").header("X-User-Id", OWNER_1).param("groupBy", "YEAR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].period", hasItem(Integer.toString(current.getYear()))));

        mockMvc.perform(get("/api/stats/owner/properties").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[?(@.propertyId == '" + PROP_1 + "')].revenue.billed").value(hasItem(7000000)));
    }

    @Test
    @DisplayName("owner chưa có nhà trọ: số liệu rỗng, không lỗi")
    void emptyOwner() throws Exception {
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.propertyCount").value(0))
            .andExpect(jsonPath("$.revenue.billed").value(0))
            .andExpect(jsonPath("$.revenue.collectionRate").doesNotExist())
            .andExpect(jsonPath("$.occupancy.occupancyRate").doesNotExist());
        mockMvc.perform(get("/api/stats/owner/properties").header("X-User-Id", OWNER_2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("owner xem nhà của owner khác bị 403")
    void ownerForbiddenOnOtherOwnersProperty() throws Exception {
        for (String path : new String[] {"summary", "revenue", "properties"}) {
            mockMvc.perform(get("/api/stats/owner/" + path).header("X-User-Id", OWNER_2).param("propertyId", PROP_1))
                .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "TECH", "TENANT"})
    @DisplayName("staff, kỹ thuật viên, người thuê bị 403 ở mọi endpoint thống kê")
    void otherRolesForbidden(String who) throws Exception {
        String user = switch (who) {
            case "STAFF" -> STAFF_1;
            case "TECH" -> TECH_1;
            default -> TENANT_1;
        };
        for (String path : new String[] {"owner/summary", "owner/revenue", "owner/properties", "admin/overview",
            "admin/pending-properties", "admin/accounts"}) {
            mockMvc.perform(get("/api/stats/" + path).header("X-User-Id", user))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("owner không xem được thống kê admin")
    void ownerForbiddenOnAdminStats() throws Exception {
        for (String path : new String[] {"overview", "pending-properties", "accounts"}) {
            mockMvc.perform(get("/api/stats/admin/" + path).header("X-User-Id", OWNER_1))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("admin xem tổng quan, nhà chờ duyệt kèm chủ nhà, tài khoản theo vai trò")
    void adminStats() throws Exception {
        mockMvc.perform(get("/api/stats/admin/overview").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.properties").value(3))
            .andExpect(jsonPath("$.propertiesByApproval.PENDING").value(1))
            .andExpect(jsonPath("$.propertiesByApproval.REJECTED").value(1))
            .andExpect(jsonPath("$.accountsByRole.OWNER").value(2))
            .andExpect(jsonPath("$.accountsByRole.TENANT").value(1))
            .andExpect(jsonPath("$.occupancy.totalRooms").value(5))
            .andExpect(jsonPath("$.revenue").doesNotExist());

        mockMvc.perform(get("/api/stats/admin/pending-properties").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(DataSeeder.PROP_2_ID.toString()))
            .andExpect(jsonPath("$[0].ownerId").value(OWNER_1));

        mockMvc.perform(get("/api/stats/admin/accounts").header("X-User-Id", ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAccounts").value(7))
            .andExpect(jsonPath("$.byRole.STAFF").value(2))
            .andExpect(jsonPath("$.activity", hasSize(12)));
    }

    @Test
    @DisplayName("#163: admin không xem thống kê tài chính, kể cả khi lọc một nhà trọ")
    void adminForbiddenOnFinanceStats() throws Exception {
        for (String path : new String[] {"summary", "revenue", "properties"}) {
            mockMvc.perform(get("/api/stats/owner/" + path).header("X-User-Id", ADMIN))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/stats/owner/" + path).header("X-User-Id", ADMIN).param("propertyId", PROP_1))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("tham số sai trả 400")
    void invalidParameters() throws Exception {
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_1)
                .param("from", "2026-06").param("to", "2026-01"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_1)
                .param("from", "2010-01").param("to", "2026-01"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stats/owner/revenue").header("X-User-Id", OWNER_1).param("groupBy", "WEEK"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_1).param("from", "2026-13"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stats/owner/summary").header("X-User-Id", OWNER_1).param("propertyId", "abc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("dashboard: owner thấy phần vận hành; admin chỉ thấy toàn hệ thống, không có tiền; staff/người thuê không thấy")
    void dashboardSections() throws Exception {
        String owner = mockMvc.perform(get("/").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(owner.contains("id=\"operations\""));
        assertTrue(owner.contains("Doanh thu theo tháng"));
        assertTrue(owner.contains("4.000.000 đ"), "đã thu");
        assertTrue(owner.contains("1 hóa đơn quá hạn"));
        assertFalse(owner.contains("id=\"adminOverview\""));

        String admin = mockMvc.perform(get("/").header("X-User-Id", ADMIN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(admin.contains("id=\"adminOverview\""));
        assertTrue(admin.contains("id=\"pendingPropertyTable\""), "bảng nhà chờ duyệt");
        assertFalse(admin.contains("id=\"operations\""), "#163: admin không có khối vận hành (tiền)");
        assertFalse(admin.contains("Doanh thu"), "#163: admin không thấy doanh thu");
        assertFalse(admin.contains("4.000.000"), "#163: admin không thấy số tiền đã thu");
        assertFalse(admin.contains("Công nợ"), "#163: admin không thấy công nợ");

        for (String user : new String[] {STAFF_1, TENANT_1, TECH_1}) {
            String html = mockMvc.perform(get("/").header("X-User-Id", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(html.contains("id=\"operations\""), user);
            assertFalse(html.contains("id=\"adminOverview\""), user);
        }
    }

    @Test
    @DisplayName("dashboard: bộ lọc sai hoặc nhà trọ ngoài phạm vi báo lỗi nhưng trang vẫn hiển thị số liệu mặc định")
    void dashboardInvalidFilter() throws Exception {
        String badMonth = mockMvc.perform(get("/").header("X-User-Id", OWNER_1).param("statsFrom", "abc"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(badMonth.contains("Tháng thống kê không hợp lệ"));
        assertTrue(badMonth.contains("id=\"operations\""));

        String foreign = mockMvc.perform(get("/").header("X-User-Id", OWNER_2).param("statsPropertyId", PROP_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(foreign.contains("id=\"operations\""));
        assertFalse(foreign.contains("7.000.000"), "không lộ số liệu nhà của owner khác");

        String filtered = mockMvc.perform(get("/").header("X-User-Id", OWNER_1).param("statsPropertyId", PROP_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(filtered.contains("· 1 nhà trọ"));
    }
}
