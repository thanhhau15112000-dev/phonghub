package com.phonghub.adapter.in.web;

import com.phonghub.adapter.in.security.DomainAuthenticationToken;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.DemoFixturePort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử phân quyền vai trò TENANT:
 * 1. Thuê chung cùng một phòng: Người thuê chính và người ở cùng có quyền ngang nhau trên hợp đồng và hóa đơn phòng mình.
 * 2. Cách ly giữa các phòng: Người thuê phòng khác tuyệt đối bị chặn 403 khi cố xem hợp đồng, hóa đơn, thông tin phòng khác.
 */
class TenantCoLivingAndCrossIsolationIntegrationTest extends SeededWebTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryTenantRepository tenants;
    @Autowired private InMemoryUserRepository users;

    private static DomainAuthenticationToken asTenantAuth(Tenant tenant) {
        CurrentUser user = new CurrentUser(
            tenant.userId(),
            tenant.email(),
            tenant.fullName(),
            UserRole.TENANT,
            false
        );
        return new DomainAuthenticationToken(
            user,
            "test-token",
            List.of(new SimpleGrantedAuthority("ROLE_TENANT")),
            false
        );
    }

    @Test
    @DisplayName("Cùng một phòng: Người thuê chính và người ở cùng ngang quyền xem hợp đồng và hóa đơn")
    void testCoLivingTenantsHaveEqualAccessToTheirContractAndInvoices() throws Exception {
        // 1. Tạo thêm tài khoản người ở cùng phòng 101
        Tenant coTenant = accountTenant("Trần Thị B (Ở Cùng)", "079201009999", "0909999999");

        // 2. Gắn người ở cùng vào hợp đồng phòng 101 (DataSeeder.CONTRACT_1_ID)
        Contract contract1 = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
        ContractOccupant coTenantOccupant = new ContractOccupant(
            UUID.randomUUID(),
            contract1.getId(),
            coTenant.id(),
            false,
            LocalDate.now().minusDays(10),
            null
        );
        List<ContractOccupant> occupants = new ArrayList<>(contract1.getOccupants());
        occupants.add(coTenantOccupant);
        ReflectionTestUtils.setField(contract1, "occupants", occupants);
        contracts.save(contract1);

        // 3. Tạo một hóa đơn tiền phòng cho phòng 101
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(
            invoiceId,
            DataSeeder.CONTRACT_1_ID,
            DataSeeder.ROOM_101_ID,
            DataSeeder.TENANT_RECORD_ID,
            10, 2026,
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            BigDecimal.ZERO,
            LocalDate.now().plusDays(5),
            InvoiceStatus.ISSUED,
            "INV-101-1026",
            InvoiceType.RENT,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        invoices.save(invoice);

        // 4. Người thuê chính (Tenant 1) xem hợp đồng và hóa đơn -> Đều 200 OK
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID)
                .header("X-User-Id", DemoFixturePort.TENANT_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DataSeeder.CONTRACT_1_ID.toString()));

        mockMvc.perform(get("/invoices/" + invoiceId + "/status")
                .header("X-User-Id", DemoFixturePort.TENANT_1_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ISSUED"));

        // 5. Người ở cùng (coTenant) xem hợp đồng và hóa đơn -> Cũng đều 200 OK (ngang quyền)
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID)
                .with(authentication(asTenantAuth(coTenant))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DataSeeder.CONTRACT_1_ID.toString()));

        mockMvc.perform(get("/invoices/" + invoiceId + "/status")
                .with(authentication(asTenantAuth(coTenant))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    @DisplayName("Khác phòng: Người thuê phòng khác bị chặn 403 khi truy cập hợp đồng, hóa đơn, hồ sơ phòng 101")
    void testCrossTenantIsolationBlockedWithForbidden() throws Exception {
        // 1. Tạo tài khoản người thuê phòng khác (hoặc nhà khác)
        Tenant crossTenant = accountTenant("Lê Văn C (Phòng Khác)", "079201008888", "0908888888");

        // 2. Tạo hóa đơn cho phòng 101
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(
            invoiceId,
            DataSeeder.CONTRACT_1_ID,
            DataSeeder.ROOM_101_ID,
            DataSeeder.TENANT_RECORD_ID,
            10, 2026,
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            BigDecimal.ZERO,
            LocalDate.now().plusDays(5),
            InvoiceStatus.ISSUED,
            "INV-101-1026",
            InvoiceType.RENT,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        invoices.save(invoice);

        // 3. Người thuê phòng khác cố tình xem hợp đồng phòng 101 -> 403 Forbidden
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID)
                .with(authentication(asTenantAuth(crossTenant))))
            .andExpect(status().isForbidden());

        // 4. Người thuê phòng khác cố tình xem hóa đơn phòng 101 -> 403 Forbidden
        mockMvc.perform(get("/invoices/" + invoiceId + "/status")
                .with(authentication(asTenantAuth(crossTenant))))
            .andExpect(status().isForbidden());

        // 5. Người thuê phòng khác cố tình xem hồ sơ tenant của người thuê phòng 101 -> 403 Forbidden
        mockMvc.perform(get("/api/tenants/" + DataSeeder.TENANT_RECORD_ID)
                .with(authentication(asTenantAuth(crossTenant))))
            .andExpect(status().isForbidden());
    }
}
