package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnerBypassAndAdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private InMemoryPropertyRepository propertyRepository;

    @Autowired
    private InMemoryStaffPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private InMemoryRoomRepository roomRepository;

    @Autowired
    private InMemoryTenantRepository tenantRepository;

    @Autowired
    private InMemoryContractRepository contractRepository;

    @Autowired
    private InMemoryMaintenanceTicketRepository ticketRepository;

    private Property owner2Property;
    private Room owner2Room;
    private Contract owner1DraftContract;
    private UUID secondAdminId;

    @BeforeEach
    void setUp() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();

        DataSeeder.seedAll(
            userRepository,
            propertyRepository,
            assignmentRepository,
            roomRepository,
            tenantRepository,
            contractRepository,
            ticketRepository
        );

        Instant now = Instant.now();

        // Tạo nhà trọ và phòng thuộc Owner 2
        owner2Property = propertyRepository.save(new Property(
            UUID.randomUUID(),
            "Nhà trọ Owner 2",
            "456 Lê Văn Lương, Quận 7",
            "Nhà trọ của chủ trọ 2",
            5,
            LocalDemoAuthenticationAdapter.OWNER_2_ID,
            PropertyApprovalStatus.VERIFIED,
            null,
            now
        ));

        owner2Room = roomRepository.save(Room.create(
            owner2Property.id(),
            "O2-101",
            1,
            new BigDecimal("22.0"),
            new BigDecimal("3200000"),
            2
        ));

        // Tạo hợp đồng DRAFT của Owner 1 trên Room 104 (AVAILABLE)
        owner1DraftContract = contractRepository.save(Contract.create(
            DataSeeder.PROP_1_ID,
            DataSeeder.ROOM_104_ID,
            DataSeeder.TENANT_RECORD_ID,
            new BigDecimal("4000000"),
            new BigDecimal("4000000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        ));

        // Tạo tài khoản Admin thứ 2 để test bảo vệ Admin
        secondAdminId = UUID.randomUUID();
        userRepository.save(new User(
            secondAdminId,
            "admin2",
            "admin2@phonghub.local",
            "Quản trị viên số 2",
            "0900000099",
            UserRole.ADMIN,
            User.UserStatus.ACTIVE,
            false,
            now
        ));
    }

    @Test
    @DisplayName("Bảo vệ BOLA: Owner 2 không thể tạo hợp đồng cho phòng của Owner 1")
    void testOwnerBypassCreateContract() throws Exception {
        // Trường hợp 1: Owner 2 chỉ định property của mình nhưng roomId của Owner 1 -> 403 Forbidden
        String attackPayloadCrossRoom = String.format("""
            {
                "propertyId": "%s",
                "roomId": "%s",
                "primaryTenantFullName": "Kẻ Đột Nhập",
                "primaryTenantIdCard": "079201999999",
                "primaryTenantPhone": "0988776655",
                "primaryTenantEmail": "attacker@phonghub.local",
                "depositAmount": 4000000,
                "rentAmount": 4000000,
                "startDate": "%s",
                "endDate": "%s",
                "paymentDay": 5
            }
            """, owner2Property.id(), DataSeeder.ROOM_104_ID, LocalDate.now(), LocalDate.now().plusMonths(6));

        mockMvc.perform(post("/api/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(attackPayloadCrossRoom))
            .andExpect(status().isForbidden());

        // Trường hợp 2: Owner 2 cố gắng tạo hợp đồng trực tiếp trên property của Owner 1 -> 403 Forbidden
        String attackPayloadCrossProperty = String.format("""
            {
                "propertyId": "%s",
                "roomId": "%s",
                "primaryTenantFullName": "Kẻ Đột Nhập 2",
                "primaryTenantIdCard": "079201999998",
                "primaryTenantPhone": "0988776654",
                "primaryTenantEmail": "attacker2@phonghub.local",
                "depositAmount": 4000000,
                "rentAmount": 4000000,
                "startDate": "%s",
                "endDate": "%s",
                "paymentDay": 5
            }
            """, DataSeeder.PROP_1_ID, DataSeeder.ROOM_104_ID, LocalDate.now(), LocalDate.now().plusMonths(6));

        mockMvc.perform(post("/api/contracts")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(attackPayloadCrossProperty))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bảo vệ BOLA: Owner 2 không thể kích hoạt hoặc thanh lý hợp đồng của Owner 1")
    void testOwnerBypassActivateAndTerminateContract() throws Exception {
        // Owner 2 cố gắng kích hoạt hợp đồng DRAFT của Owner 1 -> 403 Forbidden
        mockMvc.perform(post("/api/contracts/" + owner1DraftContract.getId() + "/activate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isForbidden());

        // Owner 2 cố gắng thanh lý hợp đồng ACTIVE của Owner 1 -> 403 Forbidden
        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/terminate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bảo vệ BOLA: Owner 2 không thể thay đổi trạng thái phòng của Owner 1")
    void testOwnerBypassChangeRoomStatus() throws Exception {
        String requestBody = """
            {
                "status": "MAINTENANCE"
            }
            """;

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_104_ID + "/status")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_2_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("State machine invariant: Không thể chuyển phòng OCCUPIED sang AVAILABLE trực tiếp")
    void testOccupiedRoomCannotTransitionToAvailableDirectly() throws Exception {
        // Phòng 101 đang có hợp đồng ACTIVE (OCCUPIED). Owner 1 cố gắng chuyển sang AVAILABLE thủ công -> 409 Conflict
        String requestBody = """
            {
                "status": "AVAILABLE"
            }
            """;

        mockMvc.perform(put("/api/rooms/" + DataSeeder.ROOM_101_ID + "/status")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Bảo vệ tài khoản quản trị: Admin không thể tự reset mật khẩu của Admin khác")
    void testAdminCannotResetAnotherAdminPassword() throws Exception {
        mockMvc.perform(post("/admin/users/" + secondAdminId + "/reset-password")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.ADMIN_ID.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("tableErrorMessage", "Không thể đặt lại mật khẩu cho tài khoản Quản trị viên."));
    }

    @Test
    @DisplayName("Trạng thái hợp đồng: Kích hoạt hợp đồng không ở trạng thái DRAFT sẽ bị từ chối")
    void testContractStatusValidation() throws Exception {
        // Hợp đồng CONTRACT_1_ID đang ACTIVE, thử gọi activate lần nữa -> 400 Bad Request
        mockMvc.perform(post("/api/contracts/" + DataSeeder.CONTRACT_1_ID + "/activate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isBadRequest());

        // Hợp đồng owner1DraftContract đang DRAFT, thử gọi terminate -> 400 Bad Request
        mockMvc.perform(post("/api/contracts/" + owner1DraftContract.getId() + "/terminate")
                .header("X-User-Id", LocalDemoAuthenticationAdapter.OWNER_1_ID.toString()))
            .andExpect(status().isBadRequest());
    }
}
