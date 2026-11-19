package com.phonghub.application.service;

import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DocumentRevealDeniedException;
import com.phonghub.domain.exception.DuplicateDocumentNumberException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.TenantDocumentNotFoundException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.TooManyRevealAttemptsException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.TenantDocument;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TenantDocumentService implements TenantDocumentUseCase {

    static final int MAX_FAILED_REVEALS = 5;
    static final Duration REVEAL_WINDOW = Duration.ofMinutes(10);
    private static final String ENCRYPTED_CONTENT_TYPE = "application/octet-stream";

    private final TenantDocumentRepositoryPort documentRepository;
    private final TenantRepositoryPort tenantRepository;
    private final TenantDataProtectionPort protector;
    private final FileStoragePort storagePort;
    private final IdentityProviderPort identityProvider;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final long maxFileSizeBytes;
    private final Clock clock;
    private final TenantLockPolicy lockPolicy;
    private final Map<UUID, Deque<Instant>> failedReveals = new ConcurrentHashMap<>();

    public TenantDocumentService(
        TenantDocumentRepositoryPort documentRepository,
        TenantRepositoryPort tenantRepository,
        TenantDataProtectionPort protector,
        FileStoragePort storagePort,
        IdentityProviderPort identityProvider,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        long maxFileSizeBytes,
        Clock clock
    ) {
        this(documentRepository, tenantRepository, protector, storagePort, identityProvider, currentUserPort,
            authorizationService, auditPort, maxFileSizeBytes, clock, null);
    }

    public TenantDocumentService(
        TenantDocumentRepositoryPort documentRepository,
        TenantRepositoryPort tenantRepository,
        TenantDataProtectionPort protector,
        FileStoragePort storagePort,
        IdentityProviderPort identityProvider,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        long maxFileSizeBytes,
        Clock clock,
        TenantLockPolicy lockPolicy
    ) {
        this.lockPolicy = lockPolicy;
        this.documentRepository = documentRepository;
        this.tenantRepository = tenantRepository;
        this.protector = protector;
        this.storagePort = storagePort;
        this.identityProvider = identityProvider;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.clock = clock;
    }

    @Override
    public List<DocumentSummary> list(UUID propertyId, UUID tenantId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, propertyId, tenantId);
        Tenant tenant = findTenant(tenantId);
        List<DocumentSummary> summaries = documentRepository.findByTenantId(tenantId).stream()
            .sorted(Comparator.comparing(TenantDocument::type))
            .map(document -> summarize(document, tenant))
            .toList();
        audit("TENANT_DOCUMENTS_VIEWED", user, propertyId, tenantId, Map.of("count", summaries.size()));
        return summaries;
    }

    @Override
    public DocumentSummary view(UUID propertyId, UUID tenantId, DocumentType type) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, propertyId, tenantId);
        Tenant tenant = findTenant(tenantId);
        TenantDocument document = findDocument(tenantId, type);
        audit("TENANT_DOCUMENT_VIEWED", user, propertyId, tenantId, Map.of("type", type.name()));
        return summarize(document, tenant);
    }

    @Override
    public DocumentSummary upload(UploadDocumentCommand command) {
        if (command == null || command.type() == null) {
            throw new IllegalArgumentException("Cần chọn loại giấy tờ.");
        }
        if (command.type() != DocumentType.CCCD && (command.number() == null || command.number().isBlank())) {
            throw new IllegalArgumentException("Cần nhập số giấy phép lái xe.");
        }
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, command.propertyId(), command.tenantId());
        assertNotLocked(command.tenantId());
        Tenant tenant = findTenant(command.tenantId());

        TenantDocument previous = documentRepository.findByTenantIdAndType(command.tenantId(), command.type())
            .orElse(null);
        TenantDocument saved = saveDocument(command.propertyId(), tenant, command.type(), command.number(),
            command.front(), command.back(), previous, user);
        audit("TENANT_DOCUMENT_UPLOADED", user, command.propertyId(), command.tenantId(),
            Map.of("type", command.type().name(), "replaced", previous != null));
        return summarize(saved, tenant);
    }

    @Override
    public OwnDocuments ownDocuments() {
        CurrentUser user = requireTenantUser();
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
        if (tenant == null) {
            return new OwnDocuments(false, List.of());
        }
        // Người thuê xem bản tóm tắt đã che số của chính mình: không ghi audit như khi người quản lý xem.
        List<DocumentSummary> documents = documentRepository.findByTenantId(tenant.id()).stream()
            .sorted(Comparator.comparing(TenantDocument::type))
            .map(document -> summarize(document, tenant))
            .toList();
        return new OwnDocuments(true, documents);
    }

    @Override
    public DocumentSummary uploadOwnIdentityCard(DocumentFile front, DocumentFile back) {
        CurrentUser user = requireTenantUser();
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElseThrow(() -> new IllegalArgumentException(
            "Tài khoản chưa gắn hồ sơ người thuê có CCCD. Vui lòng liên hệ chủ trọ hoặc quản trị viên để bổ sung."));
        assertNotLocked(tenant.id());
        TenantDocument previous = documentRepository.findByTenantIdAndType(tenant.id(), DocumentType.CCCD)
            .orElse(null);
        // CCCD từng được tải ở trang của một nhà trọ thì giữ nhà trọ đó; tải lần đầu ở hồ sơ thì không gắn nhà trọ.
        UUID propertyId = previous == null ? null : previous.propertyId();
        TenantDocument saved = saveDocument(propertyId, tenant, DocumentType.CCCD, null, front, back, previous, user);
        audit("TENANT_DOCUMENT_UPLOADED", user, propertyId, tenant.id(),
            Map.of("type", DocumentType.CCCD.name(), "replaced", previous != null, "source", "PROFILE"));
        return summarize(saved, tenant);
    }

    @Override
    public DocumentSummary uploadOwnDriverLicense(String licenseNumber, DocumentFile front, DocumentFile back) {
        CurrentUser user = requireTenantUser();
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElseThrow(() -> new IllegalArgumentException(
            "Tài khoản chưa gắn hồ sơ người thuê có CCCD. Vui lòng liên hệ chủ trọ hoặc quản trị viên để bổ sung."));
        assertNotLocked(tenant.id());
        TenantDocument previous = documentRepository.findByTenantIdAndType(tenant.id(), DocumentType.GPLX)
            .orElse(null);
        UUID propertyId = previous == null ? null : previous.propertyId();
        TenantDocument saved = saveDocument(propertyId, tenant, DocumentType.GPLX, licenseNumber, front, back, previous, user);
        audit("TENANT_DOCUMENT_UPLOADED", user, propertyId, tenant.id(),
            Map.of("type", DocumentType.GPLX.name(), "replaced", previous != null, "source", "PROFILE"));
        return summarize(saved, tenant);
    }

    /** Kiểm tra, mã hóa và lưu ảnh rồi ghi hoặc thay bản ghi giấy tờ; ảnh cũ chỉ bị xóa sau khi lưu thành công. */
    private TenantDocument saveDocument(
        UUID propertyId,
        Tenant tenant,
        DocumentType type,
        String number,
        DocumentFile frontFile,
        DocumentFile backFile,
        TenantDocument previous,
        CurrentUser user
    ) {
        Prepared front = prepareImage(frontFile, true);
        Prepared back = prepareImage(backFile, false);
        TenantDataProtectionPort.ProtectedValue protectedNumber = protectNumber(type, number, tenant, previous);

        String frontKey = store(propertyId, tenant.id(), front);
        String backKey = back == null ? null : store(propertyId, tenant.id(), back);
        Instant now = clock.instant();
        TenantDocument saved;
        try {
            saved = documentRepository.save(new TenantDocument(
                previous != null ? previous.id() : UUID.randomUUID(),
                tenant.id(),
                propertyId,
                type,
                protectedNumber == null ? null : protectedNumber.ciphertext(),
                protectedNumber == null ? null : protectedNumber.nonce(),
                protectedNumber == null ? null : protectedNumber.fingerprint(),
                protectedNumber == null ? null : protectedNumber.keyId(),
                lastFour(type, number, tenant, previous),
                frontKey,
                front.contentType(),
                backKey,
                back == null ? null : back.contentType(),
                previous != null ? previous.createdAt() : now,
                previous != null ? previous.createdBy() : user.id(),
                now,
                user.id()
            ));
        } catch (RuntimeException ex) {
            deleteQuietly(frontKey);
            deleteQuietly(backKey);
            throw ex;
        }
        if (previous != null) {
            deleteQuietly(previous.frontImageKey());
            deleteQuietly(previous.backImageKey());
        }
        return saved;
    }

    private CurrentUser requireTenantUser() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null || !user.isTenant()) {
            throw new UnauthorizedPropertyAccessException("Chỉ tài khoản người thuê tự tải giấy tờ ở Hồ sơ cá nhân.");
        }
        return user;
    }

    @Override
    public RevealedDocument reveal(UUID propertyId, UUID tenantId, DocumentType type, String password) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, propertyId, tenantId);
        assertNotThrottled(user.id());

        if (!passwordMatches(user, password)) {
            recordFailure(user.id());
            audit("TENANT_DOCUMENT_REVEAL_FAILED", user, propertyId, tenantId, Map.of("type", type.name()));
            throw new DocumentRevealDeniedException();
        }
        failedReveals.remove(user.id());

        Tenant tenant = findTenant(tenantId);
        TenantDocument document = findDocument(tenantId, type);
        RevealedDocument revealed = new RevealedDocument(
            type,
            type == DocumentType.CCCD ? tenant.identityCardNumber() : protector.reveal(
                new TenantDataProtectionPort.ProtectedValue(document.numberCiphertext(), document.numberNonce(),
                    document.numberHmac(), document.numberKeyId())),
            decryptImage(document.frontImageKey(), document.frontContentType()),
            document.hasBackImage() ? decryptImage(document.backImageKey(), document.backContentType()) : null
        );
        audit("TENANT_DOCUMENT_REVEALED", user, propertyId, tenantId, Map.of("type", type.name()));
        return revealed;
    }

    @Override
    public void delete(UUID propertyId, UUID tenantId, DocumentType type) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanDeleteTenantDocuments(user, propertyId, tenantId);
        assertNotLocked(tenantId);
        TenantDocument document = findDocument(tenantId, type);
        documentRepository.delete(tenantId, type);
        deleteQuietly(document.frontImageKey());
        deleteQuietly(document.backImageKey());
        audit("TENANT_DOCUMENT_DELETED", user, propertyId, tenantId, Map.of("type", type.name()));
    }

    private void assertNotLocked(UUID tenantId) {
        if (lockPolicy != null) {
            lockPolicy.assertNotLocked(tenantId);
        }
    }

    private boolean passwordMatches(CurrentUser user, String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        try {
            identityProvider.login(user.email(), password);
            return true;
        } catch (InvalidCredentialsException ex) {
            return false;
        }
    }

    private void assertNotThrottled(UUID userId) {
        Deque<Instant> failures = failedReveals.get(userId);
        if (failures == null) {
            return;
        }
        synchronized (failures) {
            Instant cutoff = clock.instant().minus(REVEAL_WINDOW);
            while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
                failures.pollFirst();
            }
            if (failures.size() >= MAX_FAILED_REVEALS) {
                throw new TooManyRevealAttemptsException();
            }
        }
    }

    private void recordFailure(UUID userId) {
        Deque<Instant> failures = failedReveals.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (failures) {
            failures.addLast(clock.instant());
        }
    }

    private Prepared prepareImage(DocumentFile file, boolean required) {
        if (file == null || file.content() == null || file.content().length == 0) {
            if (required) {
                throw new IllegalArgumentException("Cần ảnh mặt trước giấy tờ.");
            }
            return null;
        }
        if (!FileSanitizer.isImage(file.contentType())) {
            throw new IllegalArgumentException("Ảnh giấy tờ phải là JPEG, PNG hoặc WebP.");
        }
        FileSanitizer.SanitizedFile sanitized = FileSanitizer.sanitize(
            file.contentType(), file.content(), maxFileSizeBytes);
        return new Prepared(sanitized.contentType(), sanitized.content());
    }

    private TenantDataProtectionPort.ProtectedValue protectNumber(
        DocumentType type, String number, Tenant tenant, TenantDocument previous
    ) {
        if (type == DocumentType.CCCD) {
            return null;
        }
        if (number == null || number.isBlank()) {
            if (previous != null && previous.numberCiphertext() != null) {
                return new TenantDataProtectionPort.ProtectedValue(
                    previous.numberCiphertext(),
                    previous.numberNonce(),
                    previous.numberHmac(),
                    previous.numberKeyId()
                );
            }
            throw new IllegalArgumentException("Cần nhập số giấy phép lái xe.");
        }
        TenantDataProtectionPort.ProtectedValue value = protector.protect(number);
        if (documentRepository.existsByTypeAndNumberHmacForOtherTenant(type, value.fingerprint(), tenant.id())) {
            throw new DuplicateDocumentNumberException();
        }
        return value;
    }

    private String lastFour(DocumentType type, String number, Tenant tenant, TenantDocument previous) {
        if (type == DocumentType.CCCD) {
            String value = tenant.identityCardNumber();
            return value.length() <= 4 ? value : value.substring(value.length() - 4);
        }
        if ((number == null || number.isBlank()) && previous != null) {
            return previous.numberLast4();
        }
        if (number == null || number.isBlank()) {
            return null;
        }
        String value = number.trim().replaceAll("\\s+", "");
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    /** Giấy tờ chưa gắn nhà trọ (tải ở Hồ sơ cá nhân) nằm trong thư mục riêng của người thuê. */
    private String store(UUID propertyId, UUID tenantId, Prepared image) {
        String key = propertyId != null
            ? PrivateFileStorageService.objectKey(propertyId, "tenant-documents", "enc")
            : PrivateFileStorageService.tenantObjectKey(tenantId, "tenant-documents", "enc");
        storagePort.put(key, ENCRYPTED_CONTENT_TYPE, protector.encryptBytes(image.content()));
        return key;
    }

    private DocumentFile decryptImage(String key, String contentType) {
        return new DocumentFile(contentType, protector.decryptBytes(storagePort.get(key)));
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort: orphaned encrypted blobs are unreadable without the key
        }
    }

    private DocumentSummary summarize(TenantDocument document, Tenant tenant) {
        String masked = document.type() == DocumentType.CCCD ? tenant.maskedIdentityCardNumber()
            : document.maskedNumber();
        return new DocumentSummary(document.type(), masked, true, document.hasBackImage(), document.updatedAt());
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Không tìm thấy người thuê: " + tenantId));
    }

    private TenantDocument findDocument(UUID tenantId, DocumentType type) {
        return documentRepository.findByTenantIdAndType(tenantId, type)
            .orElseThrow(() -> new TenantDocumentNotFoundException("Document " + type + " not found for tenant"));
    }

    /** Gắn nhà trọ đang thao tác: người thuê có thể thuộc nhiều nhà, không để adapter đoán theo nhà gần nhất. */
    private void audit(String action, CurrentUser user, UUID propertyId, UUID tenantId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>(details);
        payload.put("tenantId", tenantId.toString());
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, user.id(), "TENANT_DOCUMENT", tenantId.toString(), propertyId,
            payload));
    }

    private record Prepared(String contentType, byte[] content) {}
}
