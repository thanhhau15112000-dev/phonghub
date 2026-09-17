package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DataSeeder {

    public static final UUID PROP_1_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID PROP_2_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    public static final UUID ROOM_101_ID = UUID.fromString("10101010-1010-1010-1010-101010101010");
    public static final UUID ROOM_102_ID = UUID.fromString("10201020-1020-1020-1020-102010201020");
    public static final UUID ROOM_103_ID = UUID.fromString("10301030-1030-1030-1030-103010301030");
    public static final UUID ROOM_104_ID = UUID.fromString("10401040-1040-1040-1040-104010401040");
    public static final UUID ROOM_201_ID = UUID.fromString("20102010-2010-2010-2010-201020102010");

    public static final UUID TENANT_RECORD_ID = UUID.fromString("44444444-0000-0000-0000-444444444441");
    public static final UUID CONTRACT_1_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final UUID TICKET_1_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    public static void seedAll(
        InMemoryUserRepository userRepository,
        InMemoryPropertyRepository propertyRepository,
        InMemoryStaffPropertyAssignmentRepository assignmentRepository,
        InMemoryRoomRepository roomRepository,
        InMemoryTenantRepository tenantRepository,
        InMemoryContractRepository contractRepository,
        InMemoryMaintenanceTicketRepository ticketRepository
    ) {
        Instant now = Instant.now();

        // 1. Users
        User admin = new User(LocalDemoAuthenticationAdapter.ADMIN_ID, "admin@phonghub.local", "Quan Tri Vien", "0900000001", UserRole.ADMIN, User.UserStatus.ACTIVE, now);
        User staff1 = new User(LocalDemoAuthenticationAdapter.STAFF_1_ID, "staff1@phonghub.local", "Nhan Vien Q7", "0900000002", UserRole.STAFF, User.UserStatus.ACTIVE, now);
        User staff2 = new User(LocalDemoAuthenticationAdapter.STAFF_2_ID, "staff2@phonghub.local", "Nhan Vien Tan Binh", "0900000003", UserRole.STAFF, User.UserStatus.ACTIVE, now);
        User tech1 = new User(LocalDemoAuthenticationAdapter.TECH_1_ID, "tech1@phonghub.local", "Ky Thuat Vien", "0900000004", UserRole.TECHNICIAN, User.UserStatus.ACTIVE, now);
        User tenantUser1 = new User(LocalDemoAuthenticationAdapter.TENANT_1_ID, "tenant1@phonghub.local", "Nguyen Van A", "0901234567", UserRole.TENANT, User.UserStatus.ACTIVE, now);

        userRepository.save(admin);
        userRepository.save(staff1);
        userRepository.save(staff2);
        userRepository.save(tech1);
        userRepository.save(tenantUser1);

        // 2. Properties
        Property prop1 = new Property(PROP_1_ID, "Nha Tro Xanh - Quan 7", "123 Nguyen Thi Thap, Phuong Tan Quy, Quan 7, TP.HCM", "Khu tro sinh vien va nguoi di lam", 10, now);
        Property prop2 = new Property(PROP_2_ID, "Khu Tro Tan Binh", "45 Cong Hoa, Phuong 13, Quan Tan Binh, TP.HCM", "Nha tro gan san bay, yen tinh", 5, now);

        propertyRepository.save(prop1);
        propertyRepository.save(prop2);

        // 3. Staff & Tech Assignments
        // Staff 1 assigned to Property 1
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), LocalDemoAuthenticationAdapter.STAFF_1_ID, PROP_1_ID, true, true, now));
        // Staff 2 assigned to Property 2
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), LocalDemoAuthenticationAdapter.STAFF_2_ID, PROP_2_ID, true, true, now));
        // Tech 1 assigned to Property 1
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), LocalDemoAuthenticationAdapter.TECH_1_ID, PROP_1_ID, false, false, now));

        // 4. Rooms in Property 1
        Room room101 = new Room(ROOM_101_ID, PROP_1_ID, "P101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2, RoomStatus.OCCUPIED, now, now);
        Room room102 = new Room(ROOM_102_ID, PROP_1_ID, "P102", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2, RoomStatus.MAINTENANCE, now, now);
        Room room103 = new Room(ROOM_103_ID, PROP_1_ID, "P103", 1, new BigDecimal("28.0"), new BigDecimal("3800000"), 2, RoomStatus.RESERVED, now, now);
        Room room104 = new Room(ROOM_104_ID, PROP_1_ID, "P104", 1, new BigDecimal("30.0"), new BigDecimal("4000000"), 3, RoomStatus.AVAILABLE, now, now);

        // Rooms in Property 2
        Room room201 = new Room(ROOM_201_ID, PROP_2_ID, "P201", 2, new BigDecimal("20.0"), new BigDecimal("3000000"), 2, RoomStatus.AVAILABLE, now, now);

        roomRepository.save(room101);
        roomRepository.save(room102);
        roomRepository.save(room103);
        roomRepository.save(room104);
        roomRepository.save(room201);

        // 5. Tenant profile
        Tenant tenant1 = new Tenant(
            TENANT_RECORD_ID,
            LocalDemoAuthenticationAdapter.TENANT_1_ID,
            "Nguyen Van A",
            "079201001111",
            "0901234567",
            "tenant1@phonghub.local",
            "Ben Tre",
            now
        );
        tenantRepository.save(tenant1);

        // 6. Contract for Room 101
        LocalDate startDate = LocalDate.now().minusMonths(2);
        LocalDate endDate = startDate.plusYears(1);
        ContractOccupant occupant = new ContractOccupant(
            UUID.randomUUID(),
            CONTRACT_1_ID,
            TENANT_RECORD_ID,
            true,
            startDate,
            null
        );
        Contract contract1 = new Contract(
            CONTRACT_1_ID,
            PROP_1_ID,
            ROOM_101_ID,
            TENANT_RECORD_ID,
            new BigDecimal("3500000"),
            new BigDecimal("3500000"),
            startDate,
            endDate,
            5,
            ContractStatus.ACTIVE,
            List.of(occupant),
            now,
            now
        );
        contractRepository.save(contract1);

        // 7. Maintenance ticket for Room 102
        MaintenanceTicket ticket = new MaintenanceTicket(
            TICKET_1_ID,
            ROOM_102_ID,
            PROP_1_ID,
            null,
            LocalDemoAuthenticationAdapter.TECH_1_ID,
            "Sua voi nuoc va kiem tra may lanh",
            "Voi nuoc bon rua bi ri, may lanh chay yeu",
            MaintenancePriority.HIGH,
            MaintenanceStatus.IN_PROGRESS,
            BigDecimal.ZERO,
            null,
            now,
            now
        );
        ticketRepository.save(ticket);
    }
}
