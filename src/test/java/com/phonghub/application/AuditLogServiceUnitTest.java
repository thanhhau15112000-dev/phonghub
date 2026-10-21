package com.phonghub.application;

import com.phonghub.application.port.in.AuditLogUseCase;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.service.AuditLogService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #78: admin xem tất cả, owner chỉ xem nhà của mình, vai trò khác bị từ chối. */
class AuditLogServiceUnitTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID ownProperty = UUID.randomUUID();
    private final UUID otherProperty = UUID.randomUUID();

    private AuditQueryPort queryPort;
    private PropertyRepositoryPort propertyRepository;
    private CurrentUserPort currentUserPort;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        queryPort = mock(AuditQueryPort.class);
        propertyRepository = mock(PropertyRepositoryPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        service = new AuditLogService(queryPort, propertyRepository, currentUserPort);
        when(queryPort.search(any())).thenReturn(List.of());
        when(propertyRepository.findByOwnerId(ownerId)).thenReturn(List.of(property(ownProperty, ownerId, "Nhà A")));
        when(propertyRepository.findAll()).thenReturn(List.of(
            property(ownProperty, ownerId, "Nhà A"), property(otherProperty, UUID.randomUUID(), "Nhà B")));
    }

    private static Property property(UUID id, UUID owner, String name) {
        return new Property(id, name, "addr", null, 1, owner, PropertyApprovalStatus.VERIFIED, null, Instant.now());
    }

    private void loginAs(UserRole role, UUID id) {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(id, role.name() + "@x", role.name(), role));
    }

    private AuditLogUseCase.SearchCommand all() {
        return new AuditLogUseCase.SearchCommand(null, null, null, null, null, null, 0);
    }

    private AuditQueryPort.Filter capturedFilter() {
        ArgumentCaptor<AuditQueryPort.Filter> captor = ArgumentCaptor.forClass(AuditQueryPort.Filter.class);
        verify(queryPort).search(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("admin không bị giới hạn nhà trọ")
    void adminSeesEverything() {
        loginAs(UserRole.ADMIN, UUID.randomUUID());
        service.search(all());
        assertNull(capturedFilter().propertyIds());
        assertEquals(2, service.selectableProperties().size());
    }

    @Test
    @DisplayName("#163: admin xem được sự kiện nhưng số tiền trong chi tiết bị che; owner thấy đầy đủ")
    void adminSeesEventsWithoutMoney() {
        AuditQueryPort.Entry entry = new AuditQueryPort.Entry(UUID.randomUUID(), "LATE_FEE_APPLIED", null, null,
            "INVOICE", UUID.randomUUID().toString(), ownProperty,
            "{\"amount\":\"50000\",\"daysOverdue\":3}", Instant.now());
        when(queryPort.search(any())).thenReturn(List.of(entry));

        loginAs(UserRole.ADMIN, UUID.randomUUID());
        AuditQueryPort.Entry seenByAdmin = service.search(all()).getFirst();
        assertEquals("{\"amount\":\"***\",\"daysOverdue\":3}", seenByAdmin.details());
        assertEquals("LATE_FEE_APPLIED", seenByAdmin.action());
        assertEquals(entry.targetId(), seenByAdmin.targetId());

        loginAs(UserRole.OWNER, ownerId);
        assertEquals(entry.details(), service.search(all()).getFirst().details());
    }

    @Test
    @DisplayName("owner chỉ được xem các nhà trọ của mình")
    void ownerScopedToOwnProperties() {
        loginAs(UserRole.OWNER, ownerId);
        service.search(all());
        assertEquals(Set.of(ownProperty), Set.copyOf(capturedFilter().propertyIds()));
        assertEquals(1, service.selectableProperties().size());
    }

    @Test
    @DisplayName("owner chọn nhà trọ của người khác thì bị từ chối và không truy vấn")
    void ownerCannotQueryForeignProperty() {
        loginAs(UserRole.OWNER, ownerId);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(
            new AuditLogUseCase.SearchCommand(otherProperty, null, null, null, null, null, 0)));
        verify(queryPort, never()).search(any());
    }

    @Test
    @DisplayName("owner chọn đúng nhà trọ của mình thì chỉ lọc nhà đó")
    void ownerFiltersOwnProperty() {
        loginAs(UserRole.OWNER, ownerId);
        service.search(new AuditLogUseCase.SearchCommand(ownProperty, null, null, null, null, null, 0));
        assertEquals(Set.of(ownProperty), Set.copyOf(capturedFilter().propertyIds()));
    }

    @Test
    @DisplayName("staff, kỹ thuật viên, người thuê và người chưa đăng nhập bị từ chối")
    void otherRolesDenied() {
        for (UserRole role : new UserRole[] {UserRole.STAFF, UserRole.TECHNICIAN, UserRole.TENANT}) {
            loginAs(role, UUID.randomUUID());
            assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(all()), role.name());
            assertThrows(UnauthorizedPropertyAccessException.class, () -> service.selectableProperties(), role.name());
        }
        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(all()));
        verify(queryPort, never()).search(any());
    }

    @Test
    @DisplayName("khoảng ngày được đổi thành [từ 00:00, đến hết ngày) theo giờ Việt Nam và phân trang theo 50 dòng")
    void dateRangeAndPaging() {
        loginAs(UserRole.ADMIN, UUID.randomUUID());
        service.search(new AuditLogUseCase.SearchCommand(
            null, null, "ROOM", "ROOM_UPDATED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), 2));
        AuditQueryPort.Filter filter = capturedFilter();
        assertEquals(Instant.parse("2026-09-30T17:00:00Z"), filter.from());
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), filter.to());
        assertEquals(AuditLogUseCase.PAGE_SIZE + 1, filter.limit(), "lấy thừa 1 dòng để biết còn trang sau");
        assertEquals(2 * AuditLogUseCase.PAGE_SIZE, filter.offset());
        assertEquals("ROOM", filter.targetType());
        assertEquals("ROOM_UPDATED", filter.action());
    }
}
