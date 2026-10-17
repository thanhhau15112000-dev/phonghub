package com.phonghub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.PropertyService;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #89: ẩn/khôi phục nhà trọ — quyền chủ trọ/admin, chặn khi còn hợp đồng hoặc hóa đơn, không lộ khỏi danh sách. */
class PropertyArchiveServiceUnitTest {

    private final UUID ownerId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser(ownerId, "owner@phonghub.local", "Chủ trọ", UserRole.OWNER);
    private final CurrentUser otherOwner = new CurrentUser(UUID.randomUUID(), "o2@phonghub.local", "Chủ khác", UserRole.OWNER);
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);

    private InMemoryPropertyRepository properties;
    private InMemoryContractRepository contracts;
    private InMemoryInvoiceRepository invoices;
    private InMemoryStaffPropertyAssignmentRepository assignments;
    private final List<AuditPort.AuditEvent> events = new ArrayList<>();
    private CurrentUser current;
    private PropertyUseCase service;
    private Property property;

    @BeforeEach
    void setUp() {
        properties = new InMemoryPropertyRepository();
        contracts = new InMemoryContractRepository();
        invoices = new InMemoryInvoiceRepository();
        assignments = new InMemoryStaffPropertyAssignmentRepository();
        AuthorizationService authorization = new AuthorizationService(assignments, properties,
            new InMemoryTenantRepository(), contracts);
        service = new PropertyService(properties, () -> current, authorization, events::add, contracts, invoices);
        property = properties.save(new Property(UUID.randomUUID(), "Nhà trọ Xanh", "1 Lê Lợi", null, 5, ownerId,
            PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        current = owner;
    }

    private Contract contract() {
        return Contract.create(property.id(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 5);
    }

    @Test
    @DisplayName("Chủ trọ ẩn nhà trọ không còn ràng buộc: biến mất khỏi danh sách, hiện ở mục đã ẩn, ghi nhật ký")
    void ownerArchivesAndRestores() {
        Property archived = service.archiveProperty(property.id());

        assertThat(archived.isArchived()).isTrue();
        assertThat(service.listAccessibleProperties()).isEmpty();
        assertThat(service.listArchivedProperties()).extracting(Property::id).containsExactly(property.id());
        assertThat(service.getProperty(property.id()).isArchived()).as("vẫn mở được trang chi tiết").isTrue();
        assertThat(events).extracting(AuditPort.AuditEvent::action).containsExactly("PROPERTY_ARCHIVED");

        Property restored = service.restoreProperty(property.id());
        assertThat(restored.isArchived()).isFalse();
        assertThat(service.listAccessibleProperties()).extracting(Property::id).containsExactly(property.id());
        assertThat(service.listArchivedProperties()).isEmpty();
        assertThat(events).extracting(AuditPort.AuditEvent::action)
            .containsExactly("PROPERTY_ARCHIVED", "PROPERTY_RESTORED");
    }

    @Test
    @DisplayName("Còn hợp đồng nháp hoặc đang hiệu lực -> 409, không đổi gì")
    void openContractBlocksArchive() {
        Contract draft = contracts.save(contract());

        assertThatThrownBy(() -> service.archiveProperty(property.id()))
            .isInstanceOf(ConflictException.class).hasMessageContaining("1 hợp đồng chưa kết thúc");

        draft.activate();
        contracts.save(draft);
        assertThatThrownBy(() -> service.archiveProperty(property.id())).isInstanceOf(ConflictException.class);
        assertThat(properties.findById(property.id()).orElseThrow().isArchived()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Hợp đồng đã kết thúc nhưng còn hóa đơn chưa thu -> 409; thu đủ thì ẩn được")
    void unpaidInvoiceBlocksArchive() {
        Contract ended = contract();
        ended.activate();
        Invoice invoice = invoices.save(Invoice.issueMonthlyRent(ended, YearMonth.of(2026, 3), "PH000001"));
        ended.terminate();
        contracts.save(ended);

        assertThatThrownBy(() -> service.archiveProperty(property.id()))
            .isInstanceOf(ConflictException.class).hasMessageContaining("1 hóa đơn chưa thu xong");

        invoice.applyPayment(invoice.getTotalAmount(), Instant.now());
        invoices.save(invoice);
        assertThat(service.archiveProperty(property.id()).isArchived()).isTrue();
    }

    @Test
    @DisplayName("Chủ trọ khác, nhân viên được phân công -> 403")
    void onlyOwnerOrAdmin() {
        current = otherOwner;
        assertThatThrownBy(() -> service.archiveProperty(property.id()))
            .isInstanceOf(UnauthorizedPropertyAccessException.class);

        UUID staffId = UUID.randomUUID();
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), staffId, property.id(), true, true,
            Instant.now()));
        current = new CurrentUser(staffId, "staff@phonghub.local", "Nhân viên", UserRole.STAFF);
        assertThatThrownBy(() -> service.archiveProperty(property.id()))
            .isInstanceOf(UnauthorizedPropertyAccessException.class);
        assertThat(service.listArchivedProperties()).isEmpty();

        current = admin;
        service.archiveProperty(property.id());
        current = otherOwner;
        assertThatThrownBy(() -> service.restoreProperty(property.id()))
            .isInstanceOf(UnauthorizedPropertyAccessException.class);
        assertThat(service.listArchivedProperties()).as("chủ khác không thấy nhà đã ẩn của người khác").isEmpty();
    }

    @Test
    @DisplayName("Ẩn hai lần, khôi phục nhà đang hoạt động, sửa/duyệt nhà đã ẩn -> 409")
    void conflictsOnArchivedState() {
        assertThatThrownBy(() -> service.restoreProperty(property.id())).isInstanceOf(ConflictException.class);
        service.archiveProperty(property.id());
        assertThatThrownBy(() -> service.archiveProperty(property.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.updateProperty(new PropertyUseCase.UpdatePropertyCommand(
            property.id(), "Tên mới", "1 Lê Lợi", null, 5)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("khôi phục");

        current = admin;
        assertThatThrownBy(() -> service.verifyProperty(property.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.rejectProperty(property.id(), "lý do")).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Sửa thông tin giữ nguyên trạng thái ẩn (không vô tình bỏ ẩn khi lưu lại bản ghi)")
    void updateInfoKeepsArchivedAt() {
        Instant at = Instant.parse("2026-10-01T00:00:00Z");
        Property archived = property.archive(at);
        assertThat(archived.updateInfo("A", "B", null, 1, PropertyApprovalStatus.VERIFIED, null).archivedAt())
            .isEqualTo(at);
        assertThat(archived.restore().archivedAt()).isNull();
    }
}
