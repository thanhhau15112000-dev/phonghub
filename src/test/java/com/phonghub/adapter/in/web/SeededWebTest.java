package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomAssetRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomConditionRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.application.port.out.DemoFixturePort;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền cho các test thay đổi dữ liệu mẫu dùng chung (ví dụ check-out kết thúc hợp đồng mẫu):
 * nạp lại dữ liệu mẫu trước mỗi test để các test khác không bị ảnh hưởng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class SeededWebTest {

    @Autowired private InMemoryUserRepository seedUsers;
    @Autowired private InMemoryHandoverRepository seedHandovers;
    @Autowired private MockMvc seedMvc;
    @Autowired private InMemoryPropertyRepository seedProperties;
    @Autowired private InMemoryStaffPropertyAssignmentRepository seedAssignments;
    @Autowired private InMemoryRoomRepository seedRooms;
    @Autowired private InMemoryRoomAssetRepository seedRoomAssets;
    @Autowired private InMemoryRoomConditionRepository seedRoomConditions;
    @Autowired private InMemoryTenantRepository seedTenants;
    @Autowired private InMemoryContractRepository seedContracts;
    @Autowired private InMemoryMaintenanceTicketRepository seedTickets;

    /** Người thuê của nhà trọ 1 đã có tài khoản TENANT đang hoạt động, chưa ở hợp đồng nào (#219). */
    protected Tenant accountTenant(String name, String cccd, String phone) {
        String suffix = cccd.substring(cccd.length() - 6);
        User user = seedUsers.save(new User(UUID.randomUUID(), "tenant" + suffix, "tenant" + suffix + "@test.local",
            name, phone, UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()));
        Tenant tenant = seedTenants.save(Tenant.create(user.id(), name, cccd, phone, user.email(), null));
        seedTenants.linkToProperty(tenant.id(), DataSeeder.PROP_1_ID, LocalDemoAuthenticationAdapter.OWNER_1_ID);
        return tenant;
    }

    /** Hợp đồng chỉ có hiệu lực sau biên bản check-in đã xác nhận và duyệt (#221); test dựng sẵn biên bản đó rồi kích hoạt. */
    protected void activateWithApprovedCheckIn(UUID contractId, String actingUserId) throws Exception {
        UUID actor = DemoFixturePort.ADMIN_ID;
        seedHandovers.save(Handover.draft(contractId, HandoverType.CHECK_IN, BigDecimal.ZERO, BigDecimal.ZERO,
                "Tình trạng đã ghi nhận", null, actor)
            .send("terms", null, null, actor).tenantConfirm(actor).approve(actor));
        seedMvc.perform(post("/api/contracts/" + contractId + "/activate").header("X-User-Id", actingUserId))
            .andExpect(status().isOk());
    }

    @BeforeEach
    @AfterEach
    void reseedSharedData() {
        seedUsers.clear();
        seedProperties.clear();
        seedAssignments.clear();
        seedRooms.clear();
        seedRoomAssets.clear();
        seedRoomConditions.clear();
        seedTenants.clear();
        seedContracts.clear();
        seedTickets.clear();
        DataSeeder.seedAll(seedUsers, seedProperties, seedAssignments, seedRooms, seedTenants, seedContracts,
            seedTickets);
    }
}
