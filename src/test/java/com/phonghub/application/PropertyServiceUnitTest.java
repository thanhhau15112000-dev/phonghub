package com.phonghub.application;

import com.phonghub.application.port.in.PropertyUseCase.CreatePropertyCommand;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.PropertyService;
import com.phonghub.domain.exception.InvalidPropertyStatusException;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PropertyService Unit Tests")
class PropertyServiceUnitTest {

    @Mock
    private PropertyRepositoryPort propertyRepository;

    @Mock
    private CurrentUserPort currentUserPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditPort auditPort;

    private PropertyService propertyService;

    private final UUID ownerId = UUID.randomUUID();
    private final CurrentUser ownerUser = new CurrentUser(
        ownerId,
        "owner@phonghub.local",
        "Nguyen Van Owner",
        UserRole.OWNER
    );

    private final UUID adminId = UUID.randomUUID();
    private final CurrentUser adminUser = new CurrentUser(
        adminId,
        "admin@phonghub.local",
        "Admin Root",
        UserRole.ADMIN
    );

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(
            propertyRepository,
            currentUserPort,
            authorizationService,
            auditPort
        );
    }

    @Nested
    @DisplayName("1. createProperty")
    class CreatePropertyTests {

        @Test
        @DisplayName("Chủ nhà (OWNER) tạo nhà trọ -> Trạng thái PENDING, gán đúng ownerId")
        void ownerCreatesProperty_success() {
            // Arrange
            when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            var command = new CreatePropertyCommand("  Nhà trọ Bình An  ", "  123 Nguyen Trai  ", "Gần trường ĐH", 10);

            // Act
            Property created = propertyService.createProperty(command);

            // Assert
            assertThat(created).isNotNull();
            assertThat(created.name()).isEqualTo("Nhà trọ Bình An");
            assertThat(created.address()).isEqualTo("123 Nguyen Trai");
            assertThat(created.description()).isEqualTo("Gần trường ĐH");
            assertThat(created.totalRooms()).isEqualTo(10);
            assertThat(created.ownerId()).isEqualTo(ownerId);
            assertThat(created.approvalStatus()).isEqualTo(PropertyApprovalStatus.PENDING);

            verify(authorizationService).assertCanCreateProperty(ownerUser);
            verify(propertyRepository).save(any(Property.class));
        }

        @Test
        @DisplayName("Quản trị viên (ADMIN) tạo nhà trọ -> Trạng thái VERIFIED ngay, ownerId là null")
        void adminCreatesProperty_success() {
            // Arrange
            when(currentUserPort.getCurrentUser()).thenReturn(adminUser);
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            var command = new CreatePropertyCommand("Kí túc xá Trung Tâm", "456 Le Loi", "Trung tâm", 50);

            // Act
            Property created = propertyService.createProperty(command);

            // Assert
            assertThat(created.approvalStatus()).isEqualTo(PropertyApprovalStatus.VERIFIED);
            assertThat(created.ownerId()).isNull();

            verify(authorizationService).assertCanCreateProperty(adminUser);
            verify(propertyRepository).save(any(Property.class));
        }

        @Test
        @DisplayName("Người dùng không có quyền tạo nhà trọ -> Ném UnauthorizedPropertyAccessException, không lưu DB")
        void unauthorizedUser_throwsException() {
            // Arrange
            CurrentUser tenantUser = new CurrentUser(UUID.randomUUID(), "tenant@phonghub.local", "Tenant", UserRole.TENANT);
            when(currentUserPort.getCurrentUser()).thenReturn(tenantUser);
            doThrow(new UnauthorizedPropertyAccessException("Access denied"))
                .when(authorizationService).assertCanCreateProperty(tenantUser);

            var command = new CreatePropertyCommand("Nhà trọ Test", "123 Đường 1", "Mô tả", 5);

            // Act & Assert
            assertThatThrownBy(() -> propertyService.createProperty(command))
                .isInstanceOf(UnauthorizedPropertyAccessException.class)
                .hasMessageContaining("Access denied");

            verify(propertyRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("2. listAccessibleProperties & getProperty")
    class ReadPropertyTests {

        @Test
        @DisplayName("listAccessibleProperties trả về danh sách các nhà trọ thuộc quyền của user")
        void listAccessibleProperties_success() {
            // Arrange
            when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
            UUID propId1 = UUID.randomUUID();
            UUID propId2 = UUID.randomUUID();
            Set<UUID> ids = Set.of(propId1, propId2);
            when(authorizationService.getAccessiblePropertyIds(ownerUser)).thenReturn(ids);

            List<Property> expected = List.of(
                new Property(propId1, "Nha 1", "Dia chi 1", "", 5, Instant.now()),
                new Property(propId2, "Nha 2", "Dia chi 2", "", 10, Instant.now())
            );
            when(propertyRepository.findAllById(ids)).thenReturn(expected);

            // Act
            List<Property> result = propertyService.listAccessibleProperties();

            // Assert
            assertThat(result).hasSize(2).isEqualTo(expected);
            verify(authorizationService).getAccessiblePropertyIds(ownerUser);
            verify(propertyRepository).findAllById(ids);
        }

        @Test
        @DisplayName("getProperty thành công khi user có quyền và nhà trọ tồn tại")
        void getProperty_success() {
            // Arrange
            UUID propId = UUID.randomUUID();
            Property prop = new Property(propId, "Nha 1", "Dia chi 1", "", 5, Instant.now());
            when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
            when(propertyRepository.findById(propId)).thenReturn(Optional.of(prop));

            // Act
            Property result = propertyService.getProperty(propId);

            // Assert
            assertThat(result).isEqualTo(prop);
            verify(authorizationService).assertCanAccessProperty(ownerUser, propId);
        }

        @Test
        @DisplayName("getProperty ném PropertyNotFoundException nếu nhà trọ không tồn tại")
        void getProperty_notFound_throwsException() {
            // Arrange
            UUID propId = UUID.randomUUID();
            when(currentUserPort.getCurrentUser()).thenReturn(ownerUser);
            when(propertyRepository.findById(propId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> propertyService.getProperty(propId))
                .isInstanceOf(PropertyNotFoundException.class)
                .hasMessageContaining(propId.toString());

            verify(authorizationService).assertCanAccessProperty(ownerUser, propId);
        }
    }

    @Nested
    @DisplayName("3. verifyProperty")
    class VerifyPropertyTests {

        @Test
        @DisplayName("Admin duyệt nhà trọ PENDING -> Đổi sang VERIFIED và ghi log AuditPort")
        void adminVerifiesPendingProperty_success() {
            // Arrange
            UUID propId = UUID.randomUUID();
            Property pendingProperty = new Property(
                propId, "Nhà Trọ Mới", "Địa chỉ", "Mô tả", 10, ownerId,
                PropertyApprovalStatus.PENDING, null, Instant.now()
            );

            when(currentUserPort.getCurrentUser()).thenReturn(adminUser);
            when(propertyRepository.findById(propId)).thenReturn(Optional.of(pendingProperty));
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Property verified = propertyService.verifyProperty(propId);

            // Assert
            assertThat(verified.approvalStatus()).isEqualTo(PropertyApprovalStatus.VERIFIED);
            verify(authorizationService).assertAdmin(adminUser);

            ArgumentCaptor<AuditPort.AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditPort.AuditEvent.class);
            verify(auditPort).recordEvent(auditCaptor.capture());
            AuditPort.AuditEvent capturedAudit = auditCaptor.getValue();
            assertThat(capturedAudit.action()).isEqualTo("ADMIN_VERIFY_PROPERTY");
            assertThat(capturedAudit.actorId()).isEqualTo(adminId);
            assertThat(capturedAudit.targetId()).isEqualTo(propId.toString());
            assertThat(capturedAudit.details()).containsEntry("newStatus", "VERIFIED");
        }

        @Test
        @DisplayName("Duyệt nhà trọ không ở trạng thái PENDING -> Ném InvalidPropertyStatusException")
        void verifyAlreadyVerifiedProperty_throwsException() {
            // Arrange
            UUID propId = UUID.randomUUID();
            Property verifiedProperty = new Property(
                propId, "Nhà Trọ Đã Duyệt", "Địa chỉ", "Mô tả", 10, ownerId,
                PropertyApprovalStatus.VERIFIED, null, Instant.now()
            );

            when(currentUserPort.getCurrentUser()).thenReturn(adminUser);
            when(propertyRepository.findById(propId)).thenReturn(Optional.of(verifiedProperty));

            // Act & Assert
            assertThatThrownBy(() -> propertyService.verifyProperty(propId))
                .isInstanceOf(InvalidPropertyStatusException.class)
                .hasMessageContaining("PENDING");

            verify(propertyRepository, never()).save(any());
            verify(auditPort, never()).recordEvent(any());
        }
    }

    @Nested
    @DisplayName("4. rejectProperty")
    class RejectPropertyTests {

        @Test
        @DisplayName("Admin từ chối nhà trọ PENDING kèm lý do -> Đổi sang REJECTED và ghi log AuditPort")
        void adminRejectsPendingProperty_success() {
            // Arrange
            UUID propId = UUID.randomUUID();
            Property pendingProperty = new Property(
                propId, "Nhà Trọ Sai Giấy Tờ", "Địa chỉ", "Mô tả", 10, ownerId,
                PropertyApprovalStatus.PENDING, null, Instant.now()
            );

            when(currentUserPort.getCurrentUser()).thenReturn(adminUser);
            when(propertyRepository.findById(propId)).thenReturn(Optional.of(pendingProperty));
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Property rejected = propertyService.rejectProperty(propId, "  Thiếu giấy tờ PCCC  ");

            // Assert
            assertThat(rejected.approvalStatus()).isEqualTo(PropertyApprovalStatus.REJECTED);
            assertThat(rejected.rejectionReason()).isEqualTo("Thiếu giấy tờ PCCC");

            ArgumentCaptor<AuditPort.AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditPort.AuditEvent.class);
            verify(auditPort).recordEvent(auditCaptor.capture());
            AuditPort.AuditEvent capturedAudit = auditCaptor.getValue();
            assertThat(capturedAudit.action()).isEqualTo("ADMIN_REJECT_PROPERTY");
            assertThat(capturedAudit.actorId()).isEqualTo(adminId);
            assertThat(capturedAudit.targetId()).isEqualTo(propId.toString());
            assertThat(capturedAudit.details()).containsEntry("reason", "Thiếu giấy tờ PCCC");
        }

        @Test
        @DisplayName("Từ chối nhà trọ với lý do để trống -> Ném IllegalArgumentException")
        void rejectWithBlankReason_throwsException() {
            // Arrange
            UUID propId = UUID.randomUUID();
            when(currentUserPort.getCurrentUser()).thenReturn(adminUser);

            // Act & Assert
            assertThatThrownBy(() -> propertyService.rejectProperty(propId, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lý do từ chối không được để trống");

            verify(propertyRepository, never()).findById(any());
            verify(propertyRepository, never()).save(any());
        }

        @Test
        @DisplayName("Từ chối nhà trọ không ở trạng thái PENDING -> Ném InvalidPropertyStatusException")
        void rejectAlreadyProcessedProperty_throwsException() {
            // Arrange
            UUID propId = UUID.randomUUID();
            Property rejectedProperty = new Property(
                propId, "Nhà Trọ Cũ", "Địa chỉ", "Mô tả", 10, ownerId,
                PropertyApprovalStatus.REJECTED, "Đã từ chối trước đó", Instant.now()
            );

            when(currentUserPort.getCurrentUser()).thenReturn(adminUser);
            when(propertyRepository.findById(propId)).thenReturn(Optional.of(rejectedProperty));

            // Act & Assert
            assertThatThrownBy(() -> propertyService.rejectProperty(propId, "Lý do mới"))
                .isInstanceOf(InvalidPropertyStatusException.class)
                .hasMessageContaining("PENDING");

            verify(propertyRepository, never()).save(any());
            verify(auditPort, never()).recordEvent(any());
        }
    }
}
