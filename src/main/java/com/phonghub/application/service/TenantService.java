package com.phonghub.application.service;

import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.exception.DuplicateIdentityCardException;
import com.phonghub.domain.exception.ConflictException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.TenantHasActiveContractException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
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
    private final UserRepositoryPort userRepository;
    private final TenantAccountLinker accountLinker;

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
        this(tenantRepository, currentUserPort, authorizationService, auditPort, contractRepository, null, null, null);
    }

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        ContractRepositoryPort contractRepository,
        TenantLockPolicy lockPolicy
    ) {
        this(tenantRepository, currentUserPort, authorizationService, auditPort, contractRepository, lockPolicy, null);
    }

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        ContractRepositoryPort contractRepository,
        TenantLockPolicy lockPolicy,
        UserRepositoryPort userRepository
    ) {
        this(tenantRepository, currentUserPort, authorizationService, auditPort, contractRepository, lockPolicy,
            userRepository, null);
    }

    public TenantService(
        TenantRepositoryPort tenantRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        ContractRepositoryPort contractRepository,
        TenantLockPolicy lockPolicy,
        UserRepositoryPort userRepository,
        IdentityProviderPort identityProviderPort
    ) {
        this.lockPolicy = lockPolicy;
        this.contractRepository = contractRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort != null ? auditPort : event -> {};
        this.accountLinker = userRepository == null ? null
            : new TenantAccountLinker(tenantRepository, userRepository, identityProviderPort, this.auditPort);
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
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy người thuê trong nhà trọ này."));
    }

    @Override
    public PropertyTenantView addTenant(AddTenantCommand command) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, command.propertyId());

        if (command.tenantProfileId() != null) {
            Tenant tenant = tenantRepository.findById(command.tenantProfileId())
                .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy hồ sơ người thuê đã chọn."));
            authorizationService.assertCanAccessTenant(currentUser, tenant.id());
            if (command.tenantUserId() != null) {
                tenant = linkTenantUser(tenant, command.tenantUserId());
            }
            tenantRepository.linkToProperty(tenant.id(), command.propertyId(), currentUser.id());
            audit("TENANT_LINKED_TO_PROPERTY", currentUser, tenant, command.propertyId(), Map.of(
                "propertyId", command.propertyId().toString(), "tenantId", tenant.id().toString()));
            return getPropertyTenant(command.propertyId(), tenant.id());
        }

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

        UUID userId = command.tenantUserId();
        User linkedUser = userId == null ? null : findTenantUser(userId);
        if (linkedUser != null && tenantRepository.findByUserId(linkedUser.id()).isPresent()) {
            throw new IllegalArgumentException("Tài khoản này đã được liên kết với hồ sơ người thuê khác.");
        }

        Tenant tenant = tenantRepository.save(Tenant.create(
            linkedUser == null ? null : linkedUser.id(),
            command.fullName().trim(),
            idCard,
            command.phone().trim(),
            linkedUser != null ? linkedUser.email()
                : command.email() == null || command.email().isBlank() ? null : command.email().trim(),
            command.permanentAddress() == null || command.permanentAddress().isBlank()
                ? null : command.permanentAddress().trim()
        ));
        tenantRepository.linkToProperty(tenant.id(), command.propertyId(), currentUser.id());
        audit("TENANT_CREATED", currentUser, tenant, command.propertyId(), Map.of(
            "propertyId", command.propertyId().toString(), "identityCardNumber", tenant.maskedIdentityCardNumber()));
        return new PropertyTenantView(tenant, false, false);
    }

    @Override
    public Tenant linkAccountByEmail(UUID tenantId, String email) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy người thuê."));
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanUpdateTenant(currentUser, tenantId);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Cần nhập email tài khoản người thuê.");
        }
        if (userRepository == null) {
            throw new IllegalStateException("Chưa cấu hình truy cập tài khoản người dùng.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy tài khoản theo email này."));
        Tenant saved = linkTenantUser(tenant, user.id());
        audit("TENANT_ACCOUNT_LINKED", currentUser, saved, null, Map.of("userId", user.id().toString()));
        return saved;
    }

    @Override
    public TenantAccountResult createTenantAccount(CreateTenantAccountCommand command) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(actor, command.propertyId());
        requireAccountProvisioning();

        String idCard = command.identityCardNumber().trim();
        String email = command.email().trim().toLowerCase(java.util.Locale.ROOT);
        Tenant tenant = tenantRepository.findByIdentityCardNumber(idCard).orElse(null);
        if (tenant != null) {
            // Không lộ hồ sơ của nhà trọ khác: chỉ báo trùng.
            Set<UUID> linkedProperties = tenantRepository.findLinkedPropertyIds(tenant.id());
            if (!linkedProperties.isEmpty() && !linkedProperties.contains(command.propertyId())) {
                throw new DuplicateIdentityCardException("Số CCCD đã được sử dụng bởi một người thuê khác.");
            }
            if (tenant.userId() != null) {
                throw new DuplicateIdentityCardException("CCCD này đã có tài khoản người thuê. Hãy chọn người thuê đó khi lập hợp đồng.");
            }
            tenant = new Tenant(tenant.id(), null, command.fullName().trim(), idCard, command.phone().trim(), email,
                cleanAddress(command.permanentAddress()), tenant.createdAt());
        } else {
            tenant = Tenant.create(null, command.fullName().trim(), idCard, command.phone().trim(), email,
                cleanAddress(command.permanentAddress()));
        }
        return provisionAccount(actor, tenant, email, command.propertyId(), false, false);
    }

    @Override
    public TenantAccountResult issueTenantAccount(IssueTenantAccountCommand command) {
        CurrentUser actor = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(actor, command.propertyId());
        requireAccountProvisioning();
        PropertyTenantView view = getPropertyTenant(command.propertyId(), command.tenantId());
        if (view.tenant().userId() != null) {
            throw new ConflictException("Hồ sơ này đã có tài khoản người thuê.");
        }
        return provisionAccount(actor, view.tenant(), command.email(), command.propertyId(), view.hidden(),
            view.hasActiveContract());
    }

    /** Một đường cấp tài khoản duy nhất (dùng chung {@link TenantAccountLinker}); lỗi sau đó thì xóa danh tính vừa tạo. */
    private TenantAccountResult provisionAccount(CurrentUser actor, Tenant tenant, String email, UUID propertyId,
                                                 boolean hidden, boolean hasActiveContract) {
        TenantAccountLinker.Result result = accountLinker.linkOrCreate(actor, tenant, email, "TENANT_ACCOUNT_CREATED");
        try {
            tenantRepository.linkToProperty(result.tenant().id(), propertyId, actor.id());
            audit("TENANT_ACCOUNT_LINKED", actor, result.tenant(), propertyId,
                Map.of("userId", result.tenant().userId().toString()));
        } catch (RuntimeException ex) {
            accountLinker.deleteCreatedIdentityUser(result, ex);
            throw ex;
        }
        return new TenantAccountResult(new PropertyTenantView(result.tenant(), hidden, hasActiveContract),
            result.username(), result.temporaryPassword(), result.temporaryPassword() != null);
    }

    private void requireAccountProvisioning() {
        if (accountLinker == null) {
            throw new DomainException("Chức năng cấp tài khoản người thuê chưa được cấu hình.");
        }
    }

    private static String cleanAddress(String address) {
        return address == null || address.isBlank() ? null : address.trim();
    }

    private User findTenantUser(UUID userId) {
        if (userRepository == null) {
            throw new IllegalStateException("Chưa cấu hình truy cập tài khoản người dùng.");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy tài khoản người thuê đã chọn."));
        if (user.role() != UserRole.TENANT || user.status() != User.UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Chỉ được liên kết tài khoản TENANT đang hoạt động.");
        }
        return user;
    }

    private Tenant linkTenantUser(Tenant tenant, UUID userId) {
        User user = findTenantUser(userId);
        if (tenant.userId() != null && !tenant.userId().equals(user.id())) {
            throw new IllegalArgumentException("Hồ sơ này đã liên kết với tài khoản khác.");
        }
        Optional<Tenant> linkedProfile = tenantRepository.findByUserId(user.id());
        if (linkedProfile.isPresent() && !linkedProfile.get().id().equals(tenant.id())) {
            throw new IllegalArgumentException("Tài khoản này đã được liên kết với hồ sơ người thuê khác.");
        }
        if (user.id().equals(tenant.userId()) && user.email().equalsIgnoreCase(tenant.email())) {
            return tenant;
        }
        Tenant linked = new Tenant(tenant.id(), user.id(), tenant.fullName(), tenant.identityCardNumber(),
            tenant.phone(), user.email(), tenant.permanentAddress(), tenant.createdAt());
        return tenantRepository.save(linked);
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
        audit("TENANT_HIDDEN", currentUser, view.tenant(), propertyId, Map.of("propertyId", propertyId.toString()));
        return new PropertyTenantView(view.tenant(), true, false);
    }

    @Override
    public PropertyTenantView restoreTenant(UUID propertyId, UUID tenantId) {
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, propertyId);
        PropertyTenantView view = getPropertyTenant(propertyId, tenantId);
        tenantRepository.setHidden(tenantId, propertyId, null, currentUser.id());
        audit("TENANT_RESTORED", currentUser, view.tenant(), propertyId, Map.of("propertyId", propertyId.toString()));
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

    /** Gắn nhà trọ đang thao tác: người thuê có thể thuộc nhiều nhà, không để adapter đoán theo nhà gần nhất. */
    private void audit(String action, CurrentUser actor, Tenant tenant, UUID propertyId, Map<String, Object> details) {
        auditPort.recordEvent(AuditPort.AuditEvent.of(
            action, actor != null ? actor.id() : null, "TENANT", tenant.id().toString(), propertyId, details));
    }

    @Override
    public Tenant updateTenant(UpdateTenantCommand command) {
        Tenant tenant = tenantRepository.findById(command.tenantId())
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy người thuê."));

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

        // Thông tin người thuê dùng chung giữa các nhà: ghi cho từng nhà mà người sửa có quyền và người thuê thuộc về,
        // để không lọt sang nhật ký của chủ nhà khác.
        Set<UUID> accessible = authorizationService.getAccessiblePropertyIds(currentUser);
        List<UUID> scope = authorizationService.tenantPropertyIds(saved.id()).stream()
            .filter(accessible::contains).sorted().toList();
        if (scope.isEmpty()) {
            audit("TENANT_UPDATE", currentUser, saved, null, details);
        }
        scope.forEach(propertyId -> audit("TENANT_UPDATE", currentUser, saved, propertyId, details));

        return saved;
    }
}
