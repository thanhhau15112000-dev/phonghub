package com.phonghub.application.service;

import com.phonghub.application.port.in.TenantPrivacyUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantPrivacyRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DocumentRevealDeniedException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.TenantHasActiveContractException;
import com.phonghub.domain.exception.TenantProfileLockedException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.TenantDocument;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.TenantPrivacy;
import com.phonghub.domain.model.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantPrivacyService implements TenantPrivacyUseCase {

    private static final Logger log = LoggerFactory.getLogger(TenantPrivacyService.class);

    private final TenantPrivacyRepositoryPort privacyRepository;
    private final TenantRepositoryPort tenantRepository;
    private final TenantDocumentRepositoryPort documentRepository;
    private final ContractRepositoryPort contractRepository;
    private final FileStoragePort storagePort;
    private final TenantLockPolicy lockPolicy;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final Clock clock;
    private final PurgeMode purgeMode;
    private final IdentityProviderPort identityProvider;
    private final PropertyRepositoryPort propertyRepository;
    private final UserRepositoryPort userRepository;
    private final NotificationRepositoryPort notificationRepository;

    public TenantPrivacyService(
        TenantPrivacyRepositoryPort privacyRepository,
        TenantRepositoryPort tenantRepository,
        TenantDocumentRepositoryPort documentRepository,
        ContractRepositoryPort contractRepository,
        FileStoragePort storagePort,
        TenantLockPolicy lockPolicy,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        Clock clock,
        PurgeMode purgeMode,
        IdentityProviderPort identityProvider,
        PropertyRepositoryPort propertyRepository,
        UserRepositoryPort userRepository,
        NotificationRepositoryPort notificationRepository
    ) {
        this.purgeMode = purgeMode;
        this.identityProvider = identityProvider;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.privacyRepository = privacyRepository;
        this.tenantRepository = tenantRepository;
        this.documentRepository = documentRepository;
        this.contractRepository = contractRepository;
        this.storagePort = storagePort;
        this.lockPolicy = lockPolicy;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Override
    public PrivacyStatus status(UUID propertyId, UUID tenantId) {
        authorizationService.assertCanManageProperty(currentUserPort.getCurrentUser(), propertyId);
        assertTenantInProperty(propertyId, tenantId);
        return statusOf(tenantId);
    }

    @Override
    public PrivacyStatus setLegalHold(UUID propertyId, UUID tenantId, String reason) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(actor, propertyId);
        assertTenantInProperty(propertyId, tenantId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nêu lý do giữ pháp lý");
        }
        TenantPrivacy current = privacyRepository.find(tenantId).orElseGet(() -> TenantPrivacy.none(tenantId));
        privacyRepository.save(current.withLegalHold(reason, actor.id(), clock.instant()));
        audit("TENANT_LEGAL_HOLD_SET", actor.id(), tenantId, Map.of("propertyId", propertyId.toString()));
        return statusOf(tenantId);
    }

    @Override
    public PrivacyStatus clearLegalHold(UUID propertyId, UUID tenantId) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(actor, propertyId);
        assertTenantInProperty(propertyId, tenantId);
        TenantPrivacy current = privacyRepository.find(tenantId).orElseGet(() -> TenantPrivacy.none(tenantId));
        privacyRepository.save(current.withoutLegalHold());
        audit("TENANT_LEGAL_HOLD_CLEARED", actor.id(), tenantId, Map.of("propertyId", propertyId.toString()));
        return statusOf(tenantId);
    }

    @Override
    public ErasureOutcome requestErasure() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null || user.role() != UserRole.TENANT) {
            throw new UnauthorizedPropertyAccessException("Chỉ người thuê mới tự yêu cầu xóa dữ liệu của mình");
        }
        Tenant tenant = tenantRepository.findByUserId(user.id())
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy hồ sơ người thuê của tài khoản này"));
        TenantPrivacy privacy = privacyRepository.find(tenant.id()).orElseGet(() -> TenantPrivacy.none(tenant.id()));
        if (privacy.purged()) {
            return ErasureOutcome.PURGED;
        }
        if (lockPolicy.lockInfo(tenant.id()).live()) {
            throw new TenantHasActiveContractException(
                "Bạn còn hợp đồng đang hiệu lực; chỉ yêu cầu xóa được sau khi đã check-out.");
        }
        privacy = privacyRepository.save(privacy.withErasureRequested(clock.instant()));
        audit("TENANT_ERASURE_REQUESTED", user.id(), tenant.id(), Map.of("legalHold", privacy.legalHold()));
        if (privacy.legalHold()) {
            return ErasureOutcome.PENDING_LEGAL_HOLD;
        }
        if (purgeMode == PurgeMode.APPROVAL) {
            notifyOwnersOnce(tenant, privacy, "Người thuê yêu cầu xóa giấy tờ danh tính; cần chủ trọ xác nhận.");
            return ErasureOutcome.PENDING_APPROVAL;
        }
        purge(tenant, privacy, "ERASURE_REQUEST", user.id());
        return ErasureOutcome.PURGED;
    }

    @Override
    public int purgeExpired(Instant now) {
        Set<UUID> candidates = new LinkedHashSet<>();
        for (Contract contract : contractRepository.findAll()) {
            candidates.add(contract.getPrimaryTenantId());
            for (ContractOccupant occupant : contract.getOccupants()) {
                candidates.add(occupant.tenantId());
            }
        }
        int purged = 0;
        for (UUID tenantId : candidates) {
            try {
                if (purgeIfDue(tenantId, now)) {
                    purged++;
                }
            } catch (RuntimeException ex) {
                log.error("Không xóa được dữ liệu giấy tờ của tenant {}; sẽ thử lại ở lượt sau", tenantId, ex);
            }
        }
        return purged;
    }

    private boolean purgeIfDue(UUID tenantId, Instant now) {
        TenantPrivacy privacy = privacyRepository.find(tenantId).orElseGet(() -> TenantPrivacy.none(tenantId));
        if (privacy.purged() || privacy.legalHold()) {
            return false;
        }
        TenantLockPolicy.LockInfo info = lockPolicy.lockInfo(tenantId);
        if (info.live()) {
            return false;
        }
        boolean due = privacy.erasureRequestedAt() != null
            || (info.purgeDueAt() != null && !now.isBefore(info.purgeDueAt()));
        if (!due) {
            return false;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return false;
        }
        if (purgeMode == PurgeMode.APPROVAL) {
            // Chỉ báo owner một lần; việc xóa chờ owner/admin xác nhận.
            notifyOwnersOnce(tenant, privacy, "Giấy tờ danh tính của người thuê đã đến hạn xóa; cần chủ trọ xác nhận.");
            return false;
        }
        purge(tenant, privacy, privacy.erasureRequestedAt() != null ? "ERASURE_REQUEST" : "RETENTION_EXPIRED", null);
        return true;
    }

    @Override
    public List<PendingPurge> pendingPurges(UUID propertyId) {
        authorizationService.assertOwnerOrAdmin(currentUserPort.getCurrentUser(), propertyId);
        Instant now = clock.instant();
        List<PendingPurge> pending = new java.util.ArrayList<>();
        for (UUID tenantId : tenantIdsOf(propertyId)) {
            TenantPrivacy privacy = privacyRepository.find(tenantId).orElseGet(() -> TenantPrivacy.none(tenantId));
            TenantLockPolicy.LockInfo info = lockPolicy.lockInfo(tenantId);
            if (isDue(privacy, info, now)) {
                tenantRepository.findById(tenantId).ifPresent(tenant -> pending.add(new PendingPurge(tenantId,
                    tenant.fullName(), info.endedAt(), info.purgeDueAt(), privacy.erasureRequestedAt() != null)));
            }
        }
        return pending;
    }

    @Override
    public PrivacyStatus confirmPurge(UUID propertyId, UUID tenantId, String password) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertOwnerOrAdmin(actor, propertyId);
        assertTenantInProperty(propertyId, tenantId);
        if (password == null || password.isBlank() || !passwordMatches(actor, password)) {
            audit("TENANT_PURGE_CONFIRM_FAILED", actor.id(), tenantId, Map.of("propertyId", propertyId.toString()));
            throw new DocumentRevealDeniedException();
        }
        TenantPrivacy privacy = privacyRepository.find(tenantId).orElseGet(() -> TenantPrivacy.none(tenantId));
        if (privacy.purged()) {
            return statusOf(tenantId);
        }
        if (privacy.legalHold()) {
            throw new TenantProfileLockedException("Tenant đang bị giữ pháp lý, không thể xóa.");
        }
        TenantLockPolicy.LockInfo info = lockPolicy.lockInfo(tenantId);
        if (!isDue(privacy, info, clock.instant())) {
            throw new TenantHasActiveContractException(
                "Tenant chưa đến hạn xóa (hoặc còn hợp đồng đang mở); chưa thể xác nhận xóa.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
        purge(tenant, privacy, "OWNER_CONFIRMED", actor.id());
        return statusOf(tenantId);
    }

    private boolean isDue(TenantPrivacy privacy, TenantLockPolicy.LockInfo info, Instant now) {
        if (privacy.purged() || privacy.legalHold() || info.live()) {
            return false;
        }
        return privacy.erasureRequestedAt() != null
            || (info.purgeDueAt() != null && !now.isBefore(info.purgeDueAt()));
    }

    private boolean passwordMatches(CurrentUser user, String password) {
        try {
            identityProvider.login(user.email(), password);
            return true;
        } catch (InvalidCredentialsException ex) {
            return false;
        }
    }

    private Set<UUID> tenantIdsOf(UUID propertyId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Contract contract : contractRepository.findByPropertyId(propertyId)) {
            ids.add(contract.getPrimaryTenantId());
            contract.getOccupants().forEach(occupant -> ids.add(occupant.tenantId()));
        }
        tenantRepository.findLinks(propertyId).forEach(link -> ids.add(link.tenantId()));
        return ids;
    }

    /** Báo chủ các nhà trọ liên quan một lần cho mỗi tenant (đánh dấu {@code purge_notified_at}). */
    private void notifyOwnersOnce(Tenant tenant, TenantPrivacy privacy, String message) {
        if (privacy.purgeNotifiedAt() != null || notificationRepository == null) {
            return;
        }
        Set<UUID> ownerIds = new LinkedHashSet<>();
        for (UUID propertyId : authorizationService.tenantPropertyIds(tenant.id())) {
            propertyRepository.findById(propertyId).map(Property::ownerId).ifPresent(ownerIds::add);
        }
        for (UUID ownerId : ownerIds) {
            userRepository.findById(ownerId).ifPresent(owner -> notificationRepository.save(
                Notification.createGeneral("Cần xác nhận xóa giấy tờ người thuê", message,
                    owner.id(), owner.username(), owner.fullName(), owner.role())));
        }
        privacyRepository.save(privacy.withPurgeNotified(clock.instant()));
    }

    /**
     * Xóa ảnh giấy tờ trong kho và bản ghi giấy tờ (kể cả số GPLX), thay số CCCD bằng giá trị đánh dấu. Xóa file trước:
     * nếu kho lỗi thì ném lỗi và bản ghi còn nguyên để lượt sau thử lại, không để lại bản ghi trỏ vào file đã mất mà tưởng đã xóa.
     */
    private void purge(Tenant tenant, TenantPrivacy privacy, String reason, UUID actorId) {
        List<TenantDocument> documents = documentRepository.findByTenantId(tenant.id());
        for (TenantDocument document : documents) {
            storagePort.delete(document.frontImageKey());
            if (document.hasBackImage()) {
                storagePort.delete(document.backImageKey());
            }
        }
        for (TenantDocument document : documents) {
            documentRepository.delete(tenant.id(), document.type());
        }
        if (!tenant.identityPurged()) {
            tenantRepository.save(new Tenant(tenant.id(), tenant.userId(), tenant.fullName(),
                Tenant.purgedIdentityMarker(tenant.id()), tenant.phone(), tenant.email(), tenant.permanentAddress(),
                tenant.createdAt()));
        }
        privacyRepository.save(privacy.withPurged(clock.instant()));
        audit("TENANT_DATA_PURGED", actorId, tenant.id(), Map.of("reason", reason, "documents", documents.size()));
    }

    private PrivacyStatus statusOf(UUID tenantId) {
        TenantPrivacy privacy = privacyRepository.find(tenantId).orElseGet(() -> TenantPrivacy.none(tenantId));
        TenantLockPolicy.LockInfo info = lockPolicy.lockInfo(tenantId);
        return new PrivacyStatus(info.locked(), info.endedAt(), info.purgeDueAt(), privacy.legalHold(),
            privacy.legalHoldReason(), privacy.purged(), privacy.erasureRequestedAt());
    }

    private void assertTenantInProperty(UUID propertyId, UUID tenantId) {
        if (!authorizationService.tenantPropertyIds(tenantId).contains(propertyId)) {
            throw new TenantNotFoundException("Tenant not found in property: " + tenantId);
        }
    }

    private void audit(String action, UUID actorId, UUID tenantId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>(details);
        payload.put("tenantId", tenantId.toString());
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, actorId, "TENANT", tenantId.toString(), payload));
    }
}
