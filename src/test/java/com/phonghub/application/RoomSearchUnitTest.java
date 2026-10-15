package com.phonghub.application;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.RoomService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #84: lọc kết hợp + phân trang, giữ phân quyền theo nhà trọ. */
class RoomSearchUnitTest {

    private final UUID propertyId = UUID.randomUUID();
    private InMemoryRoomRepository rooms;
    private CurrentUserPort currentUserPort;
    private AuthorizationService authorization;
    private TenantRepositoryPort tenants;
    private ContractRepositoryPort contracts;
    private RoomService service;

    private Room room(String number, int floor, String price, RoomStatus status) {
        Room room = new Room(UUID.randomUUID(), propertyId, number, floor, new BigDecimal("20"), new BigDecimal(price), 2,
            status, Instant.now(), Instant.now());
        return rooms.save(room);
    }

    @BeforeEach
    void setUp() {
        rooms = new InMemoryRoomRepository();
        currentUserPort = mock(CurrentUserPort.class);
        authorization = mock(AuthorizationService.class);
        tenants = mock(TenantRepositoryPort.class);
        contracts = mock(ContractRepositoryPort.class);
        service = new RoomService(rooms, mock(PropertyRepositoryPort.class), mock(MaintenanceTicketRepositoryPort.class),
            contracts, tenants, currentUserPort, authorization);
        when(currentUserPort.getCurrentUser())
            .thenReturn(new CurrentUser(UUID.randomUUID(), "o@x", "Owner", UserRole.OWNER));
    }

    private List<String> numbers(RoomPage page) {
        return page.items().stream().map(Room::getRoomNumber).toList();
    }

