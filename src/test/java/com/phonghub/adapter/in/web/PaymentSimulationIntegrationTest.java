package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentSimulationIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String TENANT = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String STAFF = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String CONTRACT_URL = "/contracts/" + DataSeeder.CONTRACT_1_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private SepayProperties sepayProperties;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;
    @Autowired private InMemoryInvoiceRepository invoiceRepository;
    @Autowired private InMemoryPaymentRepository paymentRepository;

    @BeforeEach
    void resetData() {
        sepayProperties.setSimulationEnabled(true);
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        invoiceRepository.clear();
        paymentRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
    }

    @AfterEach
    void restore() {
        sepayProperties.setSimulationEnabled(true);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String userId) {
        return builder.header("X-User-Id", userId);
    }

    private Invoice issueRent() throws Exception {
        mockMvc.perform(as(post(CONTRACT_URL + "/invoices"), ADMIN).param("period", YearMonth.now().toString()));
        return invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).get(0);
    }

    @Test
    @DisplayName("Pay page auto-refreshes, shows the test-mode button, and the status endpoint reports payment")
    void payPageAndStatus() throws Exception {
        Invoice invoice = issueRent();
        String payUrl = "/invoices/" + invoice.getId() + "/pay";

        mockMvc.perform(as(get(payUrl), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Chế độ thử nghiệm")))
            .andExpect(content().string(containsString("/invoices/" + invoice.getId() + "/simulate-payment")))
            // URL nằm trong script nên Thymeleaf escape dấu "/" thành "\\/"
            .andExpect(content().string(containsString(invoice.getId() + "\\/status")))
            .andExpect(content().string(containsString("fetch(statusUrl")));

        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"), TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(false))
            .andExpect(jsonPath("$.status").value("ISSUED"));

        mockMvc.perform(as(post("/invoices/" + invoice.getId() + "/simulate-payment"), TENANT))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"), TENANT))
            .andExpect(jsonPath("$.paid").value(true))
            .andExpect(jsonPath("$.remaining").value(0));
        assertEquals(InvoiceStatus.PAID, invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus());
        assertTrue(paymentRepository.findAll().stream().anyMatch(t -> "SIMULATION".equals(t.gateway()) && t.sepayId() < 0),
            "simulated transaction is recorded with a negative id that cannot clash with SePay");

        mockMvc.perform(as(get(payUrl), TENANT))
            .andExpect(content().string(containsString("đã được thanh toán đủ")))
            .andExpect(content().string(not(containsString("simulate-payment"))));
    }

    @Test
    void unrelatedOwnerReceivesForbiddenJsonFromInvoiceStatus() throws Exception {
        Invoice invoice = issueRent();
        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"),
                LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.title").value("Unauthorized Property Access"))
            .andExpect(jsonPath("$.paid").doesNotExist());
    }

    @Test
    @DisplayName("Simulated payment of a repair fee completes the ticket exactly like a real webhook")
    void simulatedFeeCompletesTicket() throws Exception {
        mockMvc.perform(as(post("/maintenance"), TENANT)
            .param("roomId", DataSeeder.ROOM_101_ID.toString())
            .param("title", "Vỡ kính mô phỏng").param("description", "Mô tả")
            .param("setRoomMaintenance", "false").param("cause", "TENANT_USAGE"));
        MaintenanceTicket ticket = ticketRepository.findAll().stream()
            .filter(t -> t.getTitle().equals("Vỡ kính mô phỏng")).findFirst().orElseThrow();
        mockMvc.perform(as(post("/maintenance/" + ticket.getId() + "/accept"), ADMIN));
        mockMvc.perform(as(post("/maintenance/" + ticket.getId() + "/resolve"), ADMIN)
            .param("resolutionNotes", "Đã sửa").param("repairCost", "250000").param("releaseRoomToAvailable", "false"));
        Invoice fee = invoiceRepository.findActiveByTicketId(ticket.getId()).orElseThrow();

        mockMvc.perform(as(post("/invoices/" + fee.getId() + "/simulate-payment"), TENANT))
            .andExpect(flash().attributeExists("successMessage"));

        assertEquals(MaintenanceStatus.RESOLVED, ticketRepository.findById(ticket.getId()).orElseThrow().getStatus());
        assertEquals(InvoiceStatus.PAID, invoiceRepository.findById(fee.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Simulation is refused when disabled, for non tenant/admin roles, and for already paid invoices")
    void simulationGuards() throws Exception {
        Invoice invoice = issueRent();

        // Tắt cấu hình: nút biến mất và endpoint từ chối
        sepayProperties.setSimulationEnabled(false);
        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/pay"), TENANT))
            .andExpect(content().string(not(containsString("simulate-payment"))));
        mockMvc.perform(as(post("/invoices/" + invoice.getId() + "/simulate-payment"), TENANT))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(InvoiceStatus.ISSUED, invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus());

        // Bật lại: nhân viên không được mô phỏng
        sepayProperties.setSimulationEnabled(true);
        mockMvc.perform(as(post("/invoices/" + invoice.getId() + "/simulate-payment"), STAFF))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(InvoiceStatus.ISSUED, invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus());

        // Hóa đơn đã trả đủ thì không mô phỏng thêm
        mockMvc.perform(as(post("/invoices/" + invoice.getId() + "/simulate-payment"), TENANT));
        long before = paymentRepository.findAll().size();
        mockMvc.perform(as(post("/invoices/" + invoice.getId() + "/simulate-payment"), TENANT))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(before, paymentRepository.findAll().size());
    }
}
