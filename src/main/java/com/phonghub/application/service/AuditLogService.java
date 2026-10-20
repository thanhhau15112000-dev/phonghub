package com.phonghub.application.service;

import com.phonghub.application.port.in.AuditLogUseCase;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.UserRole;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuditLogService implements AuditLogUseCase {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AuditQueryPort auditQueryPort;
    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;

    public AuditLogService(
        AuditQueryPort auditQueryPort,
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort
    ) {
        this.auditQueryPort = auditQueryPort;
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
    }

    @Override
    public List<PropertyOption> selectableProperties() {
        CurrentUser user = requireAuditViewer();
        List<Property> properties = user.role() == UserRole.ADMIN
            ? propertyRepository.findAll()
            : propertyRepository.findByOwnerId(user.id());
        return properties.stream().map(p -> new PropertyOption(p.id(), p.name())).toList();
    }

    @Override
    public List<AuditQueryPort.Entry> search(SearchCommand command) {
        CurrentUser user = requireAuditViewer();
        Set<UUID> allowed = null;
        if (user.role() == UserRole.OWNER) {
            allowed = propertyRepository.findByOwnerId(user.id()).stream()
                .map(Property::id).collect(Collectors.toSet());
            if (command.propertyId() != null && !allowed.contains(command.propertyId())) {
                throw new UnauthorizedPropertyAccessException("Bạn chỉ được xem nhật ký của nhà trọ của mình.");
            }
        }
        Set<UUID> scope = command.propertyId() != null ? Set.of(command.propertyId()) : allowed;
        int page = Math.max(0, command.page());
        return auditQueryPort.search(new AuditQueryPort.Filter(
            scope,
            command.actorId(),
            command.targetType(),
            command.action(),
            command.from() == null ? null : command.from().atStartOfDay(DISPLAY_ZONE).toInstant(),
            command.to() == null ? null : command.to().plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant(),
            PAGE_SIZE + 1,
            page * PAGE_SIZE
        ));
    }

    private CurrentUser requireAuditViewer() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() != UserRole.ADMIN && user.role() != UserRole.OWNER) {
            throw new UnauthorizedPropertyAccessException("Chỉ chủ nhà trọ hoặc quản trị viên được xem nhật ký.");
        }
        return user;
    }
}