    @Test
    @DisplayName("điều kiện không hợp lệ bị từ chối ngay khi tạo")
    void criteriaValidation() {
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(propertyId, null,
            new BigDecimal("5"), new BigDecimal("1"), null, null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(propertyId, null,
            new BigDecimal("-1"), null, null, null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(propertyId, null, null, null, null,
            null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(propertyId, null, null, null, null,
            null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(propertyId, null, null, null, null,
            null, 0, RoomSearchCriteria.MAX_PAGE_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(propertyId, null, null, null, null,
            null, 1, null), "chọn trang mà không có size");
        assertThrows(IllegalArgumentException.class, () -> new RoomSearchCriteria(null, null, null, null, null,
            null, 0, null));
        assertTrue(RoomSearchCriteria.all(propertyId).isUnfiltered());
        assertFalse(new RoomSearchCriteria(propertyId, null, null, null, null, null, 0, 10).isUnfiltered());
    }

    @Test
    @DisplayName("khoảng giá tính cả hai đầu mút")
    void priceBoundsInclusive() {
        room("P101", 1, "3000000", RoomStatus.AVAILABLE);
        room("P102", 1, "3500000", RoomStatus.AVAILABLE);
        room("P103", 1, "4000000", RoomStatus.AVAILABLE);

        assertEquals(List.of("P101", "P102", "P103"), numbers(service.searchRooms(new RoomSearchCriteria(propertyId,
            null, new BigDecimal("3000000"), new BigDecimal("4000000"), null, null, 0, null))));
        assertEquals(List.of("P102"), numbers(service.searchRooms(new RoomSearchCriteria(propertyId,
            null, new BigDecimal("3500000"), new BigDecimal("3500000"), null, null, 0, null))));
    }

    @Test
    @DisplayName("các điều kiện kết hợp bằng AND, sắp theo tầng rồi số phòng")
    void combinedFilters() {
        room("P201", 2, "4000000", RoomStatus.AVAILABLE);
        room("P101", 1, "3000000", RoomStatus.AVAILABLE);
        room("P102", 1, "3500000", RoomStatus.OCCUPIED);
        room("P103", 1, "4500000", RoomStatus.AVAILABLE);
        // phòng của nhà trọ khác không bao giờ lọt vào
        rooms.save(new Room(UUID.randomUUID(), UUID.randomUUID(), "X1", 1, null, new BigDecimal("3000000"), 2,
            RoomStatus.AVAILABLE, Instant.now(), Instant.now()));

        assertEquals(List.of("P101", "P102", "P103", "P201"), numbers(service.searchRooms(RoomSearchCriteria.all(propertyId))));
        assertEquals(List.of("P101", "P103", "P201"), numbers(service.searchRooms(new RoomSearchCriteria(propertyId,
            RoomStatus.AVAILABLE, null, null, null, null, 0, null))));
        assertEquals(List.of("P101", "P103"), numbers(service.searchRooms(new RoomSearchCriteria(propertyId,
            RoomStatus.AVAILABLE, null, null, 1, null, 0, null))));
        assertEquals(List.of("P103"), numbers(service.searchRooms(new RoomSearchCriteria(propertyId,
            RoomStatus.AVAILABLE, new BigDecimal("4000000"), null, 1, null, 0, null))));
        assertEquals(List.of("P201"), numbers(service.searchRooms(new RoomSearchCriteria(propertyId,
            null, new BigDecimal("3800000"), new BigDecimal("4200000"), 2, null, 0, null))));
        assertTrue(numbers(service.searchRooms(new RoomSearchCriteria(propertyId, RoomStatus.MAINTENANCE, null, null,
            null, null, 0, null))).isEmpty());
    }

    @Test
    @DisplayName("phân trang trả đúng trang, tổng số và cờ trước/sau")
    void paging() {
        for (int i = 1; i <= 5; i++) {
            room("P10" + i, 1, "3000000", RoomStatus.AVAILABLE);
        }
        RoomPage first = service.searchRooms(new RoomSearchCriteria(propertyId, null, null, null, null, null, 0, 2));
        RoomPage last = service.searchRooms(new RoomSearchCriteria(propertyId, null, null, null, null, null, 2, 2));
        RoomPage beyond = service.searchRooms(new RoomSearchCriteria(propertyId, null, null, null, null, null, 9, 2));

        assertEquals(List.of("P101", "P102"), numbers(first));
        assertEquals(5, first.totalElements());
        assertEquals(3, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(first.hasPrevious());
        assertEquals(List.of("P105"), numbers(last));
        assertFalse(last.hasNext());
        assertTrue(beyond.items().isEmpty());
        assertEquals(5, beyond.totalElements());
    }

    @Test
    @DisplayName("không có quyền truy cập nhà trọ thì bị từ chối trước khi truy vấn")
    void authorizationEnforced() {
        doThrow(new UnauthorizedPropertyAccessException("no")).when(authorization).assertCanAccessProperty(any(), any());
        room("P101", 1, "3000000", RoomStatus.AVAILABLE);

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.searchRooms(RoomSearchCriteria.all(propertyId)));
    }

    @Test
    @DisplayName("người thuê chỉ thấy phòng của mình dù lọc thế nào")
    void tenantSeesOnlyOwnRoom() {
        Room own = room("P101", 1, "3000000", RoomStatus.OCCUPIED);
        room("P102", 1, "3000000", RoomStatus.AVAILABLE);
        UUID tenantUserId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser())
            .thenReturn(new CurrentUser(tenantUserId, "t@x", "Tenant", UserRole.TENANT));
        Tenant tenant = Tenant.create(tenantUserId, "Người thuê", "079201004321", "0900000009", null, null);
        when(tenants.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
        Contract contract = new Contract(UUID.randomUUID(), propertyId, own.getId(), tenant.id(),
            new BigDecimal("1000000"), new BigDecimal("3000000"), LocalDate.now().minusMonths(1),
            LocalDate.now().plusMonths(11), 5, ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now());
        when(contracts.findByPrimaryTenantId(tenant.id())).thenReturn(List.of(contract));

        assertEquals(List.of("P101"), numbers(service.searchRooms(RoomSearchCriteria.all(propertyId))));
        assertTrue(numbers(service.searchRooms(new RoomSearchCriteria(propertyId, RoomStatus.AVAILABLE, null, null,
            null, null, 0, null))).isEmpty(), "bộ lọc không mở rộng quyền xem");
    }

    @Test
    @DisplayName("người thuê chưa có hợp đồng không thấy phòng nào")
    void tenantWithoutContractSeesNothing() {
        room("P101", 1, "3000000", RoomStatus.AVAILABLE);
        UUID tenantUserId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser())
            .thenReturn(new CurrentUser(tenantUserId, "t@x", "Tenant", UserRole.TENANT));
        when(tenants.findByUserId(tenantUserId)).thenReturn(Optional.empty());

        RoomPage page = service.searchRooms(RoomSearchCriteria.all(propertyId));
        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalElements());
    }

    @Test
    @DisplayName("người ở cùng (không đứng tên hợp đồng) vẫn thấy đúng phòng mình")
    void occupantSeesSharedRoom() {
        Room shared = room("P101", 1, "3000000", RoomStatus.OCCUPIED);
        room("P102", 1, "3000000", RoomStatus.OCCUPIED);
        Tenant occupant = loginAsTenant();
        when(contracts.findByPrimaryTenantId(occupant.id())).thenReturn(List.of());
        when(contracts.findByOccupantTenantId(occupant.id()))
            .thenReturn(List.of(contract(propertyId, shared.getId(), ContractStatus.ACTIVE)));

        assertEquals(List.of("P101"), numbers(service.searchRooms(RoomSearchCriteria.all(propertyId))));
    }

    @Test
    @DisplayName("hợp đồng ở nhà trọ khác hoặc đã kết thúc không cho thấy phòng")
    void tenantContractOutsideScopeSeesNothing() {
        Room room = room("P101", 1, "3000000", RoomStatus.AVAILABLE);
        Tenant tenant = loginAsTenant();
        when(contracts.findByPrimaryTenantId(tenant.id())).thenReturn(List.of(
            contract(UUID.randomUUID(), room.getId(), ContractStatus.ACTIVE),
            contract(propertyId, room.getId(), ContractStatus.TERMINATED)));
        when(contracts.findByOccupantTenantId(tenant.id())).thenReturn(List.of());

        assertTrue(service.searchRooms(RoomSearchCriteria.all(propertyId)).items().isEmpty());
    }

    private Tenant loginAsTenant() {
        UUID tenantUserId = UUID.randomUUID();
        when(currentUserPort.getCurrentUser())
            .thenReturn(new CurrentUser(tenantUserId, "t@x", "Tenant", UserRole.TENANT));
        Tenant tenant = Tenant.create(tenantUserId, "Người thuê", "079201004322", "0900000008", null, null);
        when(tenants.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
        return tenant;
    }

    private Contract contract(UUID contractPropertyId, UUID roomId, ContractStatus status) {
        return new Contract(UUID.randomUUID(), contractPropertyId, roomId, UUID.randomUUID(),
            new BigDecimal("1000000"), new BigDecimal("3000000"), LocalDate.now().minusMonths(1),
            LocalDate.now().plusMonths(11), 5, status, List.of(), Instant.now(), Instant.now());
    }
}
