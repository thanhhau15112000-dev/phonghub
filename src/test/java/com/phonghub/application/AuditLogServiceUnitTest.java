package com.phonghub.application;

import com.phonghub.application.port.in.AuditLogUseCase;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.service.AuditLogService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #78, #250: mỗi vai trò chỉ xem nhật ký liên quan và trong quyền của mình. */
class AuditLogServiceUnitTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID ownProperty = UUID.randomUUID();
    private final UUID otherProperty = UUID.randomUUID();

    private AuditQueryPort queryPort;
    private PropertyRepositoryPort propertyRepository;
    private CurrentUserPort currentUserPort;
    private StaffPropertyAssignmentPort staffAssignments;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        queryPort = mock(AuditQueryPort.class);
        propertyRepository = mock(PropertyRepositoryPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        staffAssignments = mock(StaffPropertyAssignmentPort.class);
        service = new AuditLogService(queryPort, propertyRepository, currentUserPort, staffAssignments);
        when(queryPort.search(any())).thenReturn(List.of());
        Property own = property(ownProperty, ownerId, "Nhà A");
        when(propertyRepository.findByOwnerId(ownerId)).thenReturn(List.of(own));
        when(propertyRepository.findById(ownProperty)).thenReturn(Optional.of(own));
        when(propertyRepository.findAll()).thenReturn(List.of(
            own, property(otherProperty, UUID.randomUUID(), "Nhà B")));
        when(staffAssignments.findPropertyIdsByUserId(staffId)).thenReturn(Set.of(ownProperty));
    }

    private static Property property(UUID id, UUID owner, String name) {
        return new Property(id, name, "addr", null, 1, owner, PropertyApprovalStatus.VERIFIED, null, Instant.now());
    }

    private void loginAs(UserRole role, UUID id) {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(id, role.name() + "@x", role.name(), role));
    }

    private void staffGrant(boolean collectPayment, boolean viewDocuments) {
        when(staffAssignments.findByUserId(staffId)).thenReturn(List.of(new StaffPropertyAssignment(
            UUID.randomUUID(), staffId, ownProperty, collectPayment, false, viewDocuments, Instant.now())));
    }

    private AuditLogUseCase.SearchCommand all() {
        return new AuditLogUseCase.SearchCommand(null, null, null, null, null, null, 0);
    }

    private AuditQueryPort.Filter capturedFilter() {
        ArgumentCaptor<AuditQueryPort.Filter> captor = ArgumentCaptor.forClass(AuditQueryPort.Filter.class);
        verify(queryPort).search(captor.capture());
        return captor.getValue();
    }

    private static AuditQueryPort.Entry entry(String targetType, UUID propertyId, String details) {
        return new AuditQueryPort.Entry(UUID.randomUUID(), "SOMETHING", null, null, targetType,
            UUID.randomUUID().toString(), propertyId, details, Instant.now());
    }

    @Test
    @DisplayName("admin không bị giới hạn phạm vi")
    void adminSeesEverything() {
        loginAs(UserRole.ADMIN, UUID.randomUUID());
        service.search(all());
        AuditQueryPort.Filter filter = capturedFilter();
        assertNull(filter.propertyIds());
        assertNull(filter.scope());
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
    @DisplayName("owner chỉ được xem nhà trọ của mình và thao tác trên tài khoản của chính mình")
    void ownerScopedToOwnProperties() {
        loginAs(UserRole.OWNER, ownerId);
        service.search(all());
        AuditQueryPort.Scope scope = capturedFilter().scope();
        assertEquals(Set.of(ownProperty), Set.copyOf(scope.propertyIds()));
        assertEquals(ownerId, scope.selfUserId());
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
    @DisplayName("nhân viên: phạm vi là các nhà trọ được phân công, chọn nhà khác thì bị từ chối")
    void staffScopedToAssignedProperties() {
        staffGrant(true, true);
        loginAs(UserRole.STAFF, staffId);
        service.search(all());
        AuditQueryPort.Scope scope = capturedFilter().scope();
        assertEquals(Set.of(ownProperty), Set.copyOf(scope.propertyIds()));
        assertEquals(staffId, scope.selfUserId());
        assertEquals(1, service.selectableProperties().size());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(
            new AuditLogUseCase.SearchCommand(otherProperty, null, null, null, null, null, 0)));
    }

    @Test
    @DisplayName("nhân viên không có quyền thu tiền thấy sự kiện hóa đơn nhưng số tiền bị che")
    void staffWithoutCollectPaymentSeesMaskedMoney() {
        staffGrant(false, true);
        loginAs(UserRole.STAFF, staffId);
        when(queryPort.search(any())).thenReturn(List.of(entry("INVOICE", ownProperty, "{\"amount\":\"50000\"}")));
        assertEquals("{\"amount\":\"***\"}", service.search(all()).getFirst().details());

        staffGrant(true, true);
        assertEquals("{\"amount\":\"50000\"}", service.search(all()).getFirst().details());
    }

    @Test
    @DisplayName("nhân viên không có quyền xem giấy tờ thì sự kiện giấy tờ không còn chi tiết")
    void staffWithoutDocumentRightSeesNoDocumentDetails() {
        staffGrant(true, false);
        loginAs(UserRole.STAFF, staffId);
        when(queryPort.search(any())).thenReturn(List.of(entry("TENANT_DOCUMENT", ownProperty, "{\"fileName\":\"cccd.jpg\"}")));
        assertNull(service.search(all()).getFirst().details());

        staffGrant(true, true);
        assertNotNull(service.search(all()).getFirst().details());
    }

    @Test
    @DisplayName("kỹ thuật viên: chỉ phiếu bảo trì được giao; số tiền ngoài phiếu bị che; không chọn được nhà trọ hay người khác")
    void technicianScope() {
        UUID techId = UUID.randomUUID();
        loginAs(UserRole.TECHNICIAN, techId);
        when(queryPort.search(any())).thenReturn(List.of(
            entry("MAINTENANCE_TICKET", ownProperty, "{\"quoteAmount\":\"200000\"}"),
            entry("INVOICE", ownProperty, "{\"amount\":\"50000\"}")));
        List<AuditQueryPort.Entry> seen = service.search(all());
        AuditQueryPort.Scope scope = capturedFilter().scope();
        assertEquals(techId, scope.technicianId());
        assertNull(scope.propertyIds());
        assertEquals("{\"quoteAmount\":\"200000\"}", seen.get(0).details(), "báo giá của phiếu được giữ");
        assertEquals("{\"amount\":\"***\"}", seen.get(1).details());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(
            new AuditLogUseCase.SearchCommand(ownProperty, null, null, null, null, null, 0)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(
            new AuditLogUseCase.SearchCommand(null, UUID.randomUUID(), null, null, null, null, 0)));
        assertEquals(0, service.selectableProperties().size());
    }

    @Test
    @DisplayName("người thuê: phạm vi theo tài khoản của mình, không chọn được nhà trọ hay người khác")
    void tenantScope() {
        UUID tenantUser = UUID.randomUUID();
        loginAs(UserRole.TENANT, tenantUser);
        service.search(all());
        AuditQueryPort.Scope scope = capturedFilter().scope();
        assertEquals(tenantUser, scope.tenantUserId());
        assertNull(scope.propertyIds());
        assertNull(scope.selfUserId());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(
            new AuditLogUseCase.SearchCommand(ownProperty, null, null, null, null, null, 0)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(
            new AuditLogUseCase.SearchCommand(null, UUID.randomUUID(), null, null, null, null, 0)));
        assertEquals(0, service.selectableProperties().size());
    }

    @Test
    @DisplayName("người chưa đăng nhập bị từ chối")
    void anonymousDenied() {
        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.search(all()));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.selectableProperties());
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
