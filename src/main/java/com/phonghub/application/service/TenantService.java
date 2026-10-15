package com.phonghub.application.service;

import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.exception.TenantHasActiveContractException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Tenant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TenantService implements TenantUseCase {

    private final TenantRepositoryPort tenantRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final ContractRepositoryPort contractRepository;
    private final TenantLockPolicy lockPolicy;

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        this(tenantRepository, currentUserPort, authorizationService, auditPort, null);
    }

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        ContractRepositoryPort contractRepository
    ) {
        this(tenantRepository, currentUserPort, authorizationService, auditPort, contractRepository, null);
    }

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        ContractRepositoryPort contractRepository,
        TenantLockPolicy lockPolicy
    ) {
        this.lockPolicy = lockPolicy;
        this.contractRepository = contractRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort != null ? auditPort : event -> {};
    }

    /** Chỉ ghi tên các trường thay đổi, không ghi giá trị cá nhân vào nhật ký audit. */
    private static java.util.List<String> changedFields(Tenant before, Tenant after) {
        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(before.fullName(), after.fullName())) {
            changed.add("fullName");
        }
        if (!java.util.Objects.equals(before.identityCardNumber(), after.identityCardNumber())) {
            changed.add("identityCardNumber");
        }
        if (!java.util.Objects.equals(before.phone(), after.phone())) {
            changed.add("phone");
        }
        if (!java.util.Objects.equals(before.email(), after.email())) {
            changed.add("email");
        }
        if (!java.util.Objects.equals(before.permanentAddress(), after.permanentAddress())) {
            changed.add("permanentAddress");
        }
        return changed;
    }

    @Override
    public Optional<Tenant> getTenant(UUID tenantId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenant(currentUser, tenantId);
        return tenantRepository.findById(tenantId);
    }

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Override
    public TenantPage listTenants(UUID propertyId, String query, boolean includeHidden, int page, int size) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, propertyId);
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        List<PropertyTenantView> matched = new ArrayList<>(loadPropertyTenants(propertyId).stream()
            .filter(view -> includeHidden || !view.hidden())
            .toList());
        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) {
            matched = filterByQuery(propertyId, matched, q);
        }
        matched.sort(Comparator.comparing((PropertyTenantView v) -> fold(v.tenant().fullName()))
            .thenComparing(v -> v.tenant().id()));

        int from = (int) Math.min((long) pageIndex * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        return new TenantPage(List.copyOf(matched.subList(from, to)), pageIndex, pageSize, matched.size());
    }

    /** CCCD tìm chính xác qua chỉ mục HMAC; tên và SĐT tìm theo đoạn chuỗi trên dữ liệu của chính nhà trọ này. */
    private List<PropertyTenantView> filterByQuery(UUID propertyId, List<PropertyTenantView> views, String q) {
        if (q.matches("^(\\d{9}|\\d{12})$")) {
            Optional<Tenant> byCard = tenantRepository.findByIdentityCardNumber(q);
            if (byCard.isEmpty()) {
                return new ArrayList<>();
            }
            UUID id = byCard.get().id();
            return views.stream().filter(v -> v.tenant().id().equals(id)).collect(java.util.stream.Collectors.toList());
        }
        String needle = fold(q);
        String digits = q.replaceAll("\\D", "");
        return views.stream()
            .filter(v -> fold(v.tenant().fullName()).contains(needle)
                || (digits.length() >= 3 && v.tenant().phone() != null && v.tenant().phone().contains(digits)))
            .collect(java.util.stream.Collectors.toList());
    }

    /** Hạ chữ thường và bỏ dấu tiếng Việt để tìm theo tên không phân biệt dấu. */
    private static String fold(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
            .toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public PropertyTenantView getPropertyTenant(UUID propertyId, UUID tenantId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, propertyId);
        return loadPropertyTenants(propertyId).stream()
            .filter(view -> view.tenant().id().equals(tenantId))
            .findFirst()
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found in property: " + tenantId));
    }

    @Override
    public PropertyTenantView addTenant(AddTenantCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, command.propertyId());

        String idCard = command.identityCardNumber().trim();
        Optional<Tenant> existing = tenantRepository.findByIdentityCardNumber(idCard);
        if (existing.isPresent()) {
            // Không lộ hồ sơ của nhà trọ khác: chỉ báo trùng.
            boolean inThisProperty = authorizationService.tenantPropertyIds(existing.get().id())
                .contains(command.propertyId());
            throw new DuplicateIdentityCardException(inThisProperty
                ? "Số CCCD đã có trong nhà trọ này (có thể đang bị ẩn)."
                : "Số CCCD đã được sử dụng bởi một người thuê khác.");
        }

        Tenant tenant = tenantRepository.save(Tenant.create(
            null,
            command.fullName().trim(),
            idCard,
            command.phone().trim(),
            command.email() == null || command.email().isBlank() ? null : command.email().trim(),
            command.permanentAddress() == null || command.permanentAddress().isBlank()
                ? null : command.permanentAddress().trim()
        ));
        tenantRepository.linkToProperty(tenant.id(), command.propertyId(), currentUser.id());
        audit("TENANT_CREATED", currentUser, tenant, Map.of(
            "propertyId", command.propertyId().toString(), "identityCardNumber", tenant.maskedIdentityCardNumber()));
        return new PropertyTenantView(tenant, false, false);
    }

    @Override
    public PropertyTenantView hideTenant(UUID propertyId, UUID tenantId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, propertyId);
        PropertyTenantView view = getPropertyTenant(propertyId, tenantId);
        if (view.hasActiveContract()) {
            throw new TenantHasActiveContractException("Không thể ẩn người thuê đang có hợp đồng nháp hoặc đang hiệu lực tại nhà trọ này.");
        }
        tenantRepository.setHidden(tenantId, propertyId, Instant.now(), currentUser.id());
        audit("TENANT_HIDDEN", currentUser, view.tenant(), Map.of("propertyId", propertyId.toString()));
        return new PropertyTenantView(view.tenant(), true, false);
    }

    @Override
    public PropertyTenantView restoreTenant(UUID propertyId, UUID tenantId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, propertyId);
        PropertyTenantView view = getPropertyTenant(propertyId, tenantId);
        tenantRepository.setHidden(tenantId, propertyId, null, currentUser.id());
        audit("TENANT_RESTORED", currentUser, view.tenant(), Map.of("propertyId", propertyId.toString()));
        return new PropertyTenantView(view.tenant(), false, view.hasActiveContract());
    }

    /** Tenant của nhà trọ = có hợp đồng tại nhà trọ ∪ được gắn trực tiếp. Ẩn chỉ có hiệu lực khi không còn hợp đồng đang mở. */
    private List<PropertyTenantView> loadPropertyTenants(UUID propertyId) {
        Set<UUID> ids = new HashSet<>();
        Set<UUID> activeIds = new HashSet<>();
        if (contractRepository != null) {
            for (Contract contract : contractRepository.findByPropertyId(propertyId)) {
                // Hợp đồng nháp cũng tính là đang mở: cùng quy tắc với khóa hồ sơ (TenantLockPolicy).
                boolean open = contract.isOpen();
                ids.add(contract.getPrimaryTenantId());
                if (open) {
                    activeIds.add(contract.getPrimaryTenantId());
                }
                for (ContractOccupant occupant : contract.getOccupants()) {
                    ids.add(occupant.tenantId());
                    if (open && occupant.checkOutDate() == null) {
                        activeIds.add(occupant.tenantId());
                    }
                }
            }
        }
        Map<UUID, Instant> hiddenAt = new HashMap<>();
        for (TenantRepositoryPort.PropertyLink link : tenantRepository.findLinks(propertyId)) {
            ids.add(link.tenantId());
            if (link.hiddenAt() != null) {
                hiddenAt.put(link.tenantId(), link.hiddenAt());
            }
        }
        return tenantRepository.findAllByIds(ids).stream()
            .map(tenant -> {
                boolean active = activeIds.contains(tenant.id());
                return new PropertyTenantView(tenant, hiddenAt.containsKey(tenant.id()) && !active, active);
            })
            .toList();
    }

    private void audit(String action, CurrentUser actor, Tenant tenant, Map<String, Object> details) {
        auditPort.recordEvent(AuditPort.AuditEvent.of(
            action, actor != null ? actor.id() : null, "TENANT", tenant.id().toString(), details));
    }

    @Override
    public Tenant updateTenant(UpdateTenantCommand command) {
        Tenant tenant = tenantRepository.findById(command.tenantId())
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found with ID: " + command.tenantId()));

        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanUpdateTenant(currentUser, tenant.id());
        if (lockPolicy != null) {
            lockPolicy.assertNotLocked(tenant.id());
        }

        String newIdCard = command.identityCardNumber() == null || command.identityCardNumber().isBlank()
            || command.identityCardNumber().startsWith("****")
            ? tenant.identityCardNumber()
            : command.identityCardNumber().trim();
        Optional<Tenant> duplicateCccd = tenantRepository.findByIdentityCardNumber(newIdCard);
        if (duplicateCccd.isPresent() && !duplicateCccd.get().id().equals(tenant.id())) {
            throw new DuplicateIdentityCardException("Số CCCD đã được sử dụng bởi một người thuê khác.");
        }

        Tenant updated = tenant.updateInfo(
            command.fullName(),
            newIdCard,
            command.phone(),
            command.email(),
            command.permanentAddress()
        );

        Tenant saved = tenantRepository.save(updated);

        Map<String, Object> details = new HashMap<>();
        details.put("tenantId", saved.id().toString());
        details.put("identityCardNumber", saved.maskedIdentityCardNumber());
        details.put("changedFields", changedFields(tenant, saved));

        auditPort.recordEvent(AuditPort.AuditEvent.of(
            "TENANT_UPDATE",
            currentUser != null ? currentUser.id() : null,
            "TENANT",
            saved.id().toString(),
            details
        ));

        return saved;
    }
}
