package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * #79: ma trận 403/409 cho hợp đồng và hóa đơn trên dữ liệu mẫu (OWNER_1 sở hữu cả ba nhà; STAFF_2 chỉ được phân
 * công PROP_2; TECH_1 ở PROP_1 nhưng không quản lý hợp đồng). Mỗi lần bị từ chối phải không đổi dữ liệu.
 */
class ContractInvoiceSecurityMatrixTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String C1 = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String CONTRACT_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final List<String> OUTSIDERS = List.of(OWNER_2, STAFF_2, TECH_1, TENANT_1);

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryRoomRepository rooms;
    @Autowired private InMemoryInvoiceRepository invoices;

    @AfterEach
    void clearInvoices() {
        invoices.clear();
    }

    private void assertSeedUntouched() {
        assertEquals(ContractStatus.ACTIVE, contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow().getStatus());
        assertEquals(RoomStatus.OCCUPIED, rooms.findById(DataSeeder.ROOM_101_ID).orElseThrow().getStatus());
    }

    private Contract draft(UUID roomId) {
        return contracts.save(Contract.create(DataSeeder.PROP_1_ID, roomId, DataSeeder.TENANT_RECORD_ID,
            new BigDecimal("1000000"), new BigDecimal("3500000"), LocalDate.now(), LocalDate.now().plusMonths(6), 5));
    }

    @Test
    @DisplayName("hợp đồng: người ngoài phạm vi bị 403 khi xem, kích hoạt, kết thúc; dữ liệu không đổi")
    void contractOutsidersForbidden() throws Exception {
        for (String user : List.of(OWNER_2, STAFF_2)) {
            mockMvc.perform(get(C1).header("X-User-Id", user)).andExpect(status().isForbidden());
        }
        for (String user : OUTSIDERS) {
            mockMvc.perform(post(C1 + "/activate").header("X-User-Id", user)).andExpect(status().isForbidden());
            mockMvc.perform(post(C1 + "/terminate").header("X-User-Id", user)).andExpect(status().isForbidden());
        }
        assertSeedUntouched();

        mockMvc.perform(get(C1).header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(get(C1).header("X-User-Id", TENANT_1)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("hợp đồng: kiểm tra quyền trước trạng thái — người ngoài không biết được trạng thái qua 409")
    void permissionCheckedBeforeState() throws Exception {
        Contract draft = draft(DataSeeder.ROOM_104_ID);
        // hợp đồng ACTIVE kích hoạt lại và hợp đồng DRAFT bị kết thúc đều là xung đột trạng thái,
        // nhưng với người ngoài phạm vi phải là 403
        mockMvc.perform(post(C1 + "/activate").header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contracts/" + draft.getId() + "/terminate").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("hợp đồng: xung đột trạng thái trả 409 và không đổi phòng/hợp đồng")
    void contractStateConflicts() throws Exception {
        mockMvc.perform(post(C1 + "/activate").header("X-User-Id", OWNER_1)).andExpect(status().isConflict());

        Contract draft = draft(DataSeeder.ROOM_104_ID);
        mockMvc.perform(post("/api/contracts/" + draft.getId() + "/terminate").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        assertEquals(ContractStatus.DRAFT, contracts.findById(draft.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.AVAILABLE, rooms.findById(DataSeeder.ROOM_104_ID).orElseThrow().getStatus());

        // phòng P101 đã có hợp đồng ACTIVE: kích hoạt hợp đồng thứ hai bị 409
        Contract second = draft(DataSeeder.ROOM_101_ID);
        mockMvc.perform(post("/api/contracts/" + second.getId() + "/activate").header("X-User-Id", STAFF_1))
            .andExpect(status().isConflict());
        assertEquals(ContractStatus.DRAFT, contracts.findById(second.getId()).orElseThrow().getStatus());
        assertSeedUntouched();
    }

    @Test
    @DisplayName("hợp đồng: tạo mới bị 403 với người ngoài/không có quyền quản lý hợp đồng, 409 khi phòng đã có hợp đồng")
    void contractCreation() throws Exception {
        String body = """
            {"propertyId": "%s", "roomId": "%s", "primaryTenantId": "%s", "depositAmount": 1000000, "rentAmount": 3000000,
             "startDate": "%s", "endDate": "%s", "paymentDay": 5}
            """;
        String room104 = body.formatted(DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, DataSeeder.TENANT_RECORD_ID, LocalDate.now(),
            LocalDate.now().plusMonths(6));
        for (String user : OUTSIDERS) {
            mockMvc.perform(post("/api/contracts").header("X-User-Id", user)
                    .contentType(MediaType.APPLICATION_JSON).content(room104))
                .andExpect(status().isForbidden());
        }
        assertEquals(1, contracts.findByRoomId(DataSeeder.ROOM_101_ID).size());
        assertTrue(contracts.findByRoomId(DataSeeder.ROOM_104_ID).isEmpty(), "bị từ chối thì không tạo hợp đồng");

        String room101 = body.formatted(DataSeeder.PROP_1_ID, DataSeeder.ROOM_101_ID, DataSeeder.TENANT_RECORD_ID, LocalDate.now(),
            LocalDate.now().plusMonths(6));
        mockMvc.perform(post("/api/contracts").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(room101))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("hóa đơn: người không quản lý hợp đồng không tạo được kỳ thanh toán; trùng kỳ bị từ chối")
    void invoiceIssuing() throws Exception {
        String period = YearMonth.now().toString();
        for (String user : List.of(OWNER_2, STAFF_2, TECH_1, TENANT_1)) {
            mockMvc.perform(post(CONTRACT_PAGE + "/invoices").header("X-User-Id", user).param("period", period))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(CONTRACT_PAGE))
                .andExpect(flash().attributeExists("errorMessage"));
        }
        assertTrue(invoices.findByContractId(DataSeeder.CONTRACT_1_ID).isEmpty());

        mockMvc.perform(post(CONTRACT_PAGE + "/invoices").header("X-User-Id", STAFF_1).param("period", period))
            .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(post(CONTRACT_PAGE + "/invoices").header("X-User-Id", OWNER_1).param("period", period))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(1, invoices.findByContractId(DataSeeder.CONTRACT_1_ID).size());
    }

    @Test
    @DisplayName("hóa đơn: người ngoài không xem được trang thanh toán và trạng thái hóa đơn (403)")
    void invoiceViewing() throws Exception {
        mockMvc.perform(post(CONTRACT_PAGE + "/invoices").header("X-User-Id", OWNER_1)
            .param("period", YearMonth.now().toString()));
        Invoice invoice = invoices.findByContractId(DataSeeder.CONTRACT_1_ID).get(0);
        String pay = "/invoices/" + invoice.getId();

        for (String user : List.of(OWNER_2, STAFF_2)) {
            mockMvc.perform(get(pay + "/status").header("X-User-Id", user)).andExpect(status().isForbidden());
            mockMvc.perform(get(pay + "/pay").header("X-User-Id", user))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contracts"))
                .andExpect(flash().attributeExists("errorMessage"));
        }
        mockMvc.perform(get(pay + "/status").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(get(pay + "/pay").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
    }
}
