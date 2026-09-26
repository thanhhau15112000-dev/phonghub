package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.DemoFixturePort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
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
    public static final UUID PROP_3_ID = DemoFixturePort.PROP_3_ID;

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
        User admin = new User(DemoFixturePort.ADMIN_ID, "admin", "admin@phonghub.local", "Quản trị viên", "0900000001", UserRole.ADMIN, User.UserStatus.ACTIVE, false, now);
        User owner1 = new User(DemoFixturePort.OWNER_1_ID, "owner1", "owner1@phonghub.local", "Chủ trọ Nguyễn Văn B", "0900000005", UserRole.OWNER, User.UserStatus.ACTIVE, false, now);
        User owner2 = new User(DemoFixturePort.OWNER_2_ID, "owner2", "owner2@phonghub.local", "Chủ trọ Trần Thị C", "0900000006", UserRole.OWNER, User.UserStatus.ACTIVE, false, now);
        User staff1 = new User(DemoFixturePort.STAFF_1_ID, "staff1", "staff1@phonghub.local", "Nhân viên Quận 7", "0900000002", UserRole.STAFF, User.UserStatus.ACTIVE, false, now);
        User staff2 = new User(DemoFixturePort.STAFF_2_ID, "staff2", "staff2@phonghub.local", "Nhân viên Tân Bình", "0900000003", UserRole.STAFF, User.UserStatus.ACTIVE, false, now);
        User tech1 = new User(DemoFixturePort.TECH_1_ID, "tech1", "tech1@phonghub.local", "Kỹ thuật viên", "0900000004", UserRole.TECHNICIAN, User.UserStatus.ACTIVE, false, now);
        User tenantUser1 = new User(DemoFixturePort.TENANT_1_ID, "tenant1", "tenant1@phonghub.local", "Nguyễn Văn A", "0901234567", UserRole.TENANT, User.UserStatus.ACTIVE, true, now);

        userRepository.save(admin);
        userRepository.save(owner1);
        userRepository.save(owner2);
        userRepository.save(staff1);
        userRepository.save(staff2);
        userRepository.save(tech1);
        userRepository.save(tenantUser1);

        // 2. Properties
        Property prop1 = new Property(
            PROP_1_ID,
            "Nhà trọ Xanh - Quận 7",
            "123 Nguyễn Thị Thập, Phường Tân Quy, Quận 7, TP.HCM",
            "Khu trọ dành cho sinh viên và người đi làm",
            10,
            DemoFixturePort.OWNER_1_ID,
            PropertyApprovalStatus.VERIFIED,
            null,
            now
        );
        Property prop2 = new Property(
            PROP_2_ID,
            "Khu trọ Tân Bình",
            "45 Cộng Hòa, Phường 13, Quận Tân Bình, TP.HCM",
            "Nhà trọ gần sân bay, yên tĩnh",
            5,
            DemoFixturePort.OWNER_1_ID,
            PropertyApprovalStatus.PENDING,
            null,
            now
        );
        Property prop3 = new Property(
            PROP_3_ID,
            "Nhà trọ Bình Thạnh",
            "78 Xô Viết Nghệ Tĩnh, Phường 21, Bình Thạnh, TP.HCM",
            "Nhà trọ ven sông",
            8,
            DemoFixturePort.OWNER_1_ID,
            PropertyApprovalStatus.REJECTED,
            "Giấy phép kinh doanh chưa hợp lệ hoặc thiếu chứng nhận PCCC",
            now
        );

        propertyRepository.save(prop1);
        propertyRepository.save(prop2);
        propertyRepository.save(prop3);

        // 3. Staff & Tech Assignments
        // Staff 1 assigned to Property 1
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), DemoFixturePort.STAFF_1_ID, PROP_1_ID, true, true, now));
        // Staff 2 assigned to Property 2
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), DemoFixturePort.STAFF_2_ID, PROP_2_ID, true, true, now));
        // Tech 1 assigned to Property 1
        assignmentRepository.save(new StaffPropertyAssignment(UUID.randomUUID(), DemoFixturePort.TECH_1_ID, PROP_1_ID, false, false, now));

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
            DemoFixturePort.TENANT_1_ID,
            "Nguyễn Văn A",
            "079201001111",
            "0901234567",
            "tenant1@phonghub.local",
            "Bến Tre",
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
            DemoFixturePort.TECH_1_ID,
            "Sửa vòi nước và kiểm tra máy lạnh",
            "Vòi nước bồn rửa bị rỉ, máy lạnh chạy yếu",
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
