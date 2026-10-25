package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Chấm dứt ngay (chủ trọ hoặc nhân viên có quyền quản lý, bắt buộc lý do), gia hạn hợp đồng, tạo tài khoản cho người ở cùng và hợp đồng đã hết
 * hạn nhưng chưa trả phòng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractLifecycleActionsIntegrationTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String C1 = "/contracts/" + DataSeeder.CONTRACT_1_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private ContractUseCase contractUseCase;
    @Autowired private DemoActorPort demoActorPort;
    @Autowired private InMemoryAuditAdapter audit;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        audit.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
    }

    private Contract contract() {
        return contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow();
    }

    private void expire() {
        Contract contract = contract();
        contract.expire();
        contractRepository.save(contract);
    }

    @Test
    @DisplayName("Chấm dứt ngay: thiếu lý do bị từ chối; chủ trọ có lý do thì kết thúc và ghi nhật ký")
    void earlyTerminationRequiresOwnerAndReason() throws Exception {
        mockMvc.perform(post(C1 + "/terminate").header("X-User-Id", OWNER_1).param("reason", "  "))
            .andExpect(flash().attribute("errorMessage", containsString("lý do")));
        assertEquals(ContractStatus.ACTIVE, contract().getStatus());

        mockMvc.perform(post(C1 + "/terminate").header("X-User-Id", OWNER_1).param("reason", "Người thuê bỏ đi"))
            .andExpect(flash().attributeExists("successMessage"));
        assertEquals(ContractStatus.TERMINATED, contract().getStatus());
        assertEquals(RoomStatus.AVAILABLE, roomRepository.findById(DataSeeder.ROOM_101_ID).orElseThrow().getStatus());
        AuditPort.AuditEvent event = audit.getRecordedEvents().stream()
            .filter(e -> e.action().equals("CONTRACT_TERMINATED")).findFirst().orElseThrow();
        assertEquals("Người thuê bỏ đi", event.details().get("reason"));
        assertEquals("ACTIVE", event.details().get("oldStatus"));
    }

    @Test
    @DisplayName("API chấm dứt: nhân viên được cấp quyền thành công; nhân viên ngoài phạm vi 403; thiếu lý do 400")
    void apiEarlyTermination() throws Exception {
        mockMvc.perform(post("/api" + C1 + "/terminate").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api" + C1 + "/terminate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.STAFF_2_ID).param("reason", "Thử"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api" + C1 + "/terminate").header("X-User-Id", STAFF_1).param("reason", "Thử"))
            .andExpect(status().isOk());
        assertEquals(ContractStatus.TERMINATED, contract().getStatus());
    }

    @Test
    @DisplayName("Gia hạn: dời ngày kết thúc và đổi tiền thuê; ngày không sau ngày kết thúc cũ bị từ chối")
    void renewActiveContract() throws Exception {
        LocalDate oldEnd = contract().getEndDate();
        mockMvc.perform(post(C1 + "/renew").header("X-User-Id", OWNER_1).param("endDate", oldEnd.toString()))
            .andExpect(flash().attribute("errorMessage", containsString("phải sau ngày kết thúc hiện tại")));

        LocalDate newEnd = oldEnd.plusYears(1);
        mockMvc.perform(post(C1 + "/renew").header("X-User-Id", OWNER_1)
                .param("endDate", newEnd.toString()).param("rentAmount", "3800000"))
            .andExpect(flash().attributeExists("successMessage"));
        assertEquals(newEnd, contract().getEndDate());
        assertEquals(0, new BigDecimal("3800000").compareTo(contract().getRentAmount()));
        assertTrue(audit.getRecordedEvents().stream().anyMatch(e -> e.action().equals("CONTRACT_RENEWED")));
    }

    @Test
    @DisplayName("Hợp đồng hết hạn chưa trả phòng: gia hạn thì trở lại hiệu lực; người thuê vẫn xem được phòng")
    void renewExpiredContractAndTenantStillSeesRoom() throws Exception {
        expire();
        mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());

        LocalDate newEnd = contract().getEndDate().plusMonths(6);
        mockMvc.perform(post(C1 + "/renew").header("X-User-Id", OWNER_1).param("endDate", newEnd.toString()));
        assertEquals(ContractStatus.ACTIVE, contract().getStatus());
        assertEquals(newEnd, contract().getEndDate());
    }

    @Test
    @DisplayName("Thêm người ở + tạo tài khoản: trả tên đăng nhập và mật khẩu tạm, tài khoản phải đổi mật khẩu")
    void occupantAccountGetsTemporaryPassword() {
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        ContractUseCase.AddOccupantResult result = contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
            DataSeeder.CONTRACT_1_ID, "Trần Thị Mới", "079300009999", "0909000111", "nguoimoi@phonghub.local",
            null, LocalDate.now(), true));

        assertNotNull(result.temporaryPassword());
        assertTrue(result.temporaryPassword().length() >= 12);
        User account = userRepository.findByUsername(result.accountUsername()).orElseThrow();
        assertEquals(UserRole.TENANT, account.role());
        assertTrue(account.mustChangePassword());
        assertEquals(account.id(), tenantRepository.findById(result.occupant().tenantId()).orElseThrow().userId());
    }

    @Test
    @DisplayName("Email của tài khoản không phải người thuê: từ chối, không thêm người ở")
    void occupantAccountRejectsStaffEmail() {
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        int before = contract().getOccupants().size();
        assertThrows(DomainException.class, () -> contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
            DataSeeder.CONTRACT_1_ID, "Trần Thị Mới", "079300009999", "0909000111", "staff1@phonghub.local",
            null, LocalDate.now(), true)));
        assertEquals(before, contract().getOccupants().size());
        assertFalse(tenantRepository.findByUserId(LocalDemoAuthenticationAdapter.STAFF_1_ID).isPresent());
    }

    @Test
    @DisplayName("Không chọn tạo tài khoản: không có tên đăng nhập hay mật khẩu tạm")
    void occupantWithoutAccount() {
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        ContractUseCase.AddOccupantResult result = contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
            DataSeeder.CONTRACT_1_ID, "Lê Văn Khách", "079300007777", "0909000222", null, null, LocalDate.now(),
            false));
        assertNull(result.accountUsername());
        assertNull(result.temporaryPassword());
    }

    @Test
    @DisplayName("Hợp đồng hết hạn chưa trả phòng: vẫn ghi nhận người ở cùng rời đi được")
    void checkOutOccupantOnExpiredContract() {
        demoActorPort.setCurrentUser(LocalDemoAuthenticationAdapter.DEMO_OWNER_1);
        ContractUseCase.AddOccupantResult added = contractUseCase.addOccupant(new ContractUseCase.AddOccupantCommand(
            DataSeeder.CONTRACT_1_ID, "Lê Văn Khách", "079300007777", "0909000222", null, null, LocalDate.now(),
            false));
        expire();

        contractUseCase.checkOutOccupant(new ContractUseCase.CheckOutOccupantCommand(
            DataSeeder.CONTRACT_1_ID, added.occupant().tenantId(), LocalDate.now()));

        assertTrue(contract().getOccupants().stream()
            .anyMatch(o -> o.tenantId().equals(added.occupant().tenantId()) && o.checkOutDate() != null));
    }
}
