package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Nền cho các test thay đổi dữ liệu mẫu dùng chung (ví dụ check-out kết thúc hợp đồng mẫu):
 * nạp lại dữ liệu mẫu trước mỗi test để các test khác không bị ảnh hưởng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class SeededWebTest {

    @Autowired private InMemoryUserRepository seedUsers;
    @Autowired private InMemoryPropertyRepository seedProperties;
    @Autowired private InMemoryStaffPropertyAssignmentRepository seedAssignments;
    @Autowired private InMemoryRoomRepository seedRooms;
    @Autowired private InMemoryTenantRepository seedTenants;
    @Autowired private InMemoryContractRepository seedContracts;
    @Autowired private InMemoryMaintenanceTicketRepository seedTickets;

    @BeforeEach
    @AfterEach
    void reseedSharedData() {
        seedUsers.clear();
        seedProperties.clear();
        seedAssignments.clear();
        seedRooms.clear();
        seedTenants.clear();
        seedContracts.clear();
        seedTickets.clear();
        DataSeeder.seedAll(seedUsers, seedProperties, seedAssignments, seedRooms, seedTenants, seedContracts,
            seedTickets);
    }
}
