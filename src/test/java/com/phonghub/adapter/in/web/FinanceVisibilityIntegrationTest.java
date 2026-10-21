package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Số liệu tiền (hóa đơn, mã chuyển khoản) chỉ dành cho chủ trọ, nhân viên có quyền thu tiền/quản lý hợp đồng và người
 * thuê của hợp đồng; kỹ thuật viên và nhân viên không có quyền vẫn xem được hợp đồng nhưng không thấy hóa đơn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceVisibilityIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT_PAGE = "/contracts/" + DataSeeder.CONTRACT_1_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private InvoiceUseCase invoiceUseCase;
    @Autowired private DemoActorPort demoActorPort;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;
    @Autowired private InMemoryInvoiceRepository invoiceRepository;

    private Invoice invoice;

    @BeforeEach
    void resetData() throws Exception {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        invoiceRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
        // Staff 2 được giao thêm nhà 1 nhưng không có quyền thu tiền hay quản lý hợp đồng
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), LocalDemoAuthenticationAdapter.STAFF_2_ID,
            DataSeeder.PROP_1_ID, false, false, Instant.now()));
        mockMvc.perform(post(CONTRACT_PAGE + "/invoices").header("X-User-Id", OWNER_1)
            .param("period", YearMonth.now().toString()));
        invoice = invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).get(0);
    }

    @Test
    @DisplayName("Chủ trọ, nhân viên có quyền và người thuê thấy hóa đơn trên trang hợp đồng")
    void allowedRolesSeeInvoices() throws Exception {
        for (String user : new String[] {OWNER_1, STAFF_1, TENANT_1}) {
            mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(invoice.getPaymentCode())));
        }
    }

    @Test
    @DisplayName("Kỹ thuật viên và nhân viên không có quyền: trang hợp đồng không có hóa đơn, không mở được trang thanh toán")
    void technicianAndUnprivilegedStaffSeeNoInvoices() throws Exception {
        for (String user : new String[] {TECH_1, STAFF_2}) {
            mockMvc.perform(get(CONTRACT_PAGE).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(invoice.getPaymentCode()))));
            mockMvc.perform(get("/invoices/" + invoice.getId() + "/status").header("X-User-Id", user))
                .andExpect(status().isForbidden());
        }

        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_TECH_1);
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> invoiceUseCase.listInvoicesForContract(DataSeeder.CONTRACT_1_ID));
    }

    @Test
    @DisplayName("Kỹ thuật viên không thấy nút tạo hợp đồng ở danh sách phòng")
    void technicianSeesNoCreateContractButton() throws Exception {
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", TECH_1))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/contracts/new?propertyId="))));
        mockMvc.perform(get("/properties/" + DataSeeder.PROP_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(content().string(containsString("/contracts/new?propertyId=")));
    }
}
